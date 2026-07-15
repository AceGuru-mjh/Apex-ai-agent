package com.apex.util.stream

// STUBBED: had 3 errors
interface SharedStream
interface MutableSharedStream
interface StateStream
interface MutableStateStream
class MutableSharedStreamImpl
interface SharedEvent
data class Value(val placeholder: String = "")
data class Completion(val placeholder: String = "")
class MutableStateStreamImpl
enum class StreamStart { DEFAULT }
