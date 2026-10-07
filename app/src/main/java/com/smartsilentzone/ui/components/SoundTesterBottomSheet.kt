package com.smartsilentzone.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.ringer.RingerModeManager
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.theme.NeonPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundTesterBottomSheet(
    ringerManager: RingerModeManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentAction by remember { mutableStateOf(ringerManager.currentAction()) }
    var testFeedback by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Interactive Sound Tester",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Active: ${currentAction.name}") }
                )
            }

            Text(
                "Test how Smart Silent Zone controls your phone's hardware ringer and DND filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (testFeedback != null) {
                Text(
                    testFeedback ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            ringerManager.apply(RingerAction.NORMAL)
                            currentAction = ringerManager.currentAction()
                            testFeedback = "Switched to NORMAL ringer mode"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Normal")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            ringerManager.apply(RingerAction.VIBRATE)
                            currentAction = ringerManager.currentAction()
                            // Trigger physical haptic vibration for immediate interactive feel
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(400)
                            }
                            testFeedback = "Switched to VIBRATE mode (Haptic fired)"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vibrate")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            ringerManager.apply(RingerAction.SILENT)
                            currentAction = ringerManager.currentAction()
                            testFeedback = "Switched to SILENT mode"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Silent")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            ringerManager.apply(RingerAction.DND)
                            currentAction = ringerManager.currentAction()
                            testFeedback = "Switched to Do Not Disturb (Priority Mode)"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DoNotDisturbOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("DND")
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close Tester")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
