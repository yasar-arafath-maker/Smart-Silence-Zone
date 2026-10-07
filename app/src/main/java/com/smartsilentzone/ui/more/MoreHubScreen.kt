package com.smartsilentzone.ui.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.ringer.RingerModeManager
import com.smartsilentzone.ui.PermissionFlowScreen
import com.smartsilentzone.ui.ZoneEvaluationViewModel
import com.smartsilentzone.ui.battery.BatteryOptimizationScreen
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.components.SoundTesterBottomSheet
import com.smartsilentzone.ui.contacts.ContactsScreen
import com.smartsilentzone.ui.history.HistoryScreen
import com.smartsilentzone.ui.theme.EmeraldGreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple

enum class SubScreen {
    HUB, VIP_CONTACTS, HISTORY, BATTERY_SHIELD, PERMISSIONS, WIFI_SURVEY
}

@Composable
fun MoreHubScreen(
    vm: ZoneEvaluationViewModel,
    ringerManager: RingerModeManager
) {
    var currentSubScreen by remember { mutableStateOf(SubScreen.HUB) }
    var showSoundTester by remember { mutableStateOf(false) }

    AnimatedContent(targetState = currentSubScreen, label = "MoreScreenTransition") { subScreen ->
        when (subScreen) {
            SubScreen.HUB -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Advanced Controls & Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HubNavigationCard(
                        title = "Wi-Fi Floor Survey Mode",
                        subtitle = "Scan & map connected AP BSSID to classroom floor & silent policy",
                        icon = Icons.Default.Wifi,
                        glowColor = NeonCyan,
                        onClick = { currentSubScreen = SubScreen.WIFI_SURVEY }
                    )

                    HubNavigationCard(
                        title = "VIP Contacts Whitelist",
                        subtitle = "Allow parents & emergency calls to bypass DND mode",
                        icon = Icons.Default.ContactPhone,
                        glowColor = NeonCyan,
                        onClick = { currentSubScreen = SubScreen.VIP_CONTACTS }
                    )

                    HubNavigationCard(
                        title = "Geofence Activity History",
                        subtitle = "View timestamped timeline of 30s dwell & exit events",
                        icon = Icons.Default.History,
                        glowColor = NeonPurple,
                        onClick = { currentSubScreen = SubScreen.HISTORY }
                    )

                    HubNavigationCard(
                        title = "Battery & OEM Background Shield",
                        subtitle = "Xiaomi, Samsung, OnePlus auto-start & battery guides",
                        icon = Icons.Default.BatteryChargingFull,
                        glowColor = EmeraldGreen,
                        onClick = { currentSubScreen = SubScreen.BATTERY_SHIELD }
                    )

                    HubNavigationCard(
                        title = "Security & Permission Flow",
                        subtitle = "Inspect and grant precise & background location access",
                        icon = Icons.Default.Security,
                        glowColor = MaterialTheme.colorScheme.primary,
                        onClick = { currentSubScreen = SubScreen.PERMISSIONS }
                    )

                    HubNavigationCard(
                        title = "Hardware Sound Mode Tester",
                        subtitle = "Test Silent, Vibrate (with haptics), Normal, and DND",
                        icon = Icons.Default.VolumeUp,
                        glowColor = EmeraldGreen,
                        onClick = { showSoundTester = true }
                    )
                }
            }
            SubScreen.WIFI_SURVEY -> {
                com.smartsilentzone.ui.survey.WifiSurveyScreen(
                    wifiFloorDetector = vm.wifiFloorDetector,
                    wifiApDao = vm.wifiApDao,
                    onBack = { currentSubScreen = SubScreen.HUB }
                )
            }
            SubScreen.VIP_CONTACTS -> SubScreenWrapper(title = "VIP Contacts", onBack = { currentSubScreen = SubScreen.HUB }) {
                ContactsScreen()
            }
            SubScreen.HISTORY -> SubScreenWrapper(title = "Activity History", onBack = { currentSubScreen = SubScreen.HUB }) {
                HistoryScreen()
            }
            SubScreen.BATTERY_SHIELD -> SubScreenWrapper(title = "Background Protection", onBack = { currentSubScreen = SubScreen.HUB }) {
                BatteryOptimizationScreen()
            }
            SubScreen.PERMISSIONS -> SubScreenWrapper(title = "Permission Flow", onBack = { currentSubScreen = SubScreen.HUB }) {
                PermissionFlowScreen(onFinished = { currentSubScreen = SubScreen.HUB }, vm = vm)
            }
        }
    }

    if (showSoundTester) {
        SoundTesterBottomSheet(
            ringerManager = ringerManager,
            onDismiss = { showSoundTester = false }
        )
    }
}

@Composable
private fun HubNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    glowColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        glowColor = glowColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = glowColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = glowColor, modifier = Modifier.size(24.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = glowColor)
        }
    }
}

@Composable
private fun SubScreenWrapper(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        content()
    }
}
