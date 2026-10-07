package com.smartsilentzone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.ui.components.SoundTesterBottomSheet
import com.smartsilentzone.ui.map.CampusMapExplorerScreen
import com.smartsilentzone.ui.more.MoreHubScreen
import com.smartsilentzone.ui.theme.NeonCyan
import com.smartsilentzone.ui.timetable.TimetableScreen

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    STATUS("Status", Icons.Default.Dashboard),
    MAP("Radar Map", Icons.Default.Explore),
    ZONES("Zones", Icons.Default.Place),
    TIMETABLE("Timetable", Icons.Default.CalendarMonth),
    MORE("More", Icons.Default.Widgets)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    vm: ZoneEvaluationViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(AppTab.STATUS) }
    var editingZone by remember { mutableStateOf<Zone?>(null) }
    var showZoneEditor by remember { mutableStateOf(false) }
    var showSoundTester by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (event is UiEvent.ShowToast) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Smart Silent Zone", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showSoundTester = true }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Test Sound Mode", tint = NeonCyan)
                    }
                    IconButton(onClick = { vm.forceEvaluate() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Geofences", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0A0D14),
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == AppTab.ZONES || currentTab == AppTab.MAP) {
                FloatingActionButton(
                    onClick = {
                        editingZone = null
                        showZoneEditor = true
                    },
                    containerColor = NeonCyan,
                    contentColor = Color(0xFF0A0D14)
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Zone")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                AppTab.STATUS -> {
                    val locationState by vm.campusLocationState.collectAsStateWithLifecycle()
                    com.smartsilentzone.ui.dashboard.CinematicCampusDashboard(
                        stateMachine = vm.centralStateMachine,
                        acousticEngine = vm.acousticEngine,
                        locationState = locationState
                    )
                }
                AppTab.MAP -> CampusMapExplorerScreen(
                    state = state,
                    onAddZoneAtLocation = { lat, lng ->
                        editingZone = Zone(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "New Campus Block",
                            type = com.smartsilentzone.domain.ZoneType.CLASSROOM,
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = 60f
                        )
                        showZoneEditor = true
                    },
                    onEditZone = { zone ->
                        editingZone = zone
                        showZoneEditor = true
                    }
                )
                AppTab.ZONES -> ZoneListScreen(
                    vm = vm,
                    state = state,
                    onOpenAddZone = {
                        editingZone = null
                        showZoneEditor = true
                    },
                    onEditZone = { zone ->
                        editingZone = zone
                        showZoneEditor = true
                    }
                )
                AppTab.TIMETABLE -> TimetableScreen()
                AppTab.MORE -> MoreHubScreen(
                    vm = vm,
                    ringerManager = vm.ringer
                )
            }
        }

        if (showZoneEditor) {
            ZoneEditorDialog(
                initialZone = editingZone,
                onDismiss = { showZoneEditor = false },
                onSave = { zone ->
                    vm.saveZone(zone)
                    showZoneEditor = false
                },
                onFetchCurrentLocation = { vm.getCurrentLocation() }
            )
        }

        if (showSoundTester) {
            SoundTesterBottomSheet(
                ringerManager = vm.ringer,
                onDismiss = { showSoundTester = false }
            )
        }
    }
}
