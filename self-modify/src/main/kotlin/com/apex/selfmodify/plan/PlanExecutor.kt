package com.apex.selfmodify.plan

import com.apex.selfmodify.audit.AuditLog
import com.apex.selfmodify.compile.CompileGate
import com.apex.selfmodify.compile.CompileResult
import com.apex.selfmodify.reload.HotReloader
import com.apex.selfmodify.reload.ReloadResult
import com.apex.selfmodify.rollback.RollbackManager
import com.apex.selfmodify.workspace.ChangeType
import com.apex.selfmodify.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineScope

/**
 * Orchestrates the full self-modify flow per AGENT_SELF_MODIFY_SPEC §4.1:
 *
 * snapshot → apply changes → compile gate → hot reload → audit
 *                ↑________________________|
 *                |  on failure: rollback  |
 *
 * Atomicity: if compile fails, the snapshot is restored automatically.
 */
class PlanExecutor(
    private val workspace: WorkspaceManager,
    private val compiler: CompileGate,
    private val reloader: HotReloader,
    private val rollback: RollbackManager,
    private val audit: AuditLog,
    private val scope: CoroutineScope
) {

    suspend fun execute(plan: ModificationPlan): ApplyResult {
        // 1. Snapshot before — for rollback safety
        val snapshotSha = rollback.snapshot("before-${plan.id}")

        // 2. Apply changes to workspace
        try {
            plan.changes.forEach { change ->
                when (change.type) {
                    ChangeType.CREATE, ChangeType.MODIFY ->
                        workspace.writeFile(change.path, change.newContent ?: "")
                    ChangeType.DELETE ->
                        workspace.deleteFile(change.path)
                    ChangeType.MOVE -> {
                        // Read old content, write to new path, delete old
                        val srcPath = change.path
                        val dstPath = change.newContent ?: throw java.io.IOException("MOVE requires newContent (destination path)")
                        val content = workspace.readFile(srcPath)
                        workspace.writeFile(dstPath, content)
                        workspace.deleteFile(srcPath)
                    }
                }
            }
        } catch (e: Exception) {
            rollback.rollback(snapshotSha)
            audit.record(plan.id, plan.agentId, plan.changes.map { it.path }, false, null)
            return ApplyResult.RolledBack(plan, "apply failed: ${e.message}")
        }

        // 3. Compile gate — must pass for changes to be accepted
        val result = compiler.compile("app")
        if (result !is CompileResult.Success) {
            rollback.rollback(snapshotSha)
            audit.record(plan.id, plan.agentId, plan.changes.map { it.path }, false, null)
            val reason = when (result) {
                is CompileResult.Failure -> "compile failed: ${result.errors.size} errors"
                is CompileResult.Timeout -> "compile timeout"
                else -> "compile error"
            }
            return ApplyResult.RolledBack(plan, reason)
        }

        // 4. Hot reload (best-effort — failure here doesn't roll back)
        val reloadResult = reloader.reload(plan.changes)
        val reloadOk = reloadResult is ReloadResult.Success

        // 5. Audit
        audit.record(plan.id, plan.agentId, plan.changes.map { it.path }, true, reloadOk)

        return ApplyResult.Success(plan, result.durationMs)
    }
}
