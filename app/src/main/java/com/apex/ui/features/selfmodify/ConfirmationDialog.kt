package com.apex.ui.features.selfmodify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apex.selfmodify.confirm.ConfirmationRequest
import com.apex.selfmodify.plan.RiskLevel

/**
 * Risk-confirmation dialog for HIGH / CRITICAL self-modify plans.
 *
 * Per AGENT_SELF_MODIFY_SPEC §7.2 + §9 Phase 4:
 * - Shows the risk level (color-coded), the plan reason, and the list of
 *   files to be changed.
 * - For CRITICAL plans, an extra warning banner is displayed.
 * - The user must explicitly approve / reject. Dismissing the dialog (back
 *   gesture / tap-outside) is treated as a rejection.
 *
 * The caller owns the [ConfirmationRequest] state and is responsible for
 * forwarding [onApprove] / [onReject] to [com.apex.selfmodify.confirm.ConfirmationManager].
 */
@Composable
fun SelfModifyConfirmationDialog(
    request: ConfirmationRequest?,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (request == null) return
    val plan = request.plan
    AlertDialog(
        onDismissRequest = { onReject(plan.id) },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("代码修改确认", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "风险等级: ${plan.riskLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (plan.riskLevel) {
                        RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
                        RiskLevel.HIGH -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("原因: ${plan.reason}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "修改文件 (${plan.changes.size}):",
                    style = MaterialTheme.typography.labelLarge
                )
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(plan.changes) { change ->
                        Text(
                            text = "${change.type}: ${change.path}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (plan.riskLevel == RiskLevel.CRITICAL) {
                    Text(
                        "⚠️ CRITICAL 修改可能影响安全/稳定性，请仔细确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApprove(plan.id) }) { Text("批准") }
        },
        dismissButton = {
            TextButton(onClick = { onReject(plan.id) }) { Text("拒绝") }
        }
    )
}
