package com.apex.agent.core.patterns

// STUBBED: had 1 errors
class Subscription
interface Observable
class ObservableImpl
sealed class AgentLifecycleEvent
object Initialized
data class StateChanged(val placeholder: String = "")
data class ErrorOccurred(val placeholder: String = "")
data class MetricsUpdated(val placeholder: String = "")
object Shutdown
interface AgentStateObserver
class AgentStateObservable
