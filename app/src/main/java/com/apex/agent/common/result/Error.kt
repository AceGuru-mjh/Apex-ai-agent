package com.apex.agent.common.result

// Minimal implementation (had 4 errors)
sealed class AppError
class DatabaseError
class AuthError
class TimeoutError
class NotFoundError
class PermissionError
class UnknownError
