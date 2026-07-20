package com.apex.engine.tools.builtin

import android.content.Context
import com.apex.core.model.ApexTool
import com.apex.selfmodify.SelfModifyService
import com.apex.selfmodify.tools.SelfModifyTools

/**
 * 内置工具集合 — 提供默认工具注册。
 *
 * 当 [selfModify] 非空时，额外注册 5 个自改工具 (ReadSource / SearchCode /
 * ModifyCode / CompileCheck / Rollback)，让 Agent 能够读写、编译、回滚自身源码。
 * Per AGENT_SELF_MODIFY_SPEC §8.3.
 */
object BuiltInTools {

    fun createAll(
        context: Context,
        selfModify: SelfModifyService? = null
    ): List<ApexTool> {
        val tools = mutableListOf<ApexTool>(
            HttpGetTool(),
            AppListTool(context),
            DateTimeTool(),
            SafeShellTool(context)
        )
        if (selfModify != null) {
            tools += SelfModifyTools.createAll(selfModify)
        }
        return tools
    }
}
