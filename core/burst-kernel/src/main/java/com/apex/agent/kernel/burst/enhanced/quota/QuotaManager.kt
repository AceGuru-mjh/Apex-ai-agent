package com.apex.agent.kernel.burst.enhanced.quota

class QuotaManager

data class ResourceQuota(val placeholder: String = "")

data class QuotaUsage(val placeholder: String = "")

data class QuotaLease(val placeholder: String = "")

enum class QuotaExceedAction { DEFAULT }

data class QuotaStatus(val placeholder: String = "")
