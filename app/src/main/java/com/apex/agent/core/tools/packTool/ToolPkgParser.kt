package com.apex.core.tools.packTool

// Minimal implementation (had 25 errors)
enum class ToolPkgSourceType { DEFAULT }
data class ToolPkgResourceRuntime(val data: String = "")
data class ToolPkgUiModuleRuntime(val data: String = "")
data class ToolPkgAppLifecycleHookRuntime(val data: String = "")
data class ToolPkgFunctionHookRuntime(val data: String = "")
data class ToolPkgTagFunctionHookRuntime(val data: String = "")
data class ToolPkgSubpackageRuntime(val data: String = "")
data class ToolPkgContainerRuntime(val data: String = "")
data class ToolPkgLoadResult(val data: String = "")
data class ToolPkgManifest(val data: String = "")
data class ToolPkgManifestSubpackage(val data: String = "")
data class ToolPkgManifestResource(val data: String = "")
data class ToolPkgRegisteredUiModule(val data: String = "")
data class ToolPkgRegisteredAppLifecycleHook(val data: String = "")
data class ToolPkgRegisteredFunctionHook(val data: String = "")
data class ToolPkgRegisteredTagFunctionHook(val data: String = "")
data class ToolPkgMainRegistration(val data: String = "")
object ToolPkgArchiveParser
