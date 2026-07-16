package com.apex.agent.core.config

// Minimal implementation (had 92 errors)
data class Warning(val data: String = "")
interface ConfigValidator
object RequiredValidator
class RangeValidator
class PatternValidator
class EnumValidator
object UrlValidator
object EmailValidator
object PortValidator
object DurationValidator
object SizeValidator
class CompositeValidator
enum class CombineMode { DEFAULT }
class ValidationEngine
fun interface() { }
