package com.smartsilentzone.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneType
import com.smartsilentzone.ui.ZoneUiState
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.DarkBackground
import com.smartsilentzone.ui.theme.EmeraldGreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple
import kotlin.math.cos

@Composable
fun CampusMapExplorerScreen(
    state: ZoneUiState,
    onAddZoneAtLocation: (Double, Double) -> Unit,
    onEditZone: (Zone) -> Unit
) {
    var centerLat by remember { mutableDoubleStateOf(state.zones.firstOrNull()?.latitude ?: 13.0827) }
    var centerLng by remember { mutableDoubleStateOf(state.zones.firstOrNull()?.longitude ?: 80.2707) }
    var zoomScale by remember { mutableFloatStateOf(0.8f) }
    var selectedZone by remember { mutableStateOf<Zone?>(null) }
    var lastTappedCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Pulsing circle animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarMapPulse")
    val pulseFactor by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseFactor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Multi-zone interactive map canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.zones) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val metersPerPx = 2.0f / zoomScale
                        val deltaXMeters = (tapOffset.x - centerX) * metersPerPx
                        val deltaYMeters = (centerY - tapOffset.y) * metersPerPx

                        val tapLat = centerLat + (deltaYMeters / 111000.0)
                        val tapLng = centerLng + (deltaXMeters / (111000.0 * cos(Math.toRadians(centerLat))))
                        lastTappedCoords = tapLat to tapLng

                        // Check if tapped inside any existing zone
                        val hit = state.zones.firstOrNull { zone ->
                            val distLatMeters = (zone.latitude - tapLat) * 111000.0
                            val distLngMeters = (zone.longitude - tapLng) * (111000.0 * cos(Math.toRadians(centerLat)))
                            val dist = kotlin.math.sqrt(distLatMeters * distLatMeters + distLngMeters * distLngMeters)
                            dist <= zone.radiusMeters
                        }
                        selectedZone = hit
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val metersPerPx = 2.0f / zoomScale
                        val deltaXMeters = dragAmount.x * metersPerPx
                        val deltaYMeters = -dragAmount.y * metersPerPx

                        centerLat -= deltaYMeters / 111000.0
                        centerLng -= deltaXMeters / (111000.0 * cos(Math.toRadians(centerLat)))
                    }
                }
        ) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            val metersPerPx = 2.0f / zoomScale

            // Draw background grid lines
            val gridSize = 60.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x1100E5FF), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += gridSize
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0x1100E5FF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += gridSize
            }

            // Draw all configured zones
            state.zones.forEach { zone ->
                val deltaLatMeters = (zone.latitude - centerLat) * 111000.0
                val deltaLngMeters = (zone.longitude - centerLng) * (111000.0 * cos(Math.toRadians(centerLat)))

                val zoneScreenX = canvasCenter.x + (deltaLngMeters / metersPerPx).toFloat()
                val zoneScreenY = canvasCenter.y - (deltaLatMeters / metersPerPx).toFloat()
                val zoneCenter = Offset(zoneScreenX, zoneScreenY)

                val radiusPx = (zone.radiusMeters / metersPerPx).toFloat()
                val isInside = zone.id in state.insideZones.map { it.id }
                val isSelected = selectedZone?.id == zone.id

                val zoneColor = when (zone.type) {
                    ZoneType.COLLEGE -> Color(0xFF3F51B5)
                    ZoneType.EXAM_HALL -> Color(0xFFFF5252)
                    ZoneType.LIBRARY -> NeonCyan
                    ZoneType.CLASSROOM -> NeonPurple
                    ZoneType.CANTEEN, ZoneType.BREAK_ZONE -> EmeraldGreen
                }

                // Fill circle
                drawCircle(
                    color = zoneColor.copy(alpha = if (isInside) 0.35f else 0.15f),
                    radius = radiusPx * if (isInside) pulseFactor else 1.0f,
                    center = zoneCenter
                )

                // Outline stroke
                drawCircle(
                    color = if (isSelected) Color.White else zoneColor,
                    radius = radiusPx,
                    center = zoneCenter,
                    style = Stroke(
                        width = if (isSelected) 3.dp.toPx() else 1.5.dp.toPx(),
                        pathEffect = if (zone.type == ZoneType.COLLEGE) PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) else null
                    )
                )

                // Center node pin
                drawCircle(color = zoneColor, radius = 6.dp.toPx(), center = zoneCenter)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = zoneCenter)
            }

            // Center crosshair (Current viewport center)
            drawCircle(color = Color(0x66FFFFFF), radius = 4.dp.toPx(), center = canvasCenter)
        }

        // Top HUD with Map Metrics
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xCC0A0D14)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("CAMPUS RADAR MAP", style = MaterialTheme.typography.labelMedium, color = NeonCyan, fontWeight = FontWeight.Bold)
                Text("${state.zones.size} Zones Active", style = MaterialTheme.typography.bodySmall, color = Color.White)
                if (state.activeZone != null) {
                    Text("Inside: ${state.activeZone.name}", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Map Control Floating Buttons
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(3.0f) },
                modifier = Modifier.size(44.dp),
                containerColor = Color(0xDD1A2035),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
            }
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.3f) },
                modifier = Modifier.size(44.dp),
                containerColor = Color(0xDD1A2035),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
            }
            FloatingActionButton(
                onClick = {
                    val first = state.zones.firstOrNull()
                    if (first != null) {
                        centerLat = first.latitude
                        centerLng = first.longitude
                    }
                },
                modifier = Modifier.size(44.dp),
                containerColor = Color(0xDD1A2035),
                contentColor = NeonCyan
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter")
            }
        }

        // Bottom Selected Zone Details Card or Tap-To-Add Prompt
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            AnimatedVisibility(visible = selectedZone != null) {
                selectedZone?.let { zone ->
                    val isInside = zone.id in state.insideZones.map { it.id }
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = if (isInside) EmeraldGreen else NeonCyan
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(zone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${zone.type.name} • Radius ${zone.radiusMeters.toInt()}m", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                                Text(
                                    "Action: ${zone.action?.name ?: "Campus Boundary"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onEditZone(zone) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NeonCyan)
                                }
                                IconButton(onClick = { selectedZone = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedZone == null && lastTappedCoords != null) {
                lastTappedCoords?.let { (tLat, tLng) ->
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = NeonPurple
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Map Point Selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${String.format("%.4f", tLat)}, ${String.format("%.4f", tLng)}", style = MaterialTheme.typography.bodySmall, color = NeonPurple)
                            }
                            Button(
                                onClick = {
                                    onAddZoneAtLocation(tLat, tLng)
                                    lastTappedCoords = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                            ) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Zone Here")
                            }
                        }
                    }
                }
            }
        }
    }
}
