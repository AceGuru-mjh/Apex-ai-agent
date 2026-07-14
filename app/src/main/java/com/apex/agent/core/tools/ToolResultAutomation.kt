package com.apex.core.tools

import com.apex.api.voice.HttpTtsResponsePipelineStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.Locale


/**
 * Automation domain result data classes.
 * Split from ToolResultDataClasses.kt for maintainability.
 * Package kept as [com.apex.core.tools] to avoid breaking existing imports.
 */

@Serializable
data class AutomationConfigSearchResult(
    val searchPackageName: String?,
    val searchAppName: String?,
    val foundConfigs: List<ConfigInfo>,
    val totalFound: Int
) : ToolResultData() {
    
    @Serializable
    data class ConfigInfo(
        val appName: String,
        val packageName: String,
        val description: String,
        val isBuiltIn: Boolean,
        val fileName: String,
        val matchType: String  // "packageName" or "appName"
    )
        override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Automation Configuration Search Result:")
        if (!searchPackageName.isNullOrBlank()) {
            sb.appendLine("Search Package Name: ${searchPackageName}")
        }
        if (!searchAppName.isNullOrBlank()) {
            sb.appendLine("Search App Name: ${searchAppName}")
        }
        sb.appendLine("Found ${totalFound} matching configurations:")
        if (foundConfigs.isEmpty()) {
            sb.appendLine("No matching automation configurations found")
        } else {
            foundConfigs.forEach { config ->
                sb.appendLine()
        sb.appendLine("App Name: ${config.appName}")
        sb.appendLine("Package Name: ${config.packageName}")
        sb.appendLine("Description: ${config.description}")
        val _kaptFix23 = if (config.isBuiltIn) "Built-in" else "User Imported"
        sb.appendLine("Type: ${_kaptFix23}")
        val _kaptFix22 = if (config.matchType == "packageName") "Package Name" else "App Name"
        sb.appendLine("Match Type: ${_kaptFix22}")
            }
        }
        return sb.toString()
    }
}

/** 自动化计划参数结果数据*/

@Serializable
data class AutomationPlanParametersResult(
    val functionName: String,
    val targetPackageName: String?,
    val requiredParameters: List<ParameterInfo>,
    val planSteps: Int,
    val planDescription: String
) : ToolResultData() {
    
    @Serializable
    data class ParameterInfo(
        val key: String,
        val description: String,
        val type: String,
        val isRequired: Boolean,
        val defaultValue: String?
    )
        override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Automation Plan Parameters Information:")
        sb.appendLine("Function Name: ${functionName}")
        targetPackageName?.let { sb.appendLine("Target App: ${it}") }
        sb.appendLine("Plan Description: ${planDescription}")
        sb.appendLine()
        if (requiredParameters.isEmpty()) {
            sb.appendLine("This function does not require additional parameters and can be executed directly.")
        } else {
            sb.appendLine("Required Parameters (${requiredParameters.size} total):")
        requiredParameters.forEach { param ->
                sb.appendLine()
        sb.appendLine("Parameter Name: ${param.key}")
        sb.appendLine("Description: ${param.description}")
        sb.appendLine("Type: ${param.type}")
        val _kaptFix21 = if (param.isRequired) "Yes" else "No"
        sb.appendLine("Required: ${_kaptFix21}")
        param.defaultValue?.let { sb.appendLine("Default Value: ${it}") }
            }
        }
        return sb.toString()
    }
}

/** 自动化执行结果数据*/

@Serializable
data class AutomationExecutionResult(
    val functionName: String,
    val providedParameters: Map<String, String>,
    val agentId: String? = null,
    val displayId: Int? = null,
    val executionSuccess: Boolean,
    val executionMessage: String,
    val executionError: String?,
    val finalState: UIStateInfo?,
    val executionSteps: Int
) : ToolResultData() {
    
    @Serializable
    data class UIStateInfo(
        val nodeId: String,
        val packageName: String,
        val activityName: String
    )
        override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Automation Execution Result:")
        sb.appendLine("Function Name: ${functionName}")
        agentId?.let { sb.appendLine("AgentId: ${it}") }
        val _kaptFix20 = if (executionSuccess) "Success" else "Failure"
        sb.appendLine("Execution Status: ${_kaptFix20}")
        sb.appendLine("Execution Steps: ${executionSteps}")
        sb.appendLine("Result Message: ${executionMessage}")
        if (!executionError.isNullOrBlank()) {
            sb.appendLine("Error Message: ${executionError}")
        }
        if (providedParameters.isNotEmpty()) {
            sb.appendLine("\nUsed Parameters:")
        providedParameters.forEach { (key, value) ->
                sb.appendLine("  ${key}: ${value}")
            }
        }
        finalState?.let { state ->
            sb.appendLine("\nFinal State:")
        sb.appendLine("  Node ID: ${state.nodeId}")
        sb.appendLine("  Package Name: ${state.packageName}")
        sb.appendLine("  Activity: ${state.activityName}")
        }
        return sb.toString()
    }
}

/** 自动化功能列表结果数据*/

@Serializable
data class AutomationFunctionListResult(
    val packageName: String?,
    val functions: List<FunctionInfo>,
    val totalCount: Int
) : ToolResultData() {
    
    @Serializable
    data class FunctionInfo(
        val name: String,
        val description: String,
        val targetNodeName: String
    )
        override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Available Automation Functions:")
        packageName?.let { sb.appendLine("Package Name: ${it}") }
        sb.appendLine("Function Count: ${totalCount}")
        sb.appendLine()
        if (functions.isEmpty()) {
            sb.appendLine("No automation functions available")
        } else {
            functions.forEach { func ->
                sb.appendLine("Function Name: ${func.name}")
        sb.appendLine("Description: ${func.description}")
        sb.appendLine("Target Page: ${func.targetNodeName}")
        sb.appendLine()
            }
        }
        return sb.toString()
    }
}

/** 终端会话创建结果数据 */

