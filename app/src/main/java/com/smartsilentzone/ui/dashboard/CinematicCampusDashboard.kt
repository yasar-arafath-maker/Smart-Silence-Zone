package com.smartsilentzone.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsilentzone.domain.ActiveSystemPriority
import com.smartsilentzone.domain.CentralActionStateMachine
import com.smartsilentzone.domain.CentralSystemState
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.geofence.LiveCampusLocationState
import com.smartsilentzone.sensor.AcousticCrowdEngine
import com.smartsilentzone.sensor.AcousticState
import com.smartsilentzone.sensor.CrowdDensityLevel
import com.smartsilentzone.ui.theme.*

@Composable
fun CinematicCampusDashboard(
    stateMachine: CentralActionStateMachine,
    acousticEngine: AcousticCrowdEngine,
    locationState: LiveCampusLocationState,
    modifier: Modifier = Modifier
) {
    val systemState by stateMachine.systemState.collectAsState()
    val acousticState by acousticEngine.acousticState.collectAsState()
    val haptic = LocalHapticFeedback.current

    val scrollState = rememberScrollState()

    // Dynamic accent color based on active mode
    val themeColor by animateColorAsState(
        targetValue = when {
            systemState.isEmergencyActive -> NeonRed
            systemState.effectiveRingerAction == RingerAction.SILENT -> NeonPurple
            systemState.effectiveRingerAction == RingerAction.VIBRATE -> NeonCyan
            else -> EmeraldGreen
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ThemeColorAnim"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterCenter,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TOP CINEMATIC RADAR PULSE WIDGET
        CinematicRadarPulseWidget(
            locationState = locationState,
            themeColor = themeColor,
            isInsideZone = locationState.isInsideAnyZone
        )

        // 2. ACTIVE RINGER STATE HERO BANNER
        CinematicHeroStateBanner(
            systemState = systemState,
            themeColor = themeColor,
            onManualToggle = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                stateMachine.setManualOverride(!systemState.isManualOverrideActive)
            }
        )

        // 3. PREDICTIVE APPROACHING ZONE BANNER (if approaching)
        val approachingEvent = locationState.predictiveEvents.firstOrNull { it.isApproaching && (it.etaSeconds ?: 10f) < 8f }
        AnimatedVisibility(
            visible = approachingEvent != null && !locationState.isInsideAnyZone,
            enter = fadeIn() + androidx.compose.animation.expandVertically(),
            exit = fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            approachingEvent?.let {
                PredictiveApproachCard(event = it)
            }
        }

        // 4. LIVE DECIBEL WAVEFORM & ACOUSTIC CROWD VISUALIZER
        CinematicAudioWaveformCard(
            acousticState = acousticState,
            themeColor = themeColor,
            onToggleMic = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (acousticState.isStreaming) {
                    acousticEngine.stopLiveAcousticStream()
                } else {
                    acousticEngine.startLiveAcousticStream()
                }
            }
        )

        // 5. LIVE TELEMETRY MATRIX HUD
        LiveTelemetryHudGrid(
            locationState = locationState,
            systemState = systemState
        )
    }
}

/**
 * Animated Canvas Radar Pulse: ripples radiate dynamically based on GPS accuracy and zone state.
 */
