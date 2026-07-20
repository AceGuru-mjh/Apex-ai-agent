package com.apex.selfmodify.reload

import com.apex.selfmodify.workspace.FileChange
import java.io.File

/**
 * Triggers Compose recomposition for changed @Composable functions.
 *
 * Per AGENT_SELF_MODIFY_SPEC §4.3: Compose hot reload requires compiler-level
 * support (Compose Hot Reload plugin) to swap function bodies without restart.
 *
 * **Phase 3 status**: framework only. Full implementation deferred to Phase 4.
 */
class ComposeReloader : HotReloader {

    override fun canHotReload(file: File): Boolean = file.extension == "kt"

    override suspend fun reload(files: List<FileChange>): ReloadResult {
        return ReloadResult.Partial(
            reloaded = emptyList(),
            failed = files.map { it.path },
            reason = "Compose hot reload requires compiler support — Phase 4"
        )
    }
}
