package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.LudoHapticsManager
import com.example.audio.SoundManager
import com.example.data.MatchHistoryRepository
import com.example.data.SavedGameRepository
import com.example.data.SettingsRepository
import com.example.engine.GameModeId
import com.example.online.AuthManager
import com.example.online.GuestProfileRepository
import com.example.online.OnlineProfileAndLeaderboardRepository
import com.example.online.OnlineRoomRepository
import com.example.online.RoomStatus
import com.example.online.isDeviceOnline
import com.example.ui.game.GameViewModel
import com.example.ui.online.LeaderboardViewModel
import com.example.ui.online.OnlineAuthViewModel
import com.example.ui.online.OnlineGameViewModel
import com.example.ui.online.OnlineHubViewModel
import com.example.ui.online.StatsHistoryViewModel
import com.example.ui.screens.GameBoardScreen
import com.example.ui.screens.GameSetupScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.ModeSelectionScreen
import com.example.ui.screens.OnlineAuthScreen
import com.example.ui.screens.OnlineHubScreen
import com.example.ui.screens.OnlineLobbyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatsHistoryScreen
import com.example.ui.screens.TutorialScreen
import com.example.ui.screens.WinnerScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LudoForMTheme
import kotlinx.coroutines.launch

object LudoRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val MODES = "modes"
    const val SETUP = "setup"
    const val GAME = "game"
    const val WINNER = "winner"
    const val SETTINGS = "settings"
    const val TUTORIAL = "tutorial"
    const val TUTORIAL_FIRST_LAUNCH = "tutorial_first_launch"
    const val STATS = "stats"
    const val ONLINE_AUTH = "online_auth"
    const val ONLINE_HUB = "online_hub"
    const val ONLINE_LOBBY = "online_lobby/{roomCode}"
    const val ONLINE_GAME = "online_game/{roomCode}"
    const val LEADERBOARD = "leaderboard"

    fun onlineLobby(roomCode: String) = "online_lobby/${roomCode.trim().uppercase()}"
    fun onlineGame(roomCode: String) = "online_game/${roomCode.trim().uppercase()}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LudoForMTheme {
                val activity = this
                val context = LocalContext.current.applicationContext
                val coroutineScope = rememberCoroutineScope()

                val settingsRepository = remember { SettingsRepository(context) }
                val savedGameRepository = remember { SavedGameRepository(context) }
                val matchHistoryRepository = remember { MatchHistoryRepository(context) }
                val soundManager = remember { SoundManager(context) }
                val hapticsManager = remember { LudoHapticsManager(context) }

                val authManager = remember { AuthManager(context) }
                val guestRepo = remember { GuestProfileRepository(context) }
                val profileRepo = remember { OnlineProfileAndLeaderboardRepository(context) }
                val roomRepo = remember { OnlineRoomRepository(context) }

                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(settingsRepository)
                )
                val gameViewModel: GameViewModel = viewModel(
                    factory = GameViewModel.Factory(soundManager, hapticsManager, savedGameRepository)
                )
                val authViewModel: OnlineAuthViewModel = viewModel(
                    factory = OnlineAuthViewModel.Factory(authManager, guestRepo, profileRepo)
                )

                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                val gameUiState by gameViewModel.uiState.collectAsStateWithLifecycle()
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                val savedGameSummary by savedGameRepository.savedGameSummaryFlow
                    .collectAsStateWithLifecycle(initialValue = null)
                var selectedMode by remember { mutableStateOf(GameModeId.CLASSIC) }

                LaunchedEffect(Unit) {
                    authViewModel.attemptAutoSignInOnce(activity)
                }

                LaunchedEffect(settings) {
                    soundManager.updateSettings(
                        soundEffects = settings.soundEffectsEnabled,
                        soundEffectsVolume = settings.soundEffectsVolume
                    )
                    hapticsManager.updateEnabled(settings.vibrationEnabled)
                }

                var attemptedOnlineAutoReconnect by rememberSaveable { mutableStateOf(false) }
                val navController = rememberNavController()

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, navController) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> soundManager.setForeground(true)
                            Lifecycle.Event.ON_PAUSE -> {
                                soundManager.setForeground(false)
                                if (navController.currentBackStackEntry?.destination?.route == LudoRoutes.GAME) {
                                    gameViewModel.saveCurrentProgressIfActive()
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        soundManager.release()
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = LudoRoutes.SPLASH,
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(240)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(240),
                            targetOffset = { it / 4 }
                        ) + fadeOut(animationSpec = tween(180))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(240),
                            initialOffset = { it / 4 }
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(240)
                        ) + fadeOut(animationSpec = tween(180))
                    }
                ) {
                    composable(
                        route = LudoRoutes.SPLASH,
                        enterTransition = { fadeIn(tween(200)) },
                        exitTransition = { fadeOut(tween(240)) }
                    ) {
                        SplashScreen(
                            onSplashFinished = {
                                val nextRoute = if (settings.isLoaded && !settings.hasSeenTutorial) {
                                    LudoRoutes.TUTORIAL_FIRST_LAUNCH
                                } else {
                                    LudoRoutes.HOME
                                }
                                navController.navigate(nextRoute) {
                                    popUpTo(LudoRoutes.SPLASH) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(LudoRoutes.TUTORIAL_FIRST_LAUNCH) {
                        TutorialScreen(
                            isFirstLaunch = true,
                            onCompleteOrSkip = {
                                settingsViewModel.markTutorialSeen()
                                navController.navigate(LudoRoutes.HOME) {
                                    popUpTo(LudoRoutes.TUTORIAL_FIRST_LAUNCH) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(LudoRoutes.HOME) {
                        val signedInUid = authState.firebaseUser?.uid
                        LaunchedEffect(signedInUid, settings.isLoaded) {
                            if (!attemptedOnlineAutoReconnect &&
                                settings.isLoaded &&
                                signedInUid != null &&
                                isDeviceOnline(context)
                            ) {
                                attemptedOnlineAutoReconnect = true
                                val runningRoom = roomRepo.findRunningRoomForUser(
                                    uid = signedInUid,
                                    preferredRoomCode = settings.activeOnlineRoomCode
                                )
                                if (runningRoom != null) {
                                    settingsViewModel.setActiveOnlineRoomCode(runningRoom.roomCode)
                                    navController.navigate(LudoRoutes.onlineGame(runningRoom.roomCode))
                                } else if (settings.activeOnlineRoomCode.isNotBlank()) {
                                    settingsViewModel.setActiveOnlineRoomCode("")
                                }
                            }
                        }

                        HomeScreen(
                            authState = authState,
                            savedGameSummary = savedGameSummary,
                            onResumeSavedGame = {
                                coroutineScope.launch {
                                    val saved = savedGameRepository.loadSavedGame()
                                    if (saved != null && !saved.isMatchFinished) {
                                        gameViewModel.resumeSavedMatch(saved)
                                        navController.navigate(LudoRoutes.GAME)
                                    } else {
                                        savedGameRepository.clearSavedGame()
                                    }
                                }
                            },
                            onDiscardSavedGame = {
                                coroutineScope.launch { savedGameRepository.clearSavedGame() }
                            },
                            onPlayClick = { navController.navigate(LudoRoutes.MODES) },
                            onPlayOnlineClick = { navController.navigate(LudoRoutes.ONLINE_HUB) },
                            onLeaderboardClick = { navController.navigate(LudoRoutes.LEADERBOARD) },
                            onStatsClick = { navController.navigate(LudoRoutes.STATS) },
                            onTutorialClick = { navController.navigate(LudoRoutes.TUTORIAL) },
                            onAccountClick = {
                                if (authState.isSignedInWithGoogle) {
                                    navController.navigate(LudoRoutes.STATS)
                                } else {
                                    navController.navigate(LudoRoutes.ONLINE_AUTH)
                                }
                            },
                            onSettingsClick = { navController.navigate(LudoRoutes.SETTINGS) }
                        )
                    }

                    composable(LudoRoutes.TUTORIAL) {
                        TutorialScreen(
                            isFirstLaunch = false,
                            onCompleteOrSkip = {
                                settingsViewModel.markTutorialSeen()
                                navController.popBackStack()
                            },
                            onPlayNowClick = {
                                settingsViewModel.markTutorialSeen()
                                navController.navigate(LudoRoutes.MODES) {
                                    popUpTo(LudoRoutes.HOME)
                                }
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(LudoRoutes.STATS) {
                        val scopeUid = authState.firebaseUser?.uid
                        key(scopeUid ?: "guest_stats") {
                            val statsVm: StatsHistoryViewModel = viewModel(
                                key = "stats_history_${scopeUid ?: "guest"}",
                                factory = StatsHistoryViewModel.Factory(scopeUid, matchHistoryRepository)
                            )
                            val historyState by statsVm.uiState.collectAsStateWithLifecycle()

                            StatsHistoryScreen(
                                authState = authState,
                                historyState = historyState,
                                onEditNickname = { newName -> authViewModel.updateNickname(newName) },
                                onEditAvatar = { newAvatar -> authViewModel.updateAvatar(newAvatar) },
                                onUpgradeOrSignInClick = { navController.navigate(LudoRoutes.ONLINE_AUTH) },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(LudoRoutes.ONLINE_AUTH) {
                        val user = authState.firebaseUser
                        LaunchedEffect(user?.uid) {
                            if (user != null) {
                                navController.navigate(LudoRoutes.ONLINE_HUB) {
                                    popUpTo(LudoRoutes.ONLINE_AUTH) { inclusive = true }
                                }
                            }
                        }
                        OnlineAuthScreen(
                            authState = authState,
                            onGoogleSignIn = { act -> authViewModel.signInWithGoogle(act) },
                            onContinueAsGuest = { nickname ->
                                authViewModel.continueAsGuest(nickname) {
                                    navController.popBackStack()
                                }
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(LudoRoutes.ONLINE_HUB) {
                        val currentUser = authState.firebaseUser
                        if (currentUser == null) {
                            OnlineAuthScreen(
                                authState = authState,
                                onGoogleSignIn = { act -> authViewModel.signInWithGoogle(act) },
                                onContinueAsGuest = { nickname ->
                                    authViewModel.continueAsGuest(nickname) {
                                        navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        } else {
                            key(currentUser.uid) {
                                val hubViewModel: OnlineHubViewModel = viewModel(
                                    key = "online_hub_${currentUser.uid}",
                                    factory = OnlineHubViewModel.Factory(currentUser.uid, roomRepo)
                                )
                                val hubState by hubViewModel.uiState.collectAsStateWithLifecycle()

                                val startedCode = hubState.currentLobbyRoom
                                    ?.takeIf { it.status == RoomStatus.PLAYING }
                                    ?.roomCode
                                LaunchedEffect(startedCode) {
                                    if (startedCode != null) {
                                        navController.navigate(LudoRoutes.onlineGame(startedCode))
                                    }
                                }

                                OnlineHubScreen(
                                    authState = authState,
                                    hubState = hubState,
                                    onCreatePrivateRoom = { mode, count, timedSec ->
                                        hubViewModel.createPrivateRoom(
                                            nickname = authState.effectiveNickname,
                                            mode = mode,
                                            targetPlayerCount = count,
                                            timedDurationSeconds = timedSec,
                                            avatarId = authState.effectiveAvatarId
                                        ) { code ->
                                            navController.navigate(LudoRoutes.onlineLobby(code))
                                        }
                                    },
                                    onJoinByCode = { code ->
                                        hubViewModel.joinRoomByCode(
                                            roomCode = code,
                                            nickname = authState.effectiveNickname,
                                            avatarId = authState.effectiveAvatarId
                                        ) { room ->
                                            if (room.status == RoomStatus.PLAYING) {
                                                navController.navigate(LudoRoutes.onlineGame(room.roomCode))
                                            } else {
                                                navController.navigate(LudoRoutes.onlineLobby(room.roomCode))
                                            }
                                        }
                                    },
                                    onStartMatchmaking = { mode, count, timedSec ->
                                        hubViewModel.startMatchmaking(
                                            nickname = authState.effectiveNickname,
                                            mode = mode,
                                            targetPlayerCount = count,
                                            timedDurationSeconds = timedSec,
                                            avatarId = authState.effectiveAvatarId
                                        )
                                    },
                                    onCancelMatchmaking = { hubViewModel.cancelMatchmaking() },
                                    onFillMatchmakingWithBots = { hubViewModel.fillMatchmakingWithBotsAndStart() },
                                    onRejoinRoom = { room ->
                                        if (room.status == RoomStatus.PLAYING) {
                                            navController.navigate(LudoRoutes.onlineGame(room.roomCode))
                                        } else {
                                            navController.navigate(LudoRoutes.onlineLobby(room.roomCode))
                                        }
                                    },
                                    onOpenLeaderboard = { navController.navigate(LudoRoutes.LEADERBOARD) },
                                    onSignOut = {
                                        authViewModel.signOut()
                                        navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    composable(
                        route = LudoRoutes.ONLINE_LOBBY,
                        arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val roomCode = backStackEntry.arguments?.getString("roomCode").orEmpty()
                        val currentUser = authState.firebaseUser
                        if (currentUser == null) {
                            OnlineAuthScreen(
                                authState = authState,
                                onGoogleSignIn = { act -> authViewModel.signInWithGoogle(act) },
                                onContinueAsGuest = { nickname ->
                                    authViewModel.continueAsGuest(nickname) {
                                        navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        } else {
                            key(currentUser.uid, roomCode) {
                                val hubViewModel: OnlineHubViewModel = viewModel(
                                    key = "online_lobby_${currentUser.uid}_$roomCode",
                                    factory = OnlineHubViewModel.Factory(currentUser.uid, roomRepo)
                                )
                                val hubState by hubViewModel.uiState.collectAsStateWithLifecycle()

                                LaunchedEffect(roomCode) {
                                    hubViewModel.observeLobby(roomCode, isMatchmaking = false)
                                }

                                val lobbyRoom = hubState.currentLobbyRoom
                                LaunchedEffect(lobbyRoom?.status) {
                                    when (lobbyRoom?.status) {
                                        RoomStatus.PLAYING -> {
                                            navController.navigate(LudoRoutes.onlineGame(roomCode)) {
                                                popUpTo(LudoRoutes.ONLINE_HUB)
                                            }
                                        }
                                        RoomStatus.CLOSED -> {
                                            navController.popBackStack()
                                        }
                                        else -> Unit
                                    }
                                }

                                OnlineLobbyScreen(
                                    room = lobbyRoom,
                                    myUid = currentUser.uid,
                                    isBusy = hubState.isBusy,
                                    errorMessage = hubState.errorMessage,
                                    onSetSeatBot = { idx, diff -> hubViewModel.setLobbySeatBot(idx, diff) },
                                    onStartMatch = { hubViewModel.startLobbyMatch(fillOpenWithBots = true) },
                                    onLeaveLobby = {
                                        hubViewModel.leaveLobby {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    composable(
                        route = LudoRoutes.ONLINE_GAME,
                        arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val roomCode = backStackEntry.arguments?.getString("roomCode").orEmpty()
                        val currentUser = authState.firebaseUser
                        if (currentUser == null) {
                            OnlineAuthScreen(
                                authState = authState,
                                onGoogleSignIn = { act -> authViewModel.signInWithGoogle(act) },
                                onContinueAsGuest = { nickname ->
                                    authViewModel.continueAsGuest(nickname) {
                                        navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        } else {
                            key(currentUser.uid, roomCode) {
                                val onlineGameVm: OnlineGameViewModel = viewModel(
                                    key = "online_game_${currentUser.uid}_$roomCode",
                                    factory = OnlineGameViewModel.Factory(
                                        uid = currentUser.uid,
                                        roomCode = roomCode,
                                        myNickname = authState.effectiveNickname,
                                        roomRepo = roomRepo,
                                        profileRepo = profileRepo,
                                        soundManager = soundManager,
                                        hapticsManager = hapticsManager,
                                        matchHistoryRepo = matchHistoryRepository
                                    )
                                )
                                val wrapper by onlineGameVm.state.collectAsStateWithLifecycle()

                                LaunchedEffect(roomCode) {
                                    settingsViewModel.setActiveOnlineRoomCode(roomCode)
                                }

                                LaunchedEffect(wrapper.isRoomClosed, wrapper.room?.status) {
                                    if (wrapper.isRoomClosed) {
                                        settingsViewModel.setActiveOnlineRoomCode("")
                                        if (!navController.popBackStack(LudoRoutes.ONLINE_HUB, inclusive = false)) {
                                            navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                        }
                                    } else if (wrapper.room?.status == RoomStatus.FINISHED) {
                                        settingsViewModel.setActiveOnlineRoomCode("")
                                    }
                                }

                                val roomStatus = wrapper.room?.status
                                val showingHomeCelebration = wrapper.gameUiState.allTokensHomeCelebration != null
                                if (roomStatus == RoomStatus.FINISHED && !showingHomeCelebration) {
                                    WinnerScreen(
                                        gameState = wrapper.gameUiState.gameState,
                                        onlineRematchUi = wrapper.rematchUi,
                                        onRematchClick = { onlineGameVm.voteRematch() },
                                        onHomeClick = {
                                            settingsViewModel.setActiveOnlineRoomCode("")
                                            onlineGameVm.leaveOnlineMatch {
                                                if (!navController.popBackStack(LudoRoutes.ONLINE_HUB, inclusive = false)) {
                                                    navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    var manualGuideOpen by remember { mutableStateOf(false) }
                                    val activeMode = wrapper.gameUiState.gameState.mode
                                    val showBoardGuide = wrapper.room != null && (
                                        manualGuideOpen || (
                                            settings.isLoaded && !settings.hasSeenModeGuide(activeMode)
                                            )
                                        )
                                    GameBoardScreen(
                                        uiState = wrapper.gameUiState,
                                        showPowerUpGuide = showBoardGuide,
                                        onOpenPowerUpGuide = { manualGuideOpen = true },
                                        onDismissPowerUpGuide = {
                                            manualGuideOpen = false
                                            if (!settings.hasSeenModeGuide(activeMode)) {
                                                settingsViewModel.markModeGuideSeen(activeMode)
                                            }
                                        },
                                        onPowerUpTapped = { onlineGameVm.onPowerUpButtonTapped(it) },
                                        onCancelPowerUpTargeting = { onlineGameVm.onCancelPowerUpTargeting() },
                                        onShieldTokenSelected = { onlineGameVm.onShieldTokenSelected(it) },
                                        onBombSquareSelected = { onlineGameVm.onBombSquareSelected(it) },
                                        isOnlineMode = true,
                                        onlineRoomCode = roomCode,
                                        isLocalPlayersTurn = wrapper.isLocalPlayersTurn,
                                        localViewerColor = wrapper.localColor,
                                        onlineLoading = wrapper.room == null && !wrapper.isRoomClosed,
                                        onlineErrorMessage = wrapper.errorMessage,
                                        onRollDice = { onlineGameVm.onRollDiceTapped() },
                                        onSelectDieValue = { onlineGameVm.onSelectDieValue(it) },
                                        onTokenTapped = { onlineGameVm.onTokenTapped(it) },
                                        onPause = { onlineGameVm.pauseMenuOpen(true) },
                                        onResume = { onlineGameVm.pauseMenuOpen(false) },
                                        onRestart = {},
                                        onEndMatchEarly = {},
                                        onQuitToMenu = {
                                            settingsViewModel.setActiveOnlineRoomCode("")
                                            onlineGameVm.leaveOnlineMatch {
                                                if (!navController.popBackStack(LudoRoutes.ONLINE_HUB, inclusive = false)) {
                                                    navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                                }
                                            }
                                        },
                                        onMatchFinished = { },
                                        onDismissAllTokensHomeCelebration = {
                                            onlineGameVm.dismissAllTokensHomeCelebration()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    composable(LudoRoutes.LEADERBOARD) {
                        val currentUser = authState.firebaseUser
                        if (currentUser == null) {
                            OnlineAuthScreen(
                                authState = authState,
                                subtitleOverride = "Global Leaderboard is exclusively for Google Accounts. Sign in with Google to view Top 100 rankings and your global rank!",
                                onGoogleSignIn = { act -> authViewModel.signInWithGoogle(act) },
                                onContinueAsGuest = { nickname ->
                                    authViewModel.continueAsGuest(nickname) {
                                        navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        } else {
                            key(currentUser.uid) {
                                val leaderboardVm: LeaderboardViewModel = viewModel(
                                    key = "leaderboard_${currentUser.uid}",
                                    factory = LeaderboardViewModel.Factory(currentUser.uid, profileRepo)
                                )
                                val selectedTab by leaderboardVm.selectedTab.collectAsStateWithLifecycle()
                                val lbState by leaderboardVm.uiState.collectAsStateWithLifecycle()

                                LeaderboardScreen(
                                    selectedTab = selectedTab,
                                    uiState = lbState,
                                    myUid = currentUser.uid,
                                    onSelectTab = leaderboardVm::selectTab,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    composable(LudoRoutes.MODES) {
                        ModeSelectionScreen(
                            onBackClick = { navController.popBackStack() },
                            onModeSelected = { mode ->
                                selectedMode = mode
                                navController.navigate(LudoRoutes.SETUP)
                            }
                        )
                    }

                    composable(LudoRoutes.SETUP) {
                        GameSetupScreen(
                            selectedMode = selectedMode,
                            defaultProfileNickname = authState.effectiveNickname,
                            defaultProfileAvatarId = authState.effectiveAvatarId,
                            showPowerUpGuide = false,
                            onDismissPowerUpGuide = { settingsViewModel.markPowerUpGuideSeen() },
                            onBackClick = { navController.popBackStack() },
                            onStartMatch = { setups, mode, timedDurationSec ->
                                gameViewModel.startMatch(setups, mode, timedDurationSec)
                                navController.navigate(LudoRoutes.GAME) {
                                    popUpTo(LudoRoutes.HOME)
                                }
                            }
                        )
                    }

                    composable(LudoRoutes.GAME) {
                        var manualGuideOpen by remember { mutableStateOf(false) }
                        val activeMode = gameUiState.gameState.mode
                        val showModeRulesGuide = manualGuideOpen || (
                            settings.isLoaded && !settings.hasSeenModeGuide(activeMode)
                            )
                        LaunchedEffect(showModeRulesGuide) {
                            gameViewModel.setGuideOverlayVisible(showModeRulesGuide)
                        }

                        GameBoardScreen(
                            uiState = gameUiState,
                            showPowerUpGuide = showModeRulesGuide,
                            onOpenPowerUpGuide = { manualGuideOpen = true },
                            onDismissPowerUpGuide = {
                                manualGuideOpen = false
                                if (!settings.hasSeenModeGuide(activeMode)) {
                                    settingsViewModel.markModeGuideSeen(activeMode)
                                }
                            },
                            onPowerUpTapped = { type -> gameViewModel.onPowerUpButtonTapped(type) },
                            onCancelPowerUpTargeting = { gameViewModel.onCancelPowerUpTargeting() },
                            onShieldTokenSelected = { tokenId -> gameViewModel.onShieldTokenSelected(tokenId) },
                            onBombSquareSelected = { trackIdx -> gameViewModel.onBombSquareSelected(trackIdx) },
                            onRollDice = { gameViewModel.onRollDiceTapped() },
                            onDiceRollStarted = { gameViewModel.onHumanDiceRollStarted() },
                            onDiceRollResult = { first, second ->
                                gameViewModel.onHumanDiceRollCompleted(first, second)
                            },
                            onSelectDieValue = { dieVal -> gameViewModel.onSelectDieValue(dieVal) },
                            onTokenTapped = { tokenRef -> gameViewModel.onTokenTapped(tokenRef) },
                            onPause = { gameViewModel.pauseGame() },
                            onResume = { gameViewModel.resumeGame() },
                            onRestart = { gameViewModel.rematch() },
                            onEndMatchEarly = { gameViewModel.endMatchEarly() },
                            onQuitToMenu = {
                                gameViewModel.stopMatch()
                                navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                            },
                            onMatchFinished = {
                                val finalState = gameUiState.gameState
                                val winnerColor = finalState.finishOrder.firstOrNull()
                                val firstHuman = finalState.players.firstOrNull { !it.setup.isBot }
                                if (firstHuman != null && winnerColor != null) {
                                    authViewModel.recordLocalMatchOutcomeForGuest(
                                        won = firstHuman.color == winnerColor
                                    )
                                }
                                coroutineScope.launch {
                                    savedGameRepository.clearSavedGame()
                                    matchHistoryRepository.recordMatch(
                                        entry = MatchHistoryRepository.buildEntryFromGameState(
                                            finalState = finalState,
                                            onlineMatch = false,
                                            localColor = firstHuman?.color
                                        ),
                                        authenticatedUid = authState.firebaseUser?.uid
                                    )
                                }
                                navController.navigate(LudoRoutes.WINNER) {
                                    popUpTo(LudoRoutes.GAME) { inclusive = true }
                                }
                            },
                            onDismissAllTokensHomeCelebration = {
                                gameViewModel.dismissAllTokensHomeCelebration()
                            }
                        )
                    }

                    composable(LudoRoutes.WINNER) {
                        WinnerScreen(
                            gameState = gameUiState.gameState,
                            onRematchClick = {
                                gameViewModel.rematch()
                                navController.navigate(LudoRoutes.GAME) {
                                    popUpTo(LudoRoutes.WINNER) { inclusive = true }
                                }
                            },
                            onHomeClick = {
                                gameViewModel.stopMatch()
                                navController.popBackStack(LudoRoutes.HOME, inclusive = false)
                            }
                        )
                    }

                    composable(LudoRoutes.SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            onToggleSoundEffects = settingsViewModel::setSoundEffects,
                            onSoundEffectsVolumeLiveChange = { vol ->
                                soundManager.updateSettings(
                                    soundEffects = vol > 0f,
                                    soundEffectsVolume = vol
                                )
                            },
                            onSoundEffectsVolumeCommit = { vol ->
                                soundManager.updateSettings(
                                    soundEffects = vol > 0f,
                                    soundEffectsVolume = vol
                                )
                                settingsViewModel.setSoundEffectsVolume(vol)
                                if (vol > 0f) {
                                    soundManager.playTokenMove()
                                }
                            },
                            onToggleVibration = settingsViewModel::setVibration,
                            onReplayTutorialClick = { navController.navigate(LudoRoutes.TUTORIAL) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
