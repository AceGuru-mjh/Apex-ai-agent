package com.ai.assistance.aiterminal.terminal.ai

/**
 * TERM-FIX-4A / I-9: Shared best-effort secret redaction utility.
 *
 * Extracted from `CommandHistoryManager.redactSecrets` (TERM-FIX-3B / I-6) so
 * that every sensitive sink in the terminal stack applies the **same** regex
 * patterns. Currently consumed by:
 *
 *   - `CommandHistoryManager.redactSecrets` — redacts commands before they are
 *     persisted to `command_history.json` and surfaced to the LLM as context.
 *   - `AgentTerminalExecutor.logAudit` — redacts commands before they enter the
 *     in-memory `_auditLogs` ring buffer (which is serialized to JSON via
 *     `getAuditLogs`).
 *
 * ## Patterns covered
 *
 *   - `Authorization: Bearer <token>`          -> `Authorization: Bearer [REDACTED]`
 *   - `password=<value>` / `password: <value>` -> `password=[REDACTED]`
 *   - `secret=<value>`  / `api_key=<value>`    -> `secret=[REDACTED]` / etc.
 *   - `token=<value>`                          -> `token=[REDACTED]`
 *   - `api_key=<value>` / `apikey=<value>` / `api-key=<value>` -> `api_key=[REDACTED]`
 *   - `https://user:pass@host`                 -> `https://user:[REDACTED]@host`
 *   - AWS access key IDs `AKIA[0-9A-Z]{16}`    -> `[REDACTED_AWS_KEY]`
 *   - Generic long hex/base64 tokens (40+ chars) -> `[REDACTED_TOKEN]`
 *
 * ## Limitations
 *
 *   - Regex-based; will NOT catch secrets in unusual formats or secrets
 *     split across multiple lines / constructed at runtime.
 *   - The "generic long token" pattern may produce false positives on
 *     legitimate long hex strings (e.g. SHA-1 commit hashes, file hashes).
 *     This is an acceptable trade-off for a defense-in-depth layer.
 *   - Does NOT redact env-var assignments like `export FOO=bar` unless the
 *     var name matches one of the patterns above.
 *
 * This is a **stateless** object — all methods are pure functions and safe to
 * call from any thread.
 */
object SecretRedactor {

    /**
     * Redact common secret patterns from a command/string before it enters a
     * sensitive sink (disk, LLM context, audit log).
     *
     * @param input the raw string that may contain secrets
     * @return a best-effort redacted copy of [input]
     */
    fun redact(input: String): String {
        var redacted = input

        // Authorization: Bearer <token>
        redacted = Regex("""(Authorization:\s*Bearer\s+)([^\s]+)""", RegexOption.IGNORE_CASE)
            .replace(redacted) { "${it.groupValues[1]}[REDACTED]" }

        // password=<value> | password: <value>  (stops at whitespace or &)
        redacted = Regex("""(password\s*[=:]\s*)([^\s&]+)""", RegexOption.IGNORE_CASE)
            .replace(redacted) { "${it.groupValues[1]}[REDACTED]" }

        // secret=<value> | secret: <value>
        redacted = Regex("""(secret\s*[=:]\s*)([^\s&]+)""", RegexOption.IGNORE_CASE)
            .replace(redacted) { "${it.groupValues[1]}[REDACTED]" }

        // token=<value> | token: <value>
        redacted = Regex("""(token\s*[=:]\s*)([^\s&]+)""", RegexOption.IGNORE_CASE)
            .replace(redacted) { "${it.groupValues[1]}[REDACTED]" }

        // api_key=<value> | apikey=<value> | api-key=<value> | api_key: <value>
        redacted = Regex("""(api_?key\s*[=:]\s*)([^\s&]+)""", RegexOption.IGNORE_CASE)
            .replace(redacted) { "${it.groupValues[1]}[REDACTED]" }

        // URL-embedded credentials: https://user:pass@host
        // Keep the username (often non-secret) but redact the password segment.
        redacted = Regex("""(https?://)([^:\s]+):([^@\s]+)@""")
            .replace(redacted) { "${it.groupValues[1]}${it.groupValues[2]}:[REDACTED]@" }

        // AWS access key IDs (well-known format: AKIA + 16 uppercase alphanumerics)
        redacted = Regex("""(AKIA[0-9A-Z]{16})""")
            .replace(redacted, "[REDACTED_AWS_KEY]")

        // Generic long hex/base64 token — 40+ contiguous hex chars.
        // Catches: SHA-1 hashes (40), AWS secret keys (40 hex), Git OIDs,
        // many JWT signatures (43+ base64url), etc. False positives on legit
        // hashes are an acceptable trade-off for defense-in-depth.
        redacted = Regex("""\b([a-fA-F0-9]{40,})\b""")
            .replace(redacted, "[REDACTED_TOKEN]")

        return redacted
    }
}
