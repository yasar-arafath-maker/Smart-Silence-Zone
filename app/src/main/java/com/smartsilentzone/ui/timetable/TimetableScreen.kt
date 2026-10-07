package com.smartsilentzone.ui.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartsilentzone.data.db.TimetableEntity
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.NeonCyan

@Composable
fun TimetableScreen(
    vm: TimetableViewModel = hiltViewModel()
) {
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val schedules by vm.schedulesForDay.collectAsStateWithLifecycle()
    val zones by vm.availableZones.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<TimetableEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Day selection tabs
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.daysOfWeek) { day ->
                FilterChip(
                    selected = day == selectedDay,
                    onClick = { vm.selectDay(day) },
                    label = { Text(day, fontWeight = if (day == selectedDay) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        if (schedules.isEmpty()) {
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
                            Icons.Default.EventNote,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = NeonCyan
                        )
                        Text(
                            "No Classes Scheduled for $selectedDay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Smart Silent Zone checks your timetable when you are inside campus to automatically apply lecture/lab ringer rules.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = {
                                editingSchedule = null
                                showAddDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Class Schedule")
                        }

                        OutlinedButton(
                            onClick = { vm.seedSampleTimetable(zones) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Seed Sample Weekly Timetable")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggle = { vm.toggleEnabled(schedule) },
                        onEdit = {
                            editingSchedule = schedule
                            showAddDialog = true
                        },
                        onDelete = { vm.deleteSchedule(schedule.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingSchedule = null
                showAddDialog = true
            },
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Class")
        }
    }

    if (showAddDialog) {
        AddEditScheduleDialog(
            initial = editingSchedule,
            day = selectedDay,
            zones = zones,
            onDismiss = { showAddDialog = false },
            onSave = { entity ->
                vm.upsertSchedule(entity)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: TimetableEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(schedule.subjectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${schedule.startTime} - ${schedule.endTime}", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                Text("Zone: ${schedule.zoneName}", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = schedule.enabled, onCheckedChange = { onToggle() })
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = { Text(schedule.soundMode) },
                leadingIcon = {
                    Icon(
                        if (schedule.soundMode == "SILENT") Icons.Default.VolumeOff else Icons.Default.Vibration,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditScheduleDialog(
    initial: TimetableEntity?,
    day: String,
    zones: List<Zone>,
    onDismiss: () -> Unit,
    onSave: (TimetableEntity) -> Unit
) {
    var subject by remember { mutableStateOf(initial?.subjectName ?: "") }
    var startTime by remember { mutableStateOf(initial?.startTime ?: "09:00 AM") }
    var endTime by remember { mutableStateOf(initial?.endTime ?: "10:30 AM") }
    var selectedZone by remember { mutableStateOf(zones.firstOrNull { it.id == initial?.zoneId } ?: zones.firstOrNull()) }
    var soundMode by remember { mutableStateOf(initial?.soundMode ?: "SILENT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Schedule ($day)" else "Edit Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject / Class Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start Time") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End Time") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Assigned Zone / Classroom", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    zones.take(4).forEach { zone ->
                        FilterChip(
                            selected = selectedZone?.id == zone.id,
                            onClick = { selectedZone = zone },
                            label = { Text(zone.name) }
                        )
                    }
                }

                Text("Sound Mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SILENT", "VIBRATE", "DND").forEach { mode ->
                        FilterChip(
                            selected = soundMode == mode,
                            onClick = { soundMode = mode },
                            label = { Text(mode) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subject.isNotBlank()) {
                        onSave(
                            TimetableEntity(
                                id = initial?.id ?: 0L,
                                subjectName = subject.trim(),
                                dayOfWeek = day,
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                zoneId = selectedZone?.id ?: "zone_default",
                                zoneName = selectedZone?.name ?: "Campus",
                                soundMode = soundMode,
                                enabled = initial?.enabled ?: true
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
