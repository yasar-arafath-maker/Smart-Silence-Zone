package com.smartsilentzone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.ui.theme.DarkBackground
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveZoneRadarMap(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
    onCoordinatesChanged: (Double, Double) -> Unit,
    onRadiusChanged: (Float) -> Unit,
    onLocateMe: () -> Unit
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    // Pulsing animation for geofence active boundary
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweep"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBackground)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        // 1 pixel = ~ (zoomScale * 1.5) meters
                        val metersPerPx = 1.5f / zoomScale
                        val deltaXMeters = (tapOffset.x - centerX) * metersPerPx
                        val deltaYMeters = (centerY - tapOffset.y) * metersPerPx

                        // 1 deg latitude ~= 111,000 meters
                        val deltaLat = deltaYMeters / 111000.0
                        val deltaLng = deltaXMeters / (111000.0 * cos(Math.toRadians(latitude)))

                        onCoordinatesChanged(latitude + deltaLat, longitude + deltaLng)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val metersPerPx = 1.5f / zoomScale
                        val deltaXMeters = dragAmount.x * metersPerPx
                        val deltaYMeters = -dragAmount.y * metersPerPx

                        val deltaLat = deltaYMeters / 111000.0
                        val deltaLng = deltaXMeters / (111000.0 * cos(Math.toRadians(latitude)))

                        onCoordinatesChanged(latitude + deltaLat, longitude + deltaLng)
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxDimension = minOf(size.width, size.height)

            // Draw radar grid rings
            val ringRadii = listOf(0.2f, 0.4f, 0.6f, 0.8f)
            ringRadii.forEach { factor ->
                drawCircle(
                    color = Color(0x2200E5FF),
                    radius = (maxDimension / 2f) * factor,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // Radar Crosshairs
            drawLine(
                color = Color(0x3300E5FF),
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0x3300E5FF),
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Radar sweep beam
            val sweepRad = Math.toRadians(sweepAngle.toDouble())
            val sweepTarget = Offset(
                center.x + (maxDimension * 0.45f * cos(sweepRad)).toFloat(),
                center.y + (maxDimension * 0.45f * sin(sweepRad)).toFloat()
            )
            drawLine(
                brush = Brush.linearGradient(
                    listOf(NeonCyan.copy(alpha = 0.5f), Color.Transparent),
                    start = center,
                    end = sweepTarget
                ),
                start = center,
                end = sweepTarget,
                strokeWidth = 2.dp.toPx()
            )

            // Geofence Radius Boundary Circle
            val radiusInPx = (radiusMeters / 1.5f) * zoomScale
            drawCircle(
                color = NeonCyan.copy(alpha = pulseAlpha),
                radius = radiusInPx,
                center = center
            )
            drawCircle(
                color = NeonCyan,
                radius = radiusInPx,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Center Pin (Target Location)
            drawCircle(color = NeonPurple, radius = 9.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = center)
        }

        // Coordinate & Radius Readout HUD
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xAA0A0D14)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    "LAT: ${String.format("%.5f", latitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "LNG: ${String.format("%.5f", longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "RADIUS: ${radiusMeters.toInt()}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Controls (Zoom in, Zoom out, Locate Me)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(3.0f) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC1A2035))
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
            }
            IconButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.4f) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC1A2035))
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
            }
            IconButton(
                onClick = onLocateMe,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC1A2035))
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", tint = NeonCyan)
            }
        }

        Text(
            "Tap or drag on radar to set center",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0x99FFFFFF)
        )
    }
}
