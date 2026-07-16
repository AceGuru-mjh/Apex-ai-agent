package com.apex.agent.core.streaming.output

// Minimal implementation (had 285 errors)
class OutputBlockStream
sealed class BlockUpdate
data class Updated(val data: String = "")
