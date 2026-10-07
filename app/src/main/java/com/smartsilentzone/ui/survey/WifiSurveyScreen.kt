package com.smartsilentzone.ui.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsilentzone.data.db.WifiApDao
import com.smartsilentzone.data.db.WifiApEntity
import com.smartsilentzone.sensor.WifiFloorDetector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSurveyScreen(
    wifiFloorDetector: WifiFloorDetector,
    wifiApDao: WifiApDao,
    onBack: () -> Unit
) {
    val floorState by wifiFloorDetector.floorState.collectAsState()
    val allAps by wifiApDao.getAllAps().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var zoneNameInput by remember { mutableStateOf("") }
    var floorInput by remember { mutableStateOf("") }
    var selectedPolicy by remember { mutableStateOf("SILENT") }
    var showSuccessToast by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi BSSID Floor Survey") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Connected AP Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connected Access Point (Live)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current BSSID: ${floorState.bssid ?: "Scanning / Disconnected"}",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    floorState.matchedZone?.let { match ->
                        Text(
                            text = "Mapped Zone: ${match.zoneName} (${match.floor}) • ${match.silentPolicy}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } ?: run {
                        Text(
                            text = "Not in Room survey table yet. Stand here & save below!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Survey Mode Inputs
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Map Connected BSSID to Floor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    OutlinedTextField(
                        value = zoneNameInput,
                        onValueChange = { zoneNameInput = it },
                        label = { Text("Zone Name (e.g. CSE 3rd Floor Lab)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = floorInput,
                        onValueChange = { floorInput = it },
                        label = { Text("Floor (e.g. 3rd Floor)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SILENT", "VIBRATE", "NORMAL").forEach { policy ->
                            FilterChip(
                                selected = selectedPolicy == policy,
                                onClick = { selectedPolicy = policy },
                                label = { Text(policy) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (zoneNameInput.isNotBlank() && floorInput.isNotBlank()) {
                                coroutineScope.launch {
                                    val saved = wifiFloorDetector.saveCurrentApSurvey(
                                        zoneName = zoneNameInput,
                                        floor = floorInput,
                                        silentPolicy = selectedPolicy
                                    )
                                    if (saved) {
                                        zoneNameInput = ""
                                        floorInput = ""
                                        showSuccessToast = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = floorState.bssid != null
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save This AP to Survey Table")
                    }
                }
            }

            // Existing Mapped APs list
            Text(
                text = "Registered Survey Points (${allAps.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allAps) { ap ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = ap.zoneName, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${ap.bssid} • ${ap.floor} • ${ap.silentPolicy}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    wifiApDao.deleteByBssid(ap.bssid)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
