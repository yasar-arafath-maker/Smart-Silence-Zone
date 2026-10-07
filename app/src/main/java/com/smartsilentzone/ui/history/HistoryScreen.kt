package com.smartsilentzone.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartsilentzone.data.db.HistoryEntity
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.EmeraldGreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple

@Composable
fun HistoryScreen(
    vm: HistoryViewModel = hiltViewModel()
) {
    val entries by vm.historyEntries.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Geofence Activity Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row {
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { vm.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { vm.seedDemoHistory() }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Seed Demo", color = NeonCyan)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.HistoryToggleOff,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = NeonCyan
                        )
                        Text(
                            "No Transitions Logged Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Every time you dwell in a zone for 30s or exit after verification, a timestamped transition entry is recorded here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { vm.seedDemoHistory() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF0A0D14))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Simulate Transition Events", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    TimelineItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun TimelineItemCard(item: HistoryEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vertical Timeline Node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (item.newMode == "SILENT" || item.newMode == "DND") NeonPurple else EmeraldGreen)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(68.dp)
                    .background(Color(0x3300E5FF))
            )
        }

        // Timeline Content Card
        GlassmorphicCard(
            modifier = Modifier.weight(1f),
            glowColor = if (item.newMode == "SILENT") NeonPurple else NeonCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.zoneName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.formattedDateTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                item.eventType,
                style = MaterialTheme.typography.bodySmall,
                color = NeonCyan
            )

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.previousMode, style = MaterialTheme.typography.labelSmall) }
                )
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCyan)
                AssistChip(
                    onClick = {},
                    label = { Text(item.newMode, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (item.newMode == "SILENT" || item.newMode == "DND") NeonPurple else EmeraldGreen,
                        labelColor = Color.White
                    )
                )
            }
        }
    }
}
