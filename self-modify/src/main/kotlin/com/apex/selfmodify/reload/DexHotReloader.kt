package com.apex.selfmodify.reload

import com.apex.selfmodify.workspace.FileChange
import java.io.File

/**
 * Hot-reloads Kotlin classes via [dalvik.system.DexClassLoader].
 *
 * Per AGENT_SELF_MODIFY_SPEC §4.3: Kotlin classes (no new signatures) can be
 * hot-swapped by compiling the changed .kt → .class → .dex, then loading the
 * new dex via DexClassLoader and replacing instances.
 *
 * **Phase 3 status**: framework only. Actual kotlac compilation + DexClassLoader
 * loading is deferred to Phase 4 (requires bundling kotlinc or delegating to
 * the on-device gradle build). This stub reports Partial so the PlanExecutor
 * flow can proceed without blocking on reload.
 */
class DexHotReloader(private val cacheDir: File) : HotReloader {

    override fun canHotReload(file: File): Boolean = file.extension == "kt"

    override suspend fun reload(files: List<FileChange>): ReloadResult {
        val ktFiles = files.filter { canHotReload(File(it.path)) }
        if (ktFiles.isEmpty()) return ReloadResult.Failure("No hot-reloadable files")

        // Phase 3 stub: actual DexClassLoader implementation deferred to Phase 4.
        // Requires kotlinc compilation of changed .kt → .class → .dex → DexClassLoader.
        return ReloadResult.Partial(
            reloaded = emptyList(),
            failed = ktFiles.map { it.path },
            reason = "DexClassLoader reload requires kotlinc — Phase 4"
        )
    }
}
