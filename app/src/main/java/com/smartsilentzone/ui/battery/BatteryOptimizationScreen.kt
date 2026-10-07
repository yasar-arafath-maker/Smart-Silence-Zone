package com.smartsilentzone.ui.battery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.EmeraldGreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple

@Composable
fun BatteryOptimizationScreen() {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    var expandedBrand by remember { mutableStateOf<String?>("Xiaomi") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = if (isIgnoringBattery) EmeraldGreen else NeonCyan
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
                        imageVector = if (isIgnoringBattery) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnoringBattery) EmeraldGreen else NeonCyan,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "Background Protection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isIgnoringBattery) "Unrestricted Background Running" else "Optimized (Risk of background kill)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AssistChip(
                    onClick = {},
                    label = { Text(if (isIgnoringBattery) "PROTECTED" else "UNPROTECTED") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (isIgnoringBattery) EmeraldGreen else MaterialTheme.colorScheme.error
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            if (!isIgnoringBattery) {
                Button(
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = MaterialTheme.colorScheme.background)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exempt From Battery Optimization", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "Smart Silent Zone is exempted from OS sleep killing. Background 30s geofences will fire promptly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldGreen
                )
            }
        }

        // Efficiency Info Card
        GlassmorphicCard(modifier = Modifier.fillMaxWidth(), glowColor = NeonPurple) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Eco, contentDescription = null, tint = EmeraldGreen)
                Text("Zero Battery Drain Guarantee", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Our geofencing uses native hardware cellular tower & Wi-Fi trilateration through Google Play Services instead of constant GPS polling. " +
                "Estimated battery consumption is less than 0.8% over a full 24-hour day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text("OEM Manufacturer Auto-Start Guides", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val oemGuides = listOf(
            "Xiaomi / Redmi / POCO" to "1. Go to Settings > Apps > Manage Apps > Smart Silent Zone.\n2. Enable 'Autostart'.\n3. Under Battery Saver, select 'No restrictions'.",
            "Samsung Galaxy (OneUI)" to "1. Go to Settings > Battery and Device Care > Battery.\n2. Tap Background usage limits > Never sleeping apps.\n3. Add Smart Silent Zone to the list.",
            "OnePlus / Realme / Oppo" to "1. Go to Settings > Battery > App Battery Management.\n2. Find Smart Silent Zone and enable 'Allow background activity' and 'Allow auto-launch'.",
            "Vivo / iQOO (Funtouch OS)" to "1. Go to Settings > Battery > High background power consumption.\n2. Turn on toggle for Smart Silent Zone."
        )

        oemGuides.forEach { (brand, instructions) ->
            val isExpanded = expandedBrand == brand
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth().clickable { expandedBrand = if (isExpanded) null else brand }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(brand, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            instructions,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
