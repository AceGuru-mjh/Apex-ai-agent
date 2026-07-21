package com.apex.ui.features.selfmodify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.selfmodify.SelfModifyService
import com.apex.selfmodify.audit.AuditEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuditLogUiState(
    val verifying: Boolean = true,
    val verified: Boolean? = null,
    val entries: List<AuditEntry> = emptyList(),
    val error: String? = null
)

/**
 * Backs [AuditLogScreen]. Loads the tamper-evidence check result + the full
 * audit entry list from [SelfModifyService.audit] on init.
 *
 * Per AGENT_SELF_MODIFY_SPEC §9 Phase 4 ("审计日志查看页") + §7.3 (tamper-evident).
 */
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    val selfModifyService: SelfModifyService
) : ViewModel() {

    private val _state = MutableStateFlow(AuditLogUiState())
    val state: StateFlow<AuditLogUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(verifying = true, error = null)
        viewModelScope.launch {
            try {
                val ok = selfModifyService.audit.verify()
                val list = selfModifyService.audit.listEntries()
                _state.value = AuditLogUiState(verifying = false, verified = ok, entries = list)
            } catch (e: Exception) {
                _state.value = AuditLogUiState(
                    verifying = false, verified = null, error = e.message ?: "读取审计日志失败"
                )
            }
        }
    }
}
