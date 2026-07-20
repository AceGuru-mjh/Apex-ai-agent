package com.apex.selfmodify.reload

import com.apex.selfmodify.workspace.FileChange
import java.io.File

/**
 * Hot-reload interface per AGENT_SELF_MODIFY_SPEC §4.3.
 *
 * Implementations attempt to swap in modified code without a full app restart.
 */
interface HotReloader {
    /** Reload the given file changes. Best-effort — may partially succeed. */
    suspend fun reload(files: List<FileChange>): ReloadResult

    /** Whether this reloader can hot-reload the given file type. */
    fun canHotReload(file: File): Boolean
}

sealed class ReloadResult {
    /** All files reloaded successfully. */
    data class Success(val reloadedClasses: List<String>) : ReloadResult()

    /** Some files reloaded, some failed. */
    data class Partial(val reloaded: List<String>, val failed: List<String>, val reason: String) : ReloadResult()

    /** Reload not possible. */
    data class Failure(val reason: String) : ReloadResult()
}
