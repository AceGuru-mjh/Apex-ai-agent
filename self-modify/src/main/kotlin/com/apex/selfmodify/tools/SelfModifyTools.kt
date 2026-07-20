package com.apex.selfmodify.tools

import com.apex.core.model.ApexTool
import com.apex.core.model.ToolCategory
import com.apex.core.model.ToolMetadata
import com.apex.core.model.ToolResult
import com.apex.selfmodify.SelfModifyService
import com.apex.selfmodify.plan.ApplyResult
import com.apex.selfmodify.plan.ModificationPlan
import com.apex.selfmodify.plan.RiskLevel
import com.apex.selfmodify.workspace.ChangeType
import com.apex.selfmodify.workspace.FileChange
import java.util.UUID

/**
 * Agent-facing tools for the self-modify subsystem.
 *
 * Per AGENT_SELF_MODIFY_SPEC §8.3 — registered into the engine ToolCatalog so the
 * LLM Agent can read/search/modify/compile/rollback its own source code.
 *
 * 5 tools:
 *  - [ReadSourceTool]    : read a workspace source file (read-only)
 *  - [SearchCodeTool]    : find symbol definitions / references (read-only)
 *  - [ModifyCodeTool]    : apply a ModificationPlan (triggers full compile-gate + rollback flow)
 *  - [CompileCheckTool]  : probe workspace index/compile state (read-only)
 *  - [RollbackTool]      : rollback to a prior git snapshot
 */
class ReadSourceTool(private val svc: SelfModifyService) : ApexTool {
    override val metadata = ToolMetadata(
        id = "read_source",
        name = "Read Source",
        description = "Read a source file from the self-modify workspace.",
        category = ToolCategory.GENERAL,
        isReadOnly = true
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val path = arguments["path"]?.toString()
            ?: return ToolResult.Error("Missing 'path'")
        return try {
            ToolResult.Success(svc.readFile(path))
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "read failed")
        }
    }
}

class SearchCodeTool(private val svc: SelfModifyService) : ApexTool {
    override val metadata = ToolMetadata(
        id = "search_code",
        name = "Search Code",
        description = "Find symbol definitions or references in the workspace.",
        category = ToolCategory.SEARCH,
        isReadOnly = true
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val symbol = arguments["symbol"]?.toString()
            ?: return ToolResult.Error("Missing 'symbol'")
        val mode = arguments["mode"]?.toString() ?: "definition"
        val results = when (mode) {
            "references" -> svc.findReferences(symbol)
            else -> svc.findSymbol(symbol)
        }
        return ToolResult.Success(results.joinToString("\n") { it.toString() })
    }
}

class ModifyCodeTool(private val svc: SelfModifyService) : ApexTool {
    override val metadata = ToolMetadata(
        id = "modify_code",
        name = "Modify Code",
        description = "Apply a modification plan (snapshot -> write -> compile gate -> reload -> audit; auto-rollback on failure).",
        category = ToolCategory.GENERAL,
        isReadOnly = false
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val changesRaw = arguments["changes"] as? List<Map<String, Any>>
            ?: return ToolResult.Error("Missing 'changes'")
        val reason = arguments["reason"]?.toString() ?: "agent modification"
        val changes = changesRaw.map { c ->
            FileChange(
                path = c["path"]?.toString() ?: "",
                type = try {
                    ChangeType.valueOf((c["type"]?.toString() ?: "MODIFY").uppercase())
                } catch (e: Exception) {
                    ChangeType.MODIFY
                },
                newContent = c["newContent"]?.toString()
            )
        }.filter { it.path.isNotEmpty() }
        if (changes.isEmpty()) return ToolResult.Error("No valid changes")

        val risk = try {
            RiskLevel.valueOf((arguments["risk"]?.toString() ?: "MEDIUM").uppercase())
        } catch (e: Exception) {
            RiskLevel.MEDIUM
        }
        val plan = ModificationPlan(
            id = UUID.randomUUID().toString(),
            changes = changes,
            reason = reason,
            riskLevel = risk,
            requiresUserConfirm = risk in setOf(RiskLevel.HIGH, RiskLevel.CRITICAL),
            agentId = arguments["agentId"]?.toString() ?: "default"
        )
        return when (val r = svc.apply(plan)) {
            is ApplyResult.Success -> ToolResult.Success("Applied: ${plan.id} (compile ${r.compileMs}ms)")
            is ApplyResult.RolledBack -> ToolResult.Error("Rolled back: ${r.reason}")
            is ApplyResult.Rejected -> ToolResult.Error("Rejected: ${r.reason}")
        }
    }
}

class CompileCheckTool(private val svc: SelfModifyService) : ApexTool {
    override val metadata = ToolMetadata(
        id = "compile_check",
        name = "Compile Check",
        description = "Check workspace index/compile status. Use modify_code to trigger the full compile gate.",
        category = ToolCategory.GENERAL,
        isReadOnly = true
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult =
        ToolResult.Success("Indexed: ${svc.index.isIndexed()}. Use modify_code to trigger compile gate.")
}

class RollbackTool(private val svc: SelfModifyService) : ApexTool {
    override val metadata = ToolMetadata(
        id = "rollback",
        name = "Rollback",
        description = "Rollback the workspace to a prior git snapshot (by commit SHA, or last if omitted).",
        category = ToolCategory.GENERAL,
        isReadOnly = false
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val toCommit = arguments["commit"]?.toString()
        val ok = svc.rollback(toCommit)
        return if (ok) ToolResult.Success("Rolled back to ${toCommit ?: "last"}")
        else ToolResult.Error("Rollback failed")
    }
}

/**
 * Factory: instantiate all 5 self-modify tools bound to a [SelfModifyService].
 */
object SelfModifyTools {
    fun createAll(svc: SelfModifyService): List<ApexTool> = listOf(
        ReadSourceTool(svc),
        SearchCodeTool(svc),
        ModifyCodeTool(svc),
        CompileCheckTool(svc),
        RollbackTool(svc)
    )
}
