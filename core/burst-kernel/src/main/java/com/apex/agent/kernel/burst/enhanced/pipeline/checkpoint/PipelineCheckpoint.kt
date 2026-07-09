package com.apex.agent.kernel.burst.enhanced.pipeline.checkpoint

class PipelineCheckpoint

data class PipelineCheckpointData(val placeholder: String = "")

data class RecoveryPlan(val placeholder: String = "")

enum class RecoveryStrategy { DEFAULT }

data class CheckpointStats(val placeholder: String = "")
