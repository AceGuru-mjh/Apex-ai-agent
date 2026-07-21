package com.apex.selfmodify.confirm

import com.apex.selfmodify.plan.ModificationPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A pending risk-confirmation request emitted to the UI layer.
 *
 * Per AGENT_SELF_MODIFY_SPEC §7.2, HIGH / CRITICAL plans must be approved by
 * the user before [com.apex.selfmodify.plan.PlanExecutor] touches the
 * workspace. The service side calls [ConfirmationManager.requestConfirmation]
 * (suspending) which blocks until the UI calls [approve] / [reject].
 */
data class ConfirmationRequest(
    val id: String,
    val plan: ModificationPlan,
    val reason: String
)

sealed class ConfirmationResult {
    data class Approved(val planId: String) : ConfirmationResult()
    data class Rejected(val planId: String, val reason: String) : ConfirmationResult()
}

/**
 * Bridges the self-modify service (background coroutine, calls `apply()`) and
 * the Compose UI (shows a confirmation dialog).
 *
 * - Service side: `suspend fun requestConfirmation(plan)` — emits a request on
 *   [pendingRequests] and suspends until the UI resolves it.
 * - UI side: collect [pendingRequests], render a dialog, then call
 *   [approve] / [reject] with the plan id.
 *
 * Thread-safety: the internal `pending` map is accessed from the service
 * coroutine (IO dispatcher) and the UI (main dispatcher). A single
 * [CompletableDeferred] per plan id is the synchronization point; a
 * [java.util.concurrent.ConcurrentHashMap] guards the rare overlap when a new
 * request arrives before the previous one is resolved.
 */
class ConfirmationManager {
    private val _pendingRequests =
        MutableSharedFlow<ConfirmationRequest>(extraBufferCapacity = 16)
    val pendingRequests: SharedFlow<ConfirmationRequest> = _pendingRequests.asSharedFlow()

    private val pending =
        java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Called from [com.apex.selfmodify.SelfModifyService.apply]. Emits the
     * request to the UI and suspends until [approve] / [reject] is called.
     * Returns `true` if the user approved, `false` otherwise.
     */
    suspend fun requestConfirmation(plan: ModificationPlan): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val request = ConfirmationRequest(plan.id, plan, plan.reason)
        pending[plan.id] = deferred
        // tryEmit never suspends (extraBufferCapacity=16); fall back to emit
        // (suspending) if the buffer is somehow full.
        if (!_pendingRequests.tryEmit(request)) {
            _pendingRequests.emit(request)
        }
        return deferred.await()
    }

    fun approve(planId: String) {
        pending.remove(planId)?.complete(true)
    }

    fun reject(planId: String, reason: String = "rejected by user") {
        pending.remove(planId)?.complete(false)
    }

    /** For diagnostics / tests: is a given plan id currently awaiting a reply? */
    fun isPending(planId: String): Boolean = pending.containsKey(planId)
}
