package com.example.fixd

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {
    private enum class DrawerDestination(val id: Int, val titleRes: Int) {
        HOME(R.id.menu_home, R.string.drawer_home),
        PROFILE(R.id.menu_profile, R.string.drawer_profile),
        SETTINGS(R.id.menu_settings, R.string.drawer_settings),
        PREMIUM(R.id.menu_premium, R.string.drawer_premium),
        LOGOUT(R.id.menu_logout, R.string.drawer_logout)
    }

    private sealed interface DashboardScreen {
        data object Home : DashboardScreen
        data object Profile : DashboardScreen
        data object Settings : DashboardScreen
        data object Premium : DashboardScreen
        data class Problem(val area: ProblemArea) : DashboardScreen
        data class Challenges(val page: ChallengePage) : DashboardScreen
        data object WakeHistory : DashboardScreen
    }

    private enum class SavedDestination(val prefix: String) {
        HOME("home"),
        PROFILE("profile"),
        SETTINGS("settings"),
        PREMIUM("premium"),
        PROBLEM("problem:"),
        CHALLENGES("challenges:"),
        WAKE_HISTORY("wake_history");

        companion object {
            fun fromValue(value: String): SavedDestination? = entries.firstOrNull { destination ->
                if (destination == HOME || destination == PROFILE || destination == SETTINGS || destination == PREMIUM || destination == WAKE_HISTORY) {
                    value == destination.prefix
                } else {
                    value.startsWith(destination.prefix)
                }
            }
        }
    }

    private fun dashboardScreenKey(screen: DashboardScreen): String = when (screen) {
        DashboardScreen.Home -> "home"
        DashboardScreen.Profile -> "profile"
        DashboardScreen.Settings -> "settings"
        DashboardScreen.Premium -> "premium"
        is DashboardScreen.Problem -> "problem_${screen.area.name}"
        is DashboardScreen.Challenges -> "challenge_${screen.page.name}"
        DashboardScreen.WakeHistory -> "wake_history"
    }

    private lateinit var auth: FirebaseAuth

    private var selectedProblems by mutableStateOf<List<ProblemArea>>(emptyList())
    private var activeDrawerItemId by mutableIntStateOf(R.id.menu_home)
    private var selectedBottomNavItemId by mutableStateOf<Int?>(null)
    private var greetingText by mutableStateOf("")
    private var screenTitle by mutableStateOf("")
    private var currentScreen by mutableStateOf<DashboardScreen>(DashboardScreen.Home)
    private var floatingMenuExpanded by mutableStateOf(false)
    private var pendingOpenAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        setContent {
            FixdComposeTheme {
                DashboardScreen()
            }
        }
        applyTopChromeColors()

        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                val previous = ThemePaletteManager.currentSettings()
                ThemePaletteManager.updateSettings(settings)
                UserPreferences.saveThemeMode(this, settings.themeMode)
                UserPreferences.saveThemeSeedColor(this, settings.themeSeedColor)
                applyTopChromeColors()
                if (previous != settings) {
                    UserPreferences.applyThemeMode(settings.themeMode)
                }
            },
            onFailure = { }
        )

        loadProfile(initialLoad = savedInstanceState == null)
    }

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser ?: return
        user.reload()
            .addOnFailureListener { exception ->
                if (exception is FirebaseAuthInvalidUserException) {
                    auth.signOut()
                    startActivity(NavigationRouter.authIntent(this))
                    finish()
                }
            }
    }

    private fun loadProfile(initialLoad: Boolean = false) {
        val user = auth.currentUser ?: return
        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = getProfileSuccess@{ profile ->
                val availableProblems = profile.availableProblems.mapNotNull { ProblemArea.fromName(it) }
                selectedProblems = profile.selectedProblems.mapNotNull { ProblemArea.fromName(it) }
                if (availableProblems.isEmpty()) {
                    startActivity(Intent(this, ProblemSelectionActivity::class.java))
                    finish()
                    return@getProfileSuccess
                }

                refreshUserShell(profile)
                if (initialLoad) {
                    activeDrawerItemId = R.id.menu_home
                    handleInitialDestination()
                }
            },
            onFailure = {
                android.widget.Toast.makeText(this, it.localizedMessage ?: getString(R.string.firebase_not_ready), android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun refreshUserShell(profile: UserProfile?) {
        val displayName = profile?.preferredName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: getString(R.string.guest_label)
        greetingText = getString(R.string.dashboard_greeting, displayName)
    }

    private fun showProblemTab(area: ProblemArea) {
        if (area == ProblemArea.CHALLENGES) {
            showChallengesPage(ChallengePage.BOARD)
            return
        }
        currentScreen = DashboardScreen.Problem(area)
        activeDrawerItemId = 0
        selectedBottomNavItemId = area.menuItemId
        screenTitle = getString(area.titleRes)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun showChallengesPage(page: ChallengePage) {
        currentScreen = DashboardScreen.Challenges(page)
        activeDrawerItemId = 0
        selectedBottomNavItemId = ProblemArea.CHALLENGES.menuItemId
        screenTitle = getString(ProblemArea.CHALLENGES.titleRes)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun showHomePage() {
        currentScreen = DashboardScreen.Home
        activeDrawerItemId = R.id.menu_home
        selectedBottomNavItemId = null
        screenTitle = getString(R.string.home_page_title)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun showProfilePage() {
        currentScreen = DashboardScreen.Profile
        activeDrawerItemId = R.id.menu_profile
        selectedBottomNavItemId = null
        screenTitle = getString(R.string.profile_page_title)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun showSettingsPage() {
        currentScreen = DashboardScreen.Settings
        activeDrawerItemId = R.id.menu_settings
        selectedBottomNavItemId = null
        screenTitle = getString(R.string.settings_page_title)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun showPremiumPage() {
        currentScreen = DashboardScreen.Premium
        activeDrawerItemId = R.id.menu_premium
        selectedBottomNavItemId = null
        screenTitle = getString(R.string.premium_page_title)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun showWakeHistoryPage() {
        currentScreen = DashboardScreen.WakeHistory
        activeDrawerItemId = 0
        selectedBottomNavItemId = ProblemArea.WAKE_UP.menuItemId
        screenTitle = getString(R.string.wake_history_full_title)
        floatingMenuExpanded = false
        persistCurrentDestination()
    }

    private fun handleDrawerDestination(itemId: Int) {
        when (itemId) {
            R.id.menu_home -> showHomePage()
            R.id.menu_profile -> showProfilePage()
            R.id.menu_settings -> showSettingsPage()
            R.id.menu_premium -> showPremiumPage()
            R.id.menu_logout -> {
                auth.signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (selectedProblems.isNotEmpty()) {
            handleInitialDestination()
        }
    }

    private fun handleInitialDestination() {
        pendingOpenAction = intent.getStringExtra(EXTRA_OPEN_ACTION)
        val requestedArea = ProblemArea.fromName(intent.getStringExtra(EXTRA_OPEN_AREA).orEmpty())
        if (requestedArea != null && selectedProblems.contains(requestedArea)) {
            if (requestedArea == ProblemArea.CHALLENGES) {
                val requestedChallengePage = runCatching {
                    ChallengePage.valueOf(intent.getStringExtra(EXTRA_OPEN_CHALLENGE_PAGE).orEmpty())
                }.getOrNull() ?: ChallengePage.BOARD
                showChallengesPage(requestedChallengePage)
            } else {
                showProblemTab(requestedArea)
            }
        } else {
            restoreLastDestinationOrDefault()
        }
    }

    private fun restoreLastDestinationOrDefault() {
        val userId = auth.currentUser?.uid
        val saved = userId?.let { UserPreferences.getLastDestination(this, it) }
        when (SavedDestination.fromValue(saved.orEmpty())) {
            SavedDestination.HOME -> showHomePage()
            SavedDestination.PROFILE -> showProfilePage()
            SavedDestination.SETTINGS -> showSettingsPage()
            SavedDestination.PREMIUM -> showPremiumPage()
            SavedDestination.WAKE_HISTORY -> showWakeHistoryPage()
            SavedDestination.PROBLEM -> {
                val areaName = saved?.removePrefix(SavedDestination.PROBLEM.prefix).orEmpty()
                val area = ProblemArea.fromName(areaName)
                if (area != null && selectedProblems.contains(area)) {
                    showProblemTab(area)
                } else {
                    showHomePage()
                }
            }
            SavedDestination.CHALLENGES -> {
                val pageName = saved?.removePrefix(SavedDestination.CHALLENGES.prefix).orEmpty()
                val page = runCatching { ChallengePage.valueOf(pageName) }.getOrNull()
                if (selectedProblems.contains(ProblemArea.CHALLENGES) && page != null) {
                    showChallengesPage(page)
                } else {
                    showHomePage()
                }
            }
            null -> showHomePage()
        }
    }

    private fun persistCurrentDestination() {
        val userId = auth.currentUser?.uid ?: return
        val value = when (val screen = currentScreen) {
            DashboardScreen.Home -> SavedDestination.HOME.prefix
            DashboardScreen.Profile -> SavedDestination.PROFILE.prefix
            DashboardScreen.Settings -> SavedDestination.SETTINGS.prefix
            DashboardScreen.Premium -> SavedDestination.PREMIUM.prefix
            is DashboardScreen.Problem -> SavedDestination.PROBLEM.prefix + screen.area.name
            is DashboardScreen.Challenges -> SavedDestination.CHALLENGES.prefix + screen.page.name
            DashboardScreen.WakeHistory -> SavedDestination.WAKE_HISTORY.prefix
        }
        UserPreferences.saveLastDestination(this, userId, value)
    }

    private fun consumePendingOpenAction(expectedAction: String): Boolean {
        val current = pendingOpenAction ?: return false
        if (current != expectedAction) return false
        pendingOpenAction = null
        return true
    }

    private fun applyTopChromeColors() {
        val palette = ThemePaletteManager.currentPalette(this)
        val chromeColor = if (UserPreferences.isDarkMode(this)) palette.surface else palette.card
        window.statusBarColor = chromeColor
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            androidx.core.graphics.ColorUtils.calculateLuminance(chromeColor) > 0.5
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DashboardScreen() {
        val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val currentProblemArea = (currentScreen as? DashboardScreen.Problem)?.area
        val currentChallengePage = (currentScreen as? DashboardScreen.Challenges)?.page
        val isProblemContext = currentProblemArea != null || currentScreen == DashboardScreen.WakeHistory || currentChallengePage != null
        val hasFloatingTabMenu =
            currentProblemArea == ProblemArea.WAKE_UP ||
                currentScreen == DashboardScreen.WakeHistory ||
                currentChallengePage != null
        val density = LocalDensity.current
        val bubbleSize = 58.dp
        val bubblePadding = 16.dp
        val bubbleDefaultTopOffset = 94.dp
        val bubbleSizePx = with(density) { bubbleSize.toPx() }
        val bubblePaddingPx = with(density) { bubblePadding.toPx() }
        val bubbleDefaultTopOffsetPx = with(density) { bubbleDefaultTopOffset.toPx() }
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        var bubbleOffset by remember { mutableStateOf(Offset.Unspecified) }
        var bubbleIsDragging by remember { mutableStateOf(false) }
        val animatedBubbleX by animateFloatAsState(
            targetValue = if (bubbleOffset == Offset.Unspecified) 0f else bubbleOffset.x,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
            label = "tab_bubble_x"
        )
        val animatedBubbleY by animateFloatAsState(
            targetValue = if (bubbleOffset == Offset.Unspecified) 0f else bubbleOffset.y,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 640f),
            label = "tab_bubble_y"
        )
        val bubbleScale by animateFloatAsState(
            targetValue = when {
                bubbleIsDragging -> 0.96f
                floatingMenuExpanded -> 1.07f
                else -> 1f
            },
            animationSpec = spring(dampingRatio = 0.74f, stiffness = 500f),
            label = "tab_bubble_scale"
        )
        val bubbleIconRotation by animateFloatAsState(
            targetValue = if (floatingMenuExpanded) 90f else 0f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 560f),
            label = "tab_bubble_rotation"
        )
        val bubbleElevation by animateDpAsState(
            targetValue = if (floatingMenuExpanded) 16.dp else 10.dp,
            label = "tab_bubble_elevation"
        )
        var refreshTick by remember { mutableIntStateOf(0) }
        var refreshing by remember { mutableStateOf(false) }

        fun refreshCurrentScreen() {
            refreshing = true
            auth.currentUser?.reload()?.addOnCompleteListener {
                loadProfile()
                refreshTick += 1
                refreshing = false
            } ?: run {
                loadProfile()
                refreshTick += 1
                refreshing = false
            }
        }

        fun clampBubbleOffset(offset: Offset): Offset {
            if (viewportSize == IntSize.Zero) return offset
            val maxX = (viewportSize.width.toFloat() - bubbleSizePx - bubblePaddingPx).coerceAtLeast(bubblePaddingPx)
            val maxY = (viewportSize.height.toFloat() - bubbleSizePx - bubblePaddingPx).coerceAtLeast(bubblePaddingPx)
            return Offset(
                x = offset.x.coerceIn(bubblePaddingPx, maxX),
                y = offset.y.coerceIn(bubblePaddingPx, maxY)
            )
        }

        fun snapBubbleToSide(offset: Offset): Offset {
            if (viewportSize == IntSize.Zero) return offset
            val maxX = (viewportSize.width.toFloat() - bubbleSizePx - bubblePaddingPx).coerceAtLeast(bubblePaddingPx)
            val snapX = if (offset.x + (bubbleSizePx / 2f) < viewportSize.width / 2f) bubblePaddingPx else maxX
            return clampBubbleOffset(offset.copy(x = snapX))
        }

        LaunchedEffect(hasFloatingTabMenu, viewportSize) {
            if (!hasFloatingTabMenu) {
                floatingMenuExpanded = false
                return@LaunchedEffect
            }
            if (viewportSize != IntSize.Zero) {
                bubbleOffset = if (bubbleOffset == Offset.Unspecified) {
                    snapBubbleToSide(
                        Offset(
                            x = viewportSize.width.toFloat() - bubbleSizePx - bubblePaddingPx,
                            y = bubblePaddingPx + bubbleDefaultTopOffsetPx
                        )
                    )
                } else {
                    snapBubbleToSide(bubbleOffset)
                }
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(272.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_fixd_panda_logo_monochrome),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(id = R.string.drawer_header_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        DrawerDestination.entries.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(id = destination.titleRes)) },
                                selected = activeDrawerItemId == destination.id,
                                onClick = {
                                    handleDrawerDestination(destination.id)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            modifier = Modifier.clip(
                                androidx.compose.foundation.shape.RoundedCornerShape(
                                    bottomStart = 24.dp,
                                    bottomEnd = 24.dp
                                )
                            ),
                            title = { Text(screenTitle.ifBlank { stringResource(id = R.string.dashboard_title) }) },
                            navigationIcon = {
                                if (isProblemContext) {
                                    IconButton(onClick = { showHomePage() }) {
                                        Icon(Icons.Outlined.Home, contentDescription = stringResource(id = R.string.drawer_home))
                                    }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Outlined.Menu, contentDescription = stringResource(id = R.string.menu_open))
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        if (selectedProblems.isNotEmpty()) {
                            NavigationBar(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                selectedProblems.forEach { area ->
                                    NavigationBarItem(
                                        selected = selectedBottomNavItemId == area.menuItemId,
                                        onClick = { showProblemTab(area) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = area.iconRes),
                                                contentDescription = stringResource(id = area.titleRes),
                                                modifier = Modifier.size(30.dp)
                                            )
                                        },
                                        label = null,
                                        alwaysShowLabel = false
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                                .animateContentSize(),
                        ) {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = { refreshCurrentScreen() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AnimatedContent(
                                    targetState = currentScreen to refreshTick,
                                    contentKey = { dashboardScreenKey(it.first) + "_${it.second}" },
                                    transitionSpec = {
                                        (fadeIn() + slideInVertically(initialOffsetY = { it / 12 })) togetherWith
                                            (fadeOut() + slideOutVertically(targetOffsetY = { -it / 18 }))
                                    },
                                    label = "dashboard_screen_transition"
                                ) { (screen, _) ->
                                    when (screen) {
                                        DashboardScreen.Home -> HomeRoute(
                                            selectedProblems = selectedProblems,
                                            onOpenArea = { showProblemTab(it) },
                                            onOpenTabDisplayChooser = { showProfilePage() }
                                        )
                                        DashboardScreen.Profile -> ProfileRoute(
                                            onProfileUpdated = {
                                                auth.currentUser?.reload()?.addOnCompleteListener {
                                                    loadProfile()
                                                    showProfilePage()
                                                }
                                            }
                                        )
                                        DashboardScreen.Settings -> SettingsRoute(
                                            onTabOrderSaved = {
                                                loadProfile()
                                                showSettingsPage()
                                            }
                                        )
                                        DashboardScreen.Premium -> PremiumRoute()
                                        is DashboardScreen.Problem -> {
                                            when (screen.area) {
                                                ProblemArea.WAKE_UP -> WakeRoute(
                                                    pendingCreateAlarm = pendingOpenAction == OPEN_ACTION_CREATE_ALARM,
                                                    onCreateAlarmConsumed = { consumePendingOpenAction(OPEN_ACTION_CREATE_ALARM) },
                                                    onShowFullHistory = { showWakeHistoryPage() }
                                                )
                                                ProblemArea.COUNTDOWN -> CountdownRoute()
                                                ProblemArea.SOCIAL_MEDIA_DISTRACTION -> SocialMediaDistractionRoute()
                                                else -> ProblemAreaPlaceholderRoute(screen.area)
                                            }
                                        }
                                        is DashboardScreen.Challenges -> ChallengesRoute(
                                            page = screen.page,
                                            onNavigateToPage = { showChallengesPage(it) }
                                        )
                                        DashboardScreen.WakeHistory -> WakeHistoryRoute()
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasFloatingTabMenu && bubbleOffset != Offset.Unspecified) {
                    Box(
                        modifier = Modifier.offset {
                            val renderedOffset = if (bubbleIsDragging) bubbleOffset else Offset(animatedBubbleX, animatedBubbleY)
                            IntOffset(
                                x = renderedOffset.x.roundToInt(),
                                y = renderedOffset.y.roundToInt()
                            )
                        }
                    ) {
                        Card(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = bubbleScale
                                    scaleY = bubbleScale
                                }
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .pointerInput(viewportSize, isProblemContext) {
                                    detectDragGestures(
                                        onDragStart = {
                                            floatingMenuExpanded = false
                                            bubbleIsDragging = true
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val currentOffset = if (bubbleOffset == Offset.Unspecified) {
                                                Offset(bubblePaddingPx, bubblePaddingPx)
                                            } else {
                                                bubbleOffset
                                            }
                                            bubbleOffset = clampBubbleOffset(
                                                Offset(
                                                    x = currentOffset.x + dragAmount.x,
                                                    y = currentOffset.y + dragAmount.y
                                                )
                                            )
                                        },
                                        onDragEnd = {
                                            bubbleIsDragging = false
                                            bubbleOffset = snapBubbleToSide(bubbleOffset)
                                        },
                                        onDragCancel = {
                                            bubbleIsDragging = false
                                            bubbleOffset = snapBubbleToSide(bubbleOffset)
                                        }
                                    )
                                },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                            elevation = CardDefaults.cardElevation(defaultElevation = bubbleElevation)
                        ) {
                            IconButton(
                                onClick = { floatingMenuExpanded = !floatingMenuExpanded },
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(id = R.string.menu_open),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = bubbleIconRotation
                                    }
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = floatingMenuExpanded,
                            onDismissRequest = { floatingMenuExpanded = false }
                        ) {
                            when {
                                currentProblemArea == ProblemArea.WAKE_UP -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.wake_history_full_title)) },
                                        onClick = {
                                            floatingMenuExpanded = false
                                            showWakeHistoryPage()
                                        }
                                    )
                                }
                                currentScreen == DashboardScreen.WakeHistory -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.problem_wake_up)) },
                                        onClick = {
                                            floatingMenuExpanded = false
                                            showProblemTab(ProblemArea.WAKE_UP)
                                        }
                                    )
                                }
                                currentChallengePage != null -> {
                                    listOf(
                                        ChallengePage.BOARD,
                                        ChallengePage.MANAGE,
                                        ChallengePage.HISTORY,
                                        ChallengePage.SHOP,
                                        ChallengePage.GALLERY,
                                        ChallengePage.FRIENDS,
                                        ChallengePage.BADGES
                                    ).forEach { page ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(id = page.titleRes)) },
                                            onClick = {
                                                floatingMenuExpanded = false
                                                showChallengesPage(page)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_AREA = "open_area"
        const val EXTRA_OPEN_CHALLENGE_PAGE = "open_challenge_page"
        const val EXTRA_OPEN_ACTION = "open_action"
        const val OPEN_ACTION_CREATE_ALARM = "create_alarm"
    }
}
