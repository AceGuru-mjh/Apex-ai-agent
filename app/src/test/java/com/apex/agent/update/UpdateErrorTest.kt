package com.apex.agent.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UpdateError] 与 [extractSha256] 的单元测试。
 *
 * 重点验证：
 * - 各 [UpdateError] 子类都能转成 [CheckResult.Failed] 且消息不丢失；
 * - [extractSha256] 在不同 release notes 格式下的解析正确性；
 * - 边界情况：空 body、无 SHA-256、多个 SHA-256、大小写、含 APK 名等。
 */
class UpdateErrorTest {

    @Test
    fun `NoNetwork toCheckFailed preserves message`() {
        val err = UpdateError.NoNetwork()
        val failed = err.toCheckFailed()
        assertEquals(err.message, failed.reason)
        assertNull(failed.cause)
    }

    @Test
    fun `WifiOnly toCheckFailed preserves message`() {
        val err = UpdateError.WifiOnly()
        val failed = err.toCheckFailed()
        assertEquals(err.message, failed.reason)
    }

    @Test
    fun `RateLimited toCheckFailed preserves message`() {
        val err = UpdateError.RateLimited(resetEpochSec = 1234567890L)
        val failed = err.toCheckFailed()
        assertTrue(failed.reason.contains("限流"))
    }

    @Test
    fun `NetworkError carries cause`() {
        val cause = java.io.IOException("timeout")
        val err = UpdateError.NetworkError(cause)
        val failed = err.toCheckFailed()
        assertNotNull(failed.cause)
        assertEquals(cause, failed.cause)
        assertTrue(failed.reason.contains("timeout"))
    }

    @Test
    fun `ParseError carries cause`() {
        val cause = kotlinx.serialization.json.JsonDecodingException("bad json")
        val err = UpdateError.ParseError(cause)
        val failed = err.toCheckFailed()
        assertEquals(cause, failed.cause)
        assertTrue(failed.reason.contains("解析失败"))
    }

    @Test
    fun `AllMirrorsFailed includes tried count`() {
        val err = UpdateError.AllMirrorsFailed(triedCount = 5)
        val failed = err.toCheckFailed()
        assertTrue(failed.reason.contains("5"))
    }

    @Test
    fun `Cancelled has fixed message`() {
        val failed = UpdateError.Cancelled.toCheckFailed()
        assertEquals("已取消", failed.reason)
    }

    @Test
    fun `Unknown with null cause still has message`() {
        val err = UpdateError.Unknown(cause = null, message = "自定义错误")
        val failed = err.toCheckFailed()
        assertEquals("自定义错误", failed.reason)
    }

    // ---------- extractSha256 ----------

    @Test
    fun `extractSha256 parses SHA-256 colon line`() {
        val release = UpdateRelease(
            tagName = "v1",
            body = "## v1\n\nSHA-256: abc123def4567890abc123def4567890abc123def4567890abc123def4567890"
        )
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        val sha = extractSha256(release, asset)
        assertEquals("abc123def4567890abc123def4567890abc123def4567890abc123def4567890", sha)
    }

    @Test
    fun `extractSha256 parses SHA256 without dash`() {
        val release = UpdateRelease(
            tagName = "v1",
            body = "SHA256: deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        )
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        val sha = extractSha256(release, asset)
        assertEquals("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef", sha)
    }

    @Test
    fun `extractSha256 parses lowercase sha256`() {
        val release = UpdateRelease(
            tagName = "v1",
            body = "sha-256: fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        )
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        val sha = extractSha256(release, asset)
        assertEquals("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210", sha)
    }

    @Test
    fun `extractSha256 returns null when body is null`() {
        val release = UpdateRelease(tagName = "v1", body = null)
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        assertNull(extractSha256(release, asset))
    }

    @Test
    fun `extractSha256 returns null when no sha256 present`() {
        val release = UpdateRelease(
            tagName = "v1",
            body = "## v1\n\n- 修复 bug\n- 改进性能"
        )
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        assertNull(extractSha256(release, asset))
    }

    @Test
    fun `extractSha256 returns null for short hex`() {
        // 63 个字符，不是合法 SHA-256
        val release = UpdateRelease(
            tagName = "v1",
            body = "SHA-256: abc123"
        )
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        assertNull(extractSha256(release, asset))
    }

    @Test
    fun `extractSha256 takes first match when multiple present`() {
        val release = UpdateRelease(
            tagName = "v1",
            body = """
                SHA-256: 1111111111111111111111111111111111111111111111111111111111111111
                SHA-256: 2222222222222222222222222222222222222222222222222222222222222222
            """.trimIndent()
        )
        val asset = UpdateAsset(name = "app.apk", browserDownloadUrl = "https://x.com/app.apk")
        val sha = extractSha256(release, asset)
        assertEquals("1111111111111111111111111111111111111111111111111111111111111111", sha)
    }
}
