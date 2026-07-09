package com.apex.apk.rage.events

class RageEventBridge

class RageEvent

data class StateChanged(val placeholder: String = "")

data class TaskEnqueued(val placeholder: String = "")

data class TaskStarted(val placeholder: String = "")

data class TaskProgress(val placeholder: String = "")

data class TaskSucceeded(val placeholder: String = "")

data class TaskFailed(val placeholder: String = "")

data class TaskCancelled(val placeholder: String = "")

data class ConfigUpdated(val placeholder: String = "")

data class PresetSwitched(val placeholder: String = "")

data class SkillRegistered(val placeholder: String = "")

data class SkillUnregistered(val placeholder: String = "")

data class MetricsUpdated(val placeholder: String = "")

data class HealthChecked(val placeholder: String = "")

object Shutdown
