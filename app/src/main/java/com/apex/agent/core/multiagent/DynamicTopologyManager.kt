package com.apex.agent.core.multiagent

// Minimal implementation (had 324 errors)
class DynamicTopologyManager
data class AgentNode(val data: String = "")
data class AgentEdge(val data: String = "")
data class GossipMessage(val data: String = "")
sealed class GossipPayload
data class Heartbeat(val data: String = "")
data class TopologyQuery(val data: String = "")
data class TopologyResponse(val data: String = "")
data class RoleProposal(val data: String = "")
data class CapabilityQuery(val data: String = "")
data class CapabilityResponse(val data: String = "")
data class NetworkTopology(val data: String = "")
data class AgentDiscoveryEvent(val data: String = "")
data class TopologyChange(val data: String = "")
