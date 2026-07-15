package com.apex.agent.core.streaming.output

// STUBBED: had 241 errors
class OutputBlockStream
sealed class BlockUpdate
data class Created(val placeholder: String = "")
data class Updated(val placeholder: String = "")
data class Completed(val placeholder: String = "")
data class Failed(val placeholder: String = "")
