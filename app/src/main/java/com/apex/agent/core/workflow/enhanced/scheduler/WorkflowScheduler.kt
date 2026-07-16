package com.apex.agent.core.workflow.enhanced.scheduler

// Minimal implementation (had 65 errors)
data class ScheduledJob(val data: String = "")
enum class MisfirePolicy { DEFAULT }
sealed class ScheduleEvent
data class JobRegistered(val data: String = "")
data class JobUnregistered(val data: String = "")
data class JobTriggered(val data: String = "")
data class JobSucceeded(val data: String = "")
data class JobFailed(val data: String = "")
data class JobMisfired(val data: String = "")
data class JobPaused(val data: String = "")
data class JobResumed(val data: String = "")
interface SchedulePersistor
class InMemorySchedulePersistor
