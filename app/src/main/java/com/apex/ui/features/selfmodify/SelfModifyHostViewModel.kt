package com.apex.ui.features.selfmodify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.selfmodify.SelfModifyService
import com.apex.selfmodify.confirm.ConfirmationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Host-level Hilt ViewModel that bridges the [SelfModifyService.confirmation]
 * SharedFlow to Compose state.
 *
 * A single instance of this VM is scoped to the [com.apex.ui.navigation.ApexNavHost]
 * composable graph (via `hiltViewModel()` at the top level) so the confirmation
 * dialog is shown regardless of which tab the user is on.
 *
 * Per AGENT_SELF_MODIFY_SPEC §9 Phase 4 ("风险分级 + 用户确认 UI").
 */
@HiltViewModel
class SelfModifyHostViewModel @Inject constructor(
    val selfModifyService: SelfModifyService
) : ViewModel() {

    private val _pendingRequest = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingRequest: StateFlow<ConfirmationRequest?> = _pendingRequest.asStateFlow()

    init {
        viewModelScope.launch {
            selfModifyService.confirmation.pendingRequests.collect { req ->
                _pendingRequest.value = req
            }
        }
    }

    fun approve(planId: String) {
        selfModifyService.confirmation.approve(planId)
        _pendingRequest.value = null
    }

    fun reject(planId: String) {
        selfModifyService.confirmation.reject(planId)
        _pendingRequest.value = null
    }
}
