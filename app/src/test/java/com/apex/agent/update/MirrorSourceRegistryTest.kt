package com.apex.agent.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MirrorSource] 与 [MirrorSourceRegistry] 的纯逻辑单元测试。
 *
 * 不涉及 Android Context / DataStore，只测试 [MirrorSource.wrap] 和
 * [MirrorSourceRegistry.applyKkGithub] 这类纯函数。
 */
class MirrorSourceRegistryTest {

    @Test
    fun `direct mirror returns original url`() {
        val direct = MirrorSource(id = "direct", name = "Direct", urlTemplate = "{url}")
        val url = "https://github.com/x/y/releases/download/v1/app.apk"
        assertEquals(url, direct.wrap(url))
    }

    @Test
    fun `blank template returns original url`() {
        val blank = MirrorSource(id = "blank", name = "Blank", urlTemplate = "")
        val url = "https://github.com/x/y/releases/download/v1/app.apk"
        assertEquals(url, blank.wrap(url))
    }

    @Test
    fun `prefix mirror wraps url correctly`() {
        val mirror = MirrorSource(
            id = "ghproxy",
            name = "ghproxy",
            urlTemplate = "https://ghproxy.com/{url}"
        )
        val original = "https://github.com/x/y/releases/download/v1/app.apk"
        assertEquals("https://ghproxy.com/$original", mirror.wrap(original))
    }

    @Test
    fun `kkgithub replaces host for https github`() {
        val original = "https://github.com/mengjinghao/Apex-ai-agent/releases/download/v1/app.apk"
        val wrapped = MirrorSourceRegistry.applyKkGithub(original)
        assertEquals(
            "https://kkgithub.com/mengjinghao/Apex-ai-agent/releases/download/v1/app.apk",
            wrapped
        )
    }

    @Test
    fun `kkgithub replaces host for http github`() {
        val original = "http://github.com/x/y/z"
        val wrapped = MirrorSourceRegistry.applyKkGithub(original)
        assertEquals("http://kkgithub.com/x/y/z", wrapped)
    }

    @Test
    fun `kkgithub leaves non-github url untouched`() {
        val original = "https://example.com/foo"
        val wrapped = MirrorSourceRegistry.applyKkGithub(original)
        assertEquals(original, wrapped)
    }

    @Test
    fun `builtin mirrors list is non-empty and contains direct`() {
        val builtins = MirrorSourceRegistry.BUILTIN_MIRRORS
        assertTrue(builtins.isNotEmpty())
        assertNotNull(builtins.firstOrNull { it.id == "direct" })
        // 所有内置镜像都应标记 builtin = true
        builtins.forEach { assertTrue("镜像 ${it.id} 应为 builtin", it.builtin) }
    }

    @Test
    fun `builtin mirrors have unique ids`() {
        val ids = MirrorSourceRegistry.BUILTIN_MIRRORS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `builtin mirrors all have non-blank templates`() {
        MirrorSourceRegistry.BUILTIN_MIRRORS.forEach { m ->
            assertTrue("镜像 ${m.id} 的 urlTemplate 不应为空白", m.urlTemplate.isNotBlank())
        }
    }

    @Test
    fun `custom mirror defaults to enabled true`() {
        val m = MirrorSource(id = "x", name = "X", urlTemplate = "https://x.com/{url}")
        assertTrue(m.enabled)
        assertFalse(m.builtin)
    }
}
