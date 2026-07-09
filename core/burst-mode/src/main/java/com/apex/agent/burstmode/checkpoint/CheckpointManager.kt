package com.apex.agent.burstmode.checkpoint

data class TaskCheckpoint(val placeholder: String = "")

interface CheckpointStore

class InMemoryCheckpointStore

class FileCheckpointStore

class CheckpointManager
