package com.smartsilentzone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.EmeraldGreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple

@Composable
fun StatusScreen(
    vm: ZoneEvaluationViewModel,
    state: ZoneUiState,
    onNavigateToZones: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Automation Status Card
        val isReady = state.permissions?.readyForAutomation == true
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = if (isReady) EmeraldGreen else MaterialTheme.colorScheme.error
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isReady) EmeraldGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "Automation Guard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isReady) "ONLINE" else "ACTION NEEDED",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (isReady) EmeraldGreen else MaterialTheme.colorScheme.error
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (isReady) "Smart Silent Zone is actively monitoring college perimeters with 30-second sustained dwell detection."
                else "Some permissions or GPS services are inactive. Head to the Security tab to enable full automation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Ringer Status Card
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = NeonCyan
        ) {
            Text(
                "Hardware Sound Mode",
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyan
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (state.deviceAction) {
                            RingerAction.SILENT -> Icons.Default.VolumeOff
                            RingerAction.VIBRATE -> Icons.Default.Vibration
                            RingerAction.DND -> Icons.Default.DoNotDisturbOn
                            RingerAction.NORMAL -> Icons.Default.VolumeUp
                        },
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        state.deviceAction.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.targetAction != null) {
                    Text(
                        "Target: ${state.targetAction.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonPurple
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Divider(color = Color(0x2200E5FF))
            Spacer(Modifier.height(14.dp))

            StatusInfoRow(
                icon = Icons.Default.Place,
                label = "Active Detected Zone",
                value = state.activeZone?.name ?: "Outside Campus Perimeters"
            )

            Spacer(Modifier.height(8.dp))

            StatusInfoRow(
                icon = Icons.Default.Info,
                label = "Evaluator Reason",
                value = if (state.targetReason.isNotBlank()) state.targetReason else "Strict Unsilent outside college"
            )

            Spacer(Modifier.height(8.dp))

            StatusInfoRow(
                icon = Icons.Default.Layers,
                label = "Total Active Zones",
                value = "${state.zones.size} registered"
            )
        }

        // 30-Second Rule Protocol Card
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = NeonPurple
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = NeonPurple)
                Text(
                    "30-Second Boundary Hysteresis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "• Sustained Entry: Uses Play Services DWELL transition with 30,000ms loitering delay before silencing.\n" +
                "• Debounced Exit: 30s WorkManager timer + one-shot Fused Location check verifies true exit before restoring ringer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action Buttons
        Button(
            onClick = { vm.forceEvaluate() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !state.isSyncing,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = Color(0xFF0A0D14)
            )
        ) {
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0A0D14))
                Spacer(Modifier.width(8.dp))
                Text("Syncing Geofences...")
            } else {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync & Re-evaluate Perimeters", fontWeight = FontWeight.Bold)
            }
        }

        if (state.zones.isEmpty()) {
            OutlinedButton(
                onClick = onNavigateToZones,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Configure Zones & Map")
            }
        }
    }
}

@Composable
private fun StatusInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
