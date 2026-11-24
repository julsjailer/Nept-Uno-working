package com.example.neptuno.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.neptuno.data.remote.CloudUiState
import com.example.neptuno.feature.bluetooth.BluetoothScreen
import com.example.neptuno.feature.bluetooth.BluetoothViewModel
import com.example.neptuno.feature.chat.ChatScreen
import com.example.neptuno.feature.chat.ChatViewModel
import com.example.neptuno.feature.cloud.CloudAccountSummary
import com.example.neptuno.feature.cloud.CloudAuthUser
import com.example.neptuno.feature.cloud.CloudSessionViewModel
import com.example.neptuno.feature.home.HomeScreen
import com.example.neptuno.feature.home.HomeViewModel
import com.example.neptuno.feature.memory.MemoryScreen
import com.example.neptuno.feature.memory.MemoryViewModel
import com.example.neptuno.feature.settings.PersonalizationScreen
import com.example.neptuno.feature.settings.SettingsScreen
import com.example.neptuno.feature.settings.SettingsViewModel
import com.example.neptuno.feature.tools.ToolsScreen

object NeptunoRoutes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val BLUETOOTH = "bluetooth"
    const val MEMORY = "memory"
    const val SETTINGS = "settings"
    const val PERSONALIZATION = "personalization"
    const val TOOLS = "tools"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(NeptunoRoutes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(NeptunoRoutes.CHAT, R.string.nav_chat, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    TopLevelDestination(NeptunoRoutes.BLUETOOTH, R.string.nav_bluetooth, Icons.Filled.Bluetooth, Icons.Outlined.Bluetooth),
    TopLevelDestination(NeptunoRoutes.MEMORY, R.string.nav_memory, Icons.Filled.Memory, Icons.Outlined.Memory),
    TopLevelDestination(NeptunoRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun NeptunoAppShell(
    container: NeptunoAppContainer,
    currentUser: CloudAuthUser?,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val cloudSessionViewModel: CloudSessionViewModel = viewModel(
        key = "neptuno_cloud_session",
        factory = CloudSessionViewModel.factory(
            initialUser = currentUser,
            preferencesRepository = container.preferencesRepository,
            memoryRepository = container.memoryRepository,
            conversationRepository = container.conversationRepository,
            networkMonitor = container.networkMonitor
        )
    )

    LaunchedEffect(currentUser) {
        cloudSessionViewModel.updateAuthUser(currentUser)
    }

    val homeViewModel: HomeViewModel = viewModel(
        key = "neptuno_home",
        factory = HomeViewModel.factory(
            networkMonitor = container.networkMonitor,
            bluetoothController = container.bluetoothController,
            preferencesRepository = container.preferencesRepository,
            memoryRepository = container.memoryRepository,
            conversationRepository = container.conversationRepository,
            aiProviderRegistry = container.aiProviderRegistry
        )
    )

    val chatViewModel: ChatViewModel = viewModel(
        key = "neptuno_chat",
        factory = ChatViewModel.factory(
            conversationRepository = container.conversationRepository,
            memoryRepository = container.memoryRepository,
            preferencesRepository = container.preferencesRepository,
            aiProviderRegistry = container.aiProviderRegistry,
            audioCoordinator = container.audioCoordinator,
            networkMonitor = container.networkMonitor,
            speechToTextEngine = container.speechToTextEngine,
            textToSpeechEngine = container.textToSpeechEngine,
            audioPlaybackEngine = container.audioPlaybackEngine,
            cloudSyncGateway = cloudSessionViewModel.cloudSyncGateway
        )
    )

    // Bluetooth autónomo: en Android 12+ sin este permiso Android no deja ver los auriculares; se pide una vez al abrir.
    val bluetoothContext = androidx.compose.ui.platform.LocalContext.current
    val bluetoothPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !com.example.neptuno.core.PermissionHelper.hasBluetoothConnectPermission(bluetoothContext)
        ) {
            bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    // "Hey Neptuno": el servicio de fondo pide escuchar; el chat dicta y envía solo, y avisa al terminar.
    LaunchedEffect(chatViewModel, container) {
        val bus = container.wakeActivationBus
        launch {
            bus.pending.collect { requestedAt ->
                if (requestedAt != null && bus.consume(maxAgeMillis = 45_000L)) chatViewModel.startVoiceFromWake()
            }
        }
        chatViewModel.voiceTurnEnded.collect { bus.notifyTurnEnded() }
    }

    val bluetoothViewModel: BluetoothViewModel = viewModel(
        key = "neptuno_bluetooth",
        factory = BluetoothViewModel.factory(
            context = context,
            bluetoothController = container.bluetoothController,
            audioRouteManager = container.audioRouteManager,
            preferencesRepository = container.preferencesRepository
        )
    )

    val memoryViewModel: MemoryViewModel = viewModel(
        key = "neptuno_memory",
        factory = MemoryViewModel.factory(
            memoryRepository = container.memoryRepository,
            conversationRepository = container.conversationRepository,
            preferencesRepository = container.preferencesRepository,
            cloudSyncGateway = cloudSessionViewModel.cloudSyncGateway
        )
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        key = "neptuno_settings",
        factory = SettingsViewModel.factory(
            preferencesRepository = container.preferencesRepository,
            aiProviderRegistry = container.aiProviderRegistry,
            textToSpeechEngine = container.textToSpeechEngine
        )
    )

    val cloudSummary by cloudSessionViewModel.accountSummary.collectAsStateWithLifecycle()
    val cloudMemoriesState by cloudSessionViewModel.cloudMemoriesState.collectAsStateWithLifecycle()
    val cloudConversationsState by cloudSessionViewModel.cloudConversationsState.collectAsStateWithLifecycle()
    val cloudMemoriesCount = (cloudMemoriesState as? CloudUiState.Success)?.data?.size
    val cloudConversationsCount = (cloudConversationsState as? CloudUiState.Success)?.data?.size
    val cloudError = if (cloudSummary.isAuthenticated) {
        (cloudMemoriesState as? CloudUiState.Error)?.message
            ?: (cloudConversationsState as? CloudUiState.Error)?.message
    } else {
        null
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                if (isTopLevel) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(navController, dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    NeptunoNavHost(
                        navController = navController,
                        container = container,
                        homeViewModel = homeViewModel,
                        chatViewModel = chatViewModel,
                        bluetoothViewModel = bluetoothViewModel,
                        memoryViewModel = memoryViewModel,
                        settingsViewModel = settingsViewModel,
                        cloudSummary = cloudSummary,
                        cloudMemoriesCount = cloudMemoriesCount,
                        cloudConversationsCount = cloudConversationsCount,
                        cloudError = cloudError,
                        onSyncNow = cloudSessionViewModel::syncProfileAndLocalState,
                        onOpenSignIn = onOpenSignIn,
                        onSignOut = onSignOut
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (isTopLevel) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopLevel(navController, dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_bar_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NeptunoNavHost(
                        navController = navController,
                        container = container,
                        homeViewModel = homeViewModel,
                        chatViewModel = chatViewModel,
                        bluetoothViewModel = bluetoothViewModel,
                        memoryViewModel = memoryViewModel,
                        settingsViewModel = settingsViewModel,
                        cloudSummary = cloudSummary,
                        cloudMemoriesCount = cloudMemoriesCount,
                        cloudConversationsCount = cloudConversationsCount,
                        cloudError = cloudError,
                        onSyncNow = cloudSessionViewModel::syncProfileAndLocalState,
                        onOpenSignIn = onOpenSignIn,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}

@Composable
private fun NeptunoNavHost(
    navController: NavHostController,
    container: NeptunoAppContainer,
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    bluetoothViewModel: BluetoothViewModel,
    memoryViewModel: MemoryViewModel,
    settingsViewModel: SettingsViewModel,
    cloudSummary: CloudAccountSummary,
    cloudMemoriesCount: Int?,
    cloudConversationsCount: Int?,
    cloudError: String?,
    onSyncNow: () -> Unit,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = NeptunoRoutes.HOME
    ) {
        composable(NeptunoRoutes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                cloudSummary = cloudSummary,
                onStartConversation = { navigateToTopLevel(navController, NeptunoRoutes.CHAT) },
                onOpenBluetooth = { navigateToTopLevel(navController, NeptunoRoutes.BLUETOOTH) },
                onOpenSettings = { navigateToTopLevel(navController, NeptunoRoutes.SETTINGS) },
                onOpenPersonalization = { navController.navigate(NeptunoRoutes.PERSONALIZATION) },
                onOpenTools = { navController.navigate(NeptunoRoutes.TOOLS) },
                onOpenMemory = { navigateToTopLevel(navController, NeptunoRoutes.MEMORY) },
                onOpenSignIn = onOpenSignIn
            )
        }
        composable(NeptunoRoutes.CHAT) {
            ChatScreen(viewModel = chatViewModel)
        }
        composable(NeptunoRoutes.BLUETOOTH) {
            BluetoothScreen(viewModel = bluetoothViewModel)
        }
        composable(NeptunoRoutes.MEMORY) {
            MemoryScreen(
                viewModel = memoryViewModel,
                onOpenConversation = { conversationId ->
                    chatViewModel.selectConversation(conversationId)
                    navigateToTopLevel(navController, NeptunoRoutes.CHAT)
                }
            )
        }
        composable(NeptunoRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                wakeWordEngine = container.wakeWordEngine,
                onOpenPersonalization = { navController.navigate(NeptunoRoutes.PERSONALIZATION) },
                onOpenMemory = { navigateToTopLevel(navController, NeptunoRoutes.MEMORY) },
                cloudSummary = cloudSummary,
                cloudMemoriesCount = cloudMemoriesCount,
                cloudConversationsCount = cloudConversationsCount,
                cloudError = cloudError,
                onSyncNow = onSyncNow,
                onSignIn = onOpenSignIn,
                onSignOut = onSignOut
            )
        }
        composable(NeptunoRoutes.PERSONALIZATION) {
            PersonalizationScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NeptunoRoutes.TOOLS) {
            ToolsScreen(
                registry = container.toolRegistry,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private fun navigateToTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
