package com.apex.util

// Minimal implementation (had 1 errors)
class StreamingJsonXmlConverter
sealed class Event
data class Tag(val data: String = "")
data class Content(val data: String = "")
enum class State { DEFAULT }
