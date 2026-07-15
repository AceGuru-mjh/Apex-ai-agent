package com.apex.agent.core.workflow.enhanced.observability

// STUBBED: had 4 errors
enum class SpanStatus { DEFAULT }
interface Span
data class SpanRecord(val placeholder: String = "")
data class SpanEvent(val placeholder: String = "")
interface WorkflowTracer
class InMemoryTracer
class ActiveSpan
object NoopTracer
object NoopSpan
object TracerHolder
