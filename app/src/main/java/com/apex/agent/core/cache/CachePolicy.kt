package com.apex.agent.core.cache

// Minimal implementation (had 40 errors)
sealed class CachePolicy
data class TtlPolicy(val data: String = "")
data class LruPolicy(val data: String = "")
data class LfuPolicy(val data: String = "")
data class FifoPolicy(val data: String = "")
data class HybridPolicy(val data: String = "")