@Composable
fun CinematicRadarPulseWidget(
    locationState: LiveCampusLocationState,
    themeColor: Color,
    isInsideZone: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")

    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )

    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.height * 0.44f

            // Radar circle 1
            drawCircle(
                color = themeColor.copy(alpha = pulseAlpha1 * 0.7f),
                radius = maxRadius * pulseScale1,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Radar circle 2
            drawCircle(
                color = themeColor.copy(alpha = pulseAlpha2 * 0.7f),
                radius = maxRadius * pulseScale2,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner boundary circle
            drawCircle(
                color = themeColor.copy(alpha = 0.15f),
                radius = maxRadius * 0.55f,
                center = center
            )

            // Center location pin pulse
            drawCircle(
                color = themeColor,
                radius = 12.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = center
            )
        }

        // Radar Center Status Pill
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 110.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkCardBackground.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(themeColor)
                    )
                    Text(
                        text = if (isInsideZone) "INSIDE GEOFENCE" else "SCANNING CAMPUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Animated Hero State Banner displaying active audio policy and trigger cause.
 */
@Composable
fun CinematicHeroStateBanner(
    systemState: CentralSystemState,
    themeColor: Color,
    onManualToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            .shadow(12.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = DarkCardBackground,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, themeColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(themeColor.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE DEVICE PROFILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.2.sp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = themeColor.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColor)
                ) {
                    Text(
                        text = systemState.effectiveRingerAction.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = themeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = systemState.activeZone?.name ?: systemState.wifiFloorZoneName ?: "Campus Free Zone",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = systemState.reason,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onManualToggle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (systemState.isManualOverrideActive) NeonAmber else NeonCyan
                    )
                ) {
                    Icon(
                        imageVector = if (systemState.isManualOverrideActive) Icons.Default.LockReset else Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (systemState.isManualOverrideActive) "Disable Override" else "Manual Override",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Predictive Approach Notification Card: visual vector notification before entering boundary.
 */
@Composable
fun PredictiveApproachCard(
    event: com.smartsilentzone.geofence.PredictiveZoneEvent
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NeonAmber.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = NeonAmber,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Approaching ${event.zone.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "Distance: ${event.distanceToBoundaryMeters.toInt()}m • Anticipated in ~${event.etaSeconds?.toInt() ?: 5}s",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Bouncing Decibel Waveform Visualizer: 8 spring-physics animated bars.
 */
@Composable
fun CinematicAudioWaveformCard(
    acousticState: AcousticState,
    themeColor: Color,
    onToggleMic: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DarkCardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan)
                    Column {
                        Text(
                            text = "LIVE CROWD DECIBEL SENSOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${acousticState.currentDecibels.toInt()} dB SPL • ${acousticState.crowdLevel.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onToggleMic,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (acousticState.isStreaming) NeonRed.copy(alpha = 0.25f) else NeonCyan.copy(alpha = 0.2f),
                        contentColor = if (acousticState.isStreaming) NeonRed else NeonCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (acousticState.isStreaming) "Stop Mic" else "Live Stream",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 8 Reactive Bouncing Equalizer Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                acousticState.waveformBars.forEachIndexed { index, rawHeight ->
                    val animatedHeight by animateFloatAsState(
                        targetValue = if (acousticState.isStreaming) rawHeight.coerceIn(0.08f, 1.0f) else 0.08f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "BarHeight_$index"
                    )

                    val barColor = when {
                        acousticState.currentDecibels > 70 -> NeonRed
                        acousticState.currentDecibels > 50 -> NeonAmber
                        else -> EmeraldGreen
                    }

                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(fraction = animatedHeight)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(barColor, barColor.copy(alpha = 0.3f))
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * 4-Tile Telemetry Grid showing live GPS speed, accuracy, boundary, and active priority.
 */
@Composable
fun LiveTelemetryHudGrid(
    locationState: LiveCampusLocationState,
    systemState: CentralSystemState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile(
                title = "GPS ACCURACY",
                value = "±${locationState.accuracyMeters.toInt()}m",
                icon = Icons.Default.GpsFixed,
                accentColor = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            TelemetryTile(
                title = "WALKING SPEED",
                value = "${(locationState.speedMps * 3.6f).toInt()} km/h",
                icon = Icons.Default.Speed,
                accentColor = EmeraldGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile(
                title = "NEAREST BOUNDARY",
                value = if (locationState.distanceToNearestBoundaryMeters < 1000f) {
                    "${abs(locationState.distanceToNearestBoundaryMeters).toInt()}m"
                } else "Campus Edge",
                icon = Icons.Default.Place,
                accentColor = NeonPurple,
                modifier = Modifier.weight(1f)
            )
            TelemetryTile(
                title = "SYSTEM PRIORITY",
                value = systemState.priority.name.substringAfter("PRIORITY_"),
                icon = Icons.Default.Shield,
                accentColor = NeonAmber,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TelemetryTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = DarkCardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}
