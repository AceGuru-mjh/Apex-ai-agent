package com.apex.agent.core.workflow.enhanced.expression

// Minimal implementation (had 4 errors)
class ExpressionEvaluator
class Tokenizer
sealed class Token
data class Variable(val data: String = "")
data class StringLiteral(val data: String = "")
data class NumberLiteral(val data: String = "")
data class BooleanLiteral(val data: String = "")
object NullLiteral
data class Identifier(val data: String = "")
data class Operator(val data: String = "")
object QuestionMark
object Colon
object LParen
object RParen
object Comma
object Dot
sealed class ASTNode
data class VariableRef(val data: String = "")
data class BinaryOp(val data: String = "")
data class UnaryOp(val data: String = "")
data class MethodCall(val data: String = "")
data class Ternary(val data: String = "")
class Parser
