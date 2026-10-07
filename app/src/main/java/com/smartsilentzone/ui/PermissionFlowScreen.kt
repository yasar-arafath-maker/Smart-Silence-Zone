package com.smartsilentzone.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class StepInfo(
    val step: NextPermissionStep,
    val title: String,
    val why: String,
    val action: String
)

private val steps = listOf(
    StepInfo(
        NextPermissionStep.FINE_LOCATION, "Precise location",
        "Smart Silent Zone compares your position with your college zones to know when you enter or leave one. " +
            "Location is processed on this device only. Please choose \"Precise\", not \"Approximate\".",
        "Allow location"
    ),
    StepInfo(
        NextPermissionStep.BACKGROUND_LOCATION, "Location all the time",
        "Your phone is usually in your pocket, so zone detection must work while the app is closed. " +
            "Android will open a settings page: choose \"Allow all the time\", then come back.",
        "Continue to settings"
    ),
    StepInfo(
        NextPermissionStep.NOTIFICATIONS, "Notifications",
        "Lets the app tell you when it changes your ringer, so a change is never a surprise.",
        "Allow notifications"
    ),
    StepInfo(
        NextPermissionStep.POLICY_ACCESS, "Do Not Disturb access",
        "Android only lets apps change silent / Do Not Disturb after you grant this. " +
            "On the next screen, find Smart Silent Zone and switch it on.",
        "Open Do Not Disturb settings"
    ),
    StepInfo(
        NextPermissionStep.ENABLE_GPS, "Location services",
        "Location is switched off for the whole phone, so no zone can be detected. Turn it on to continue.",
        "Open location settings"
    )
)

private fun PermissionState.isDone(step: NextPermissionStep) = when (step) {
    NextPermissionStep.FINE_LOCATION -> fineLocation
    NextPermissionStep.BACKGROUND_LOCATION -> backgroundLocation
    NextPermissionStep.NOTIFICATIONS -> notifications
    NextPermissionStep.POLICY_ACCESS -> policyAccess
    NextPermissionStep.ENABLE_GPS -> gpsEnabled
    NextPermissionStep.NONE -> true
}

private fun runtimePermissionFor(step: NextPermissionStep): String? = when (step) {
    NextPermissionStep.FINE_LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
    NextPermissionStep.BACKGROUND_LOCATION -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
    NextPermissionStep.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
    else -> null
}

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some OEM builds lack a specific settings screen: fall back to the app's own page.
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Explain -> request -> check -> if denied, explain again + offer app settings.
 * One step at a time, in the order Android requires. Calls [onFinished] when automation is ready.
 */
@Composable
fun PermissionFlowScreen(
    onFinished: () -> Unit,
    vm: ZoneEvaluationViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var requestedStep by remember { mutableStateOf<NextPermissionStep?>(null) }
    var deniedSteps by remember { mutableStateOf(emptySet<NextPermissionStep>()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val step = requestedStep
        if (!granted && step != null) deniedSteps = deniedSteps + step
        vm.refreshSystemState()
    }

    // Re-read state whenever the user returns from a system settings page.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.OpenSettings -> safeStart(context, event.intent)
                is UiEvent.RequestPermission -> {
                    val permission = runtimePermissionFor(event.step) ?: return@collect
                    requestedStep = event.step
                    permissionLauncher.launch(permission)
                }
            }
        }
    }

    val perms = state.permissions
    LaunchedEffect(state.nextStep, perms) {
        if (perms != null && state.nextStep == NextPermissionStep.NONE) onFinished()
    }

    val current = steps.firstOrNull { it.step == state.nextStep }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Set up Smart Silent Zone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "We only ask for what the app needs, one step at a time.",
            style = MaterialTheme.typography.bodyLarge
        )

        // Checklist: icon AND text, so status is never conveyed by color alone.
        steps.forEach { info ->
            val done = perms?.isDone(info.step) == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(info.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (done) "Granted" else "Needed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (current != null) {
            val denied = current.step in deniedSteps
            com.smartsilentzone.ui.components.GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = if (denied) MaterialTheme.colorScheme.error else com.smartsilentzone.ui.theme.NeonCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Why we need: ${current.title}", style = MaterialTheme.typography.titleMedium)
                    Text(current.why, style = MaterialTheme.typography.bodyMedium)
                    if (denied) {
                        Text(
                            "This permission was declined. Without it, automatic silent mode cannot work. " +
                                "You can try again, or turn it on manually in the app's settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { vm.onGrantClicked() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(if (denied) "Try again" else current.action) }

                    if (denied) {
                        OutlinedButton(
                            onClick = {
                                safeStart(
                                    context,
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Open app settings") }
                    }
                }
            }
        }
    }
}
