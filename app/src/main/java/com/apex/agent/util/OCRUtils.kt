package com.apex.util

// STUBBED: had 1 errors
object OCRUtils
enum class Language { DEFAULT }
enum class Quality { DEFAULT }
sealed class OCRResult
data class Success(val placeholder: String = "")
data class Error(val placeholder: String = "")
