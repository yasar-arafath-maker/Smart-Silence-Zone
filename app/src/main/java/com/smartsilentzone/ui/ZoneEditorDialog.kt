package com.smartsilentzone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneType
import com.smartsilentzone.ui.components.InteractiveZoneRadarMap
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditorDialog(
    initialZone: Zone? = null,
    onDismiss: () -> Unit,
    onSave: (Zone) -> Unit,
    onFetchCurrentLocation: suspend () -> android.location.Location?
) {
    var name by remember { mutableStateOf(initialZone?.name ?: "") }
    var selectedType by remember { mutableStateOf(initialZone?.type ?: ZoneType.CLASSROOM) }
    var latitude by remember { mutableDoubleStateOf(initialZone?.latitude ?: 13.0827) }
    var longitude by remember { mutableDoubleStateOf(initialZone?.longitude ?: 80.2707) }
    var radiusMeters by remember { mutableFloatStateOf(initialZone?.radiusMeters ?: 60f) }
    var actionOverride by remember { mutableStateOf<RingerAction?>(initialZone?.actionOverride) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialZone == null) "Add New Zone" else "Edit Zone",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Interactive Radar Map
                InteractiveZoneRadarMap(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                    onCoordinatesChanged = { lat, lng ->
                        latitude = lat
                        longitude = lng
                    },
                    onRadiusChanged = { r -> radiusMeters = r },
                    onLocateMe = {
                        coroutineScope.launch {
                            val loc = onFetchCurrentLocation()
                            if (loc != null) {
                                latitude = loc.latitude
                                longitude = loc.longitude
                            } else {
                                errorMessage = "Location fix unavailable"
                            }
                        }
                    }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Zone Name") },
                    placeholder = { Text("e.g. CS Seminar Hall, Library") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Zone Category & Priority", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZoneType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = {
                                Text("${type.name} (Priority ${type.priority} • ${type.defaultAction?.name ?: "Campus Boundary"})")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Radius: ${radiusMeters.toInt()} meters", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (radiusMeters < 50f) "Small indoor" else if (radiusMeters > 200f) "Campus scale" else "Standard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = 25f..600f,
                        steps = 22
                    )
                }

                Text("Custom Ringer Action (Optional Override)", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val actions = listOf(null to "Default") + RingerAction.values().map { it to it.name }
                    actions.forEach { (act, label) ->
                        FilterChip(
                            selected = actionOverride == act,
                            onClick = { actionOverride = act },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Please enter a zone name"
                        return@Button
                    }
                    val zone = Zone(
                        id = initialZone?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        type = selectedType,
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = radiusMeters,
                        actionOverride = actionOverride,
                        enabled = initialZone?.enabled ?: true
                    )
                    onSave(zone)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
