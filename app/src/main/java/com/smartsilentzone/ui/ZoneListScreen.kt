package com.smartsilentzone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneType
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.EmeraldGreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple
import kotlinx.coroutines.launch

@Composable
fun ZoneListScreen(
    vm: ZoneEvaluationViewModel,
    state: ZoneUiState,
    onOpenAddZone: () -> Unit,
    onEditZone: (Zone) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    if (state.zones.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonCyan
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = NeonCyan
                    )
                    Text(
                        "No Campus Zones Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Create college boundary and lecture zones to enable automatic silent mode with the 30-second sustained dwell rule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onOpenAddZone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF0A0D14))
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Custom Zone", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val loc = vm.getCurrentLocation()
                                val lat = loc?.latitude ?: 13.0827
                                val lng = loc?.longitude ?: 80.2707
                                vm.seedCampusTemplates(lat, lng)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = NeonPurple)
                        Spacer(Modifier.width(8.dp))
                        Text("Seed College Preset Zones", color = NeonPurple)
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Registered Geofences (${state.zones.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val loc = vm.getCurrentLocation()
                            val lat = loc?.latitude ?: 13.0827
                            val lng = loc?.longitude ?: 80.2707
                            vm.seedCampusTemplates(lat, lng)
                        }
                    }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Presets", color = NeonCyan)
                    }
                }
            }

            items(state.zones, key = { it.id }) { zone ->
                val isInside = zone.id in state.insideZones.map { it.id }
                ZoneGlassCard(
                    zone = zone,
                    isInside = isInside,
                    onToggleEnabled = { vm.toggleZoneEnabled(zone) },
                    onEdit = { onEditZone(zone) },
                    onDelete = { vm.deleteZone(zone.id) }
                )
            }
        }
    }
}

@Composable
fun ZoneGlassCard(
    zone: Zone,
    isInside: Boolean,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (isInside) EmeraldGreen else NeonCyan
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isInside) EmeraldGreen.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (zone.type) {
                                ZoneType.COLLEGE -> Icons.Default.School
                                ZoneType.LIBRARY -> Icons.Default.LocalLibrary
                                ZoneType.CLASSROOM -> Icons.Default.MeetingRoom
                                ZoneType.EXAM_HALL -> Icons.Default.FactCheck
                                ZoneType.CANTEEN, ZoneType.BREAK_ZONE -> Icons.Default.Restaurant
                            },
                            contentDescription = null,
                            tint = if (isInside) EmeraldGreen else NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        zone.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Radius: ${zone.radiusMeters.toInt()}m • ${zone.type.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = zone.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text("Priority ${zone.type.priority}", style = MaterialTheme.typography.labelSmall) }
            )
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        when (zone.action) {
                            RingerAction.SILENT -> "Silent"
                            RingerAction.VIBRATE -> "Vibrate"
                            RingerAction.DND -> "Do Not Disturb"
                            RingerAction.NORMAL -> "Normal Sound"
                            null -> "Campus Boundary"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            if (isInside) {
                AssistChip(
                    onClick = {},
                    label = { Text("INSIDE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = EmeraldGreen,
                        labelColor = Color(0xFF0A0D14)
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                Spacer(Modifier.width(4.dp))
                Text("Edit", color = NeonCyan)
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
        }
    }
}
