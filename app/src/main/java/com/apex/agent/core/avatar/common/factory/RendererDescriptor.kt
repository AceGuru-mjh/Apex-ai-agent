package com.apex.agent.core.avatar.common.factory

// Minimal implementation (had 1 errors)
sealed class RendererDescriptor
data class WebP(val data: String = "")
data class Mp4(val data: String = "")
data class Mmd(val data: String = "")
data class Gltf(val data: String = "")
data class Fbx(val data: String = "")
