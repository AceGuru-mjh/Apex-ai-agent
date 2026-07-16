package com.apex.agent.core.workflow.enhanced.templates

// Minimal implementation (had 6 errors)
enum class TemplateParamType { DEFAULT }
data class TemplateParameter(val data: String = "")
data class TemplateMeta(val data: String = "")
data class WorkflowTemplate(val data: String = "")
sealed class TemplateInstallResult
data class MissingParameters(val data: String = "")
interface WorkflowTemplateRegistry
class InMemoryTemplateRegistry
object TemplateRegistryHolder
