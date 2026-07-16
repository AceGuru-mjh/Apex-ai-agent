package com.apex.agent.core.workflow.enhanced.saga

// Minimal implementation (had 15 errors)
data class SagaStep(val data: String = "")
sealed class SagaResult
data class Compensated(val data: String = "")
data class CompensationResult(val data: String = "")
class Saga
sealed class SagaEvent
data class StepStarted(val data: String = "")
data class StepCompleted(val data: String = "")
data class StepFailed(val data: String = "")
data class CompensationStarted(val data: String = "")
data class CompensationCompleted(val data: String = "")
data class CompensationFailed(val data: String = "")
data class SagaCompleted(val data: String = "")
class ObservableSaga
