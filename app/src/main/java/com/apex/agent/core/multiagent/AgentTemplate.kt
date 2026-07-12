package com.apex.agent.core.multiagent

import android.os.Parcelable
import com.google.gson.Gson
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class AgentTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val category: String = "通用",
    val roleCardId: String? = null,
    val modelCardId: String? = null,
    val examplePrompt: String = "",
    val tags: Set<String> = emptySet(),
    val isDefault: Boolean = false,
    val icon: String = "??",
    val usageCount: Int = 0,
    val rating: Float = 0f,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis()
) : Parcelable {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): AgentTemplate? = try { Gson().fromJson(json, AgentTemplate::class.java) } catch (e: Exception) { null }

        fun getDefaultTemplates(): List<AgentTemplate> {
            return listOf(
                AgentTemplate(name = "万能助手", description = "通用全能助手，适合各种任务", category = "通用", examplePrompt = "我需要你的帮助来完成一项任务..", tags = setOf("通用", "助手", "日常"), isDefault = true, icon = "??"),
                AgentTemplate(name = "研究分析常, description = "深度研究和分析专定, category = "研究", examplePrompt = "请帮我研究一为..", tags = setOf("研究", "分析", "深度"), isDefault = true, icon = "??"),
                AgentTemplate(name = "代码工程常, description = "专业编程和技术实现, category = "开取, examplePrompt = "请帮我实现一个功能..", tags = setOf("编程", "代码", "开取), isDefault = true, icon = "??"),
                AgentTemplate(name = "创意设计常, description = "创意设计和艺术创作, category = "设计", examplePrompt = "请帮我设计一为..", tags = setOf("设计", "创意", "艺术"), isDefault = true, icon = "??"),
                AgentTemplate(name = "写作专家", description = "优秀的文章和内容创作", category = "写作", examplePrompt = "请帮我写一篇..", tags = setOf("写作", "文章", "内容"), isDefault = true, icon = "??"),
                AgentTemplate(name = "语言翻译", description = "多语言翻译和文化转据, category = "语言", examplePrompt = "请帮我翻试..", tags = setOf("翻译", "语言", "跨文化), isDefault = true, icon = "??"),
                AgentTemplate(name = "项目经理", description = "项目管理和团队协调", category = "管理", examplePrompt = "请帮我规分..", tags = setOf("管理", "规划", "协调"), isDefault = true, icon = "??"),
                AgentTemplate(name = "学习导师", description = "教育和知识传接", category = "教育", examplePrompt = "请教成..", tags = setOf("学习", "教育", "导师"), isDefault = true, icon = "??")
            )
        }
    }
}
