package com.example.fixd

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.GravityCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.drawerlayout.widget.DrawerLayout
import com.example.fixd.databinding.ActivityDashboardBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ProfileFragment.Host, HomeFragment.Host, SettingsFragment.Host {
    private enum class FloatingDestination {
        HOME,
        TAB_ROOT,
        TAB_SECONDARY
    }

    private enum class FloatingMenuAction {
        GO_HOME,
        GO_TAB_ROOT,
        GO_TAB_SECONDARY
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private var selectedProblems: List<ProblemArea> = emptyList()
    private var activeDrawerItemId: Int = R.id.menu_home
    private var isFloatingMenuExpanded = false
    private var isOnProblemTabPage = false
    private var currentProblemArea: ProblemArea? = null
    private var currentFloatingDestination = FloatingDestination.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemePaletteManager.applyToActivity(this)
        applyTopChromeColors()

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                binding.dashboardMistBorder.setAnimationsEnabled(slideOffset <= 0f)
            }

            override fun onDrawerOpened(drawerView: View) {
                binding.dashboardMistBorder.setAnimationsEnabled(false)
            }

            override fun onDrawerClosed(drawerView: View) {
                binding.dashboardMistBorder.setAnimationsEnabled(true)
            }
        })
        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                ThemePaletteManager.updateSettings(settings)
                ThemePaletteManager.syncFromAppearance(this)
                applyTopChromeColors()
            },
            onFailure = { }
        )

        binding.menuButton.setOnClickListener {
            if (isOnProblemTabPage) {
                toggleFloatingMenu()
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
        setupFloatingMenu()
        binding.navigationView.setNavigationItemSelectedListener(this)
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            animateBottomNavIcon(item.itemId)
            ProblemArea.fromMenuItemId(item.itemId)?.let { area ->
                showProblemTab(area)
                true
            } ?: false
        }
        binding.bottomNavigationView.setOnItemReselectedListener { item ->
            animateBottomNavIcon(item.itemId)
            ProblemArea.fromMenuItemId(item.itemId)?.let(::showProblemTab)
        }

        loadProfile(initialLoad = savedInstanceState == null)
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
                configureBottomNavigation()

                if (initialLoad) {
                    setDrawerSelection(R.id.menu_home)
                    handleInitialDestination()
                }
            },
            onFailure = {
                Toast.makeText(this, it.localizedMessage ?: getString(R.string.firebase_not_ready), Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun refreshUserShell(profile: UserProfile?) {
        val displayName = profile?.preferredName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: getString(R.string.guest_label)

        binding.greetingText.text = getString(R.string.dashboard_greeting, displayName)
    }

    private fun configureBottomNavigation() {
        val menu = binding.bottomNavigationView.menu
        menu.clear()
        selectedProblems.forEachIndexed { index, area ->
            menu.add(0, area.menuItemId, index, area.titleRes).setIcon(area.iconRes)
        }
        binding.bottomNavigationView.itemBackground = null
        updateBottomNavSelection(null)
    }

    private fun clearBottomNavSelection() {
        updateBottomNavSelection(null)
    }

    private fun updateBottomNavSelection(selectedItemId: Int?) {
        val menu = binding.bottomNavigationView.menu
        menu.setGroupCheckable(0, false, true)
        repeat(menu.size()) { index ->
            menu.getItem(index).isChecked = false
        }
        menu.setGroupCheckable(0, true, true)

        if (selectedItemId != null) {
            menu.findItem(selectedItemId)?.isChecked = true
        }

        val menuView = binding.bottomNavigationView.getChildAt(0) as? BottomNavigationMenuView ?: run {
            binding.bottomNavigationView.invalidate()
            return
        }
        repeat(menuView.childCount.coerceAtMost(menu.size())) { index ->
            val isActive = menu.getItem(index).itemId == selectedItemId
            menuView.getChildAt(index)?.apply {
                isSelected = isActive
                isActivated = isActive
                refreshDrawableState()
            }
        }
        binding.bottomNavigationView.invalidate()
    }

    private fun showProblemTab(area: ProblemArea) {
        currentProblemArea = area
        currentFloatingDestination = FloatingDestination.TAB_ROOT
        clearDrawerSelection()
        updateBottomNavSelection(area.menuItemId)
        binding.screenTitle.text = getString(area.titleRes)
        setProblemTabChrome(enabled = true)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ProblemTabFragment.newInstance(area))
            .commit()
    }

    private fun showHomePage() {
        currentProblemArea = null
        currentFloatingDestination = FloatingDestination.HOME
        setDrawerSelection(R.id.menu_home)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.home_page_title)
        setProblemTabChrome(enabled = false)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, HomeFragment.newInstance(selectedProblems))
            .commit()
    }

    private fun animateBottomNavIcon(itemId: Int) {
        val menuView = binding.bottomNavigationView.getChildAt(0) as? BottomNavigationMenuView ?: return
        val itemView = menuView.findViewById<View>(itemId) ?: return
        val iconView = itemView.findViewById<ImageView>(com.google.android.material.R.id.navigation_bar_item_icon_view)
            ?: return

        iconView.animate().cancel()
        iconView.rotation = 0f
        iconView.scaleX = 1f
        iconView.scaleY = 1f

        iconView.animate()
            .rotation(-12f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(70)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                iconView.animate()
                    .rotation(10f)
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .setDuration(110)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction {
                        iconView.animate()
                            .rotation(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .setInterpolator(FastOutSlowInInterpolator())
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun showInfoPage(title: String, body: String, cta: String? = null) {
        currentProblemArea = null
        currentFloatingDestination = FloatingDestination.HOME
        clearBottomNavSelection()
        clearDrawerSelection()
        binding.screenTitle.text = title
        setProblemTabChrome(enabled = false)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(
                R.id.fragmentContainer,
                InfoPageFragment.newInstance(
                    title = title,
                    body = body,
                    cta = cta,
                    ctaEnabled = false
                )
            )
            .commit()
    }

    private fun showProfilePage() {
        currentProblemArea = null
        currentFloatingDestination = FloatingDestination.HOME
        setDrawerSelection(R.id.menu_profile)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.profile_page_title)
        setProblemTabChrome(enabled = false)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
    }

    private fun showSettingsPage() {
        currentProblemArea = null
        currentFloatingDestination = FloatingDestination.HOME
        setDrawerSelection(R.id.menu_settings)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.settings_page_title)
        setProblemTabChrome(enabled = false)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, SettingsFragment())
            .commit()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
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

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onProfileUpdated() {
        auth.currentUser?.reload()?.addOnCompleteListener {
            loadProfile()
            showProfilePage()
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
        val requestedArea = ProblemArea.fromName(intent.getStringExtra(EXTRA_OPEN_AREA).orEmpty())
        if (requestedArea != null && selectedProblems.contains(requestedArea)) {
            showProblemTab(requestedArea)
        } else {
            showHomePage()
        }
    }

    fun consumeOpenAction(expectedAction: String): Boolean {
        val currentIntent = intent ?: return false
        val action = currentIntent.getStringExtra(EXTRA_OPEN_ACTION).orEmpty()
        if (action != expectedAction) return false
        currentIntent.removeExtra(EXTRA_OPEN_ACTION)
        setIntent(currentIntent)
        return true
    }

    private fun selectBottomNavigationItem(itemId: Int) {
        updateBottomNavSelection(itemId)
    }

    companion object {
        const val EXTRA_OPEN_AREA = "open_area"
        const val EXTRA_OPEN_ACTION = "open_action"
        const val OPEN_ACTION_CREATE_ALARM = "create_alarm"
    }

    private fun showPremiumPage() {
        currentProblemArea = null
        currentFloatingDestination = FloatingDestination.HOME
        setDrawerSelection(R.id.menu_premium)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.premium_page_title)
        setProblemTabChrome(enabled = false)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, PremiumFragment())
            .commit()
    }

    override fun openHomeArea(area: ProblemArea) {
        showProblemTab(area)
    }

    override fun openTabDisplayChooser() {
        showProfilePage()
    }

    override fun onTabOrderSaved() {
        loadProfile()
        showSettingsPage()
    }

    private fun setDrawerSelection(itemId: Int) {
        activeDrawerItemId = itemId
        binding.navigationView.setCheckedItem(itemId)
        updateFloatingMenuSelection(itemId)
    }

    private fun clearDrawerSelection() {
        activeDrawerItemId = 0
        repeat(binding.navigationView.menu.size()) { index ->
            binding.navigationView.menu.getItem(index).isChecked = false
        }
        updateFloatingMenuSelection(0)
    }

    private fun applyTopChromeColors() {
        val palette = ThemePaletteManager.currentPalette(this)
        val isDarkMode = UserPreferences.isDarkMode(this)
        val chromeColor = if (isDarkMode) palette.surface else palette.card
        val contentColor = palette.surface
        window.statusBarColor = chromeColor
        binding.drawerLayout.setBackgroundColor(chromeColor)
        binding.drawerLayout.setStatusBarBackgroundColor(chromeColor)
        binding.dashboardContentRoot.setBackgroundColor(palette.surface)
        (binding.menuButton.parent as? View)?.let { header ->
            header.setBackgroundColor(chromeColor)
        }
        binding.menuButton.backgroundTintList = ColorStateList.valueOf(
            if (isDarkMode) chromeColor else palette.card
        )
        binding.bottomNavigationView.setBackgroundColor(if (isDarkMode) contentColor else palette.card)
        binding.navigationView.setBackgroundColor(palette.card)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            androidx.core.graphics.ColorUtils.calculateLuminance(chromeColor) > 0.5
    }

    private fun setupFloatingMenu() {
        binding.dashboardContentRoot.setOnClickListener {
            if (isOnProblemTabPage && isFloatingMenuExpanded) {
                setFloatingMenuExpanded(false)
            }
        }
        binding.floatingMenuPanel.setOnClickListener { }
        binding.floatingMenuHome.setOnClickListener { handleFloatingMenuButtonClick(binding.floatingMenuHome) }
        binding.floatingMenuProfile.setOnClickListener { handleFloatingMenuButtonClick(binding.floatingMenuProfile) }
        binding.floatingMenuSettings.setOnClickListener { handleFloatingMenuButtonClick(binding.floatingMenuSettings) }
        binding.floatingMenuPremium.visibility = View.GONE
        binding.floatingMenuLogout.visibility = View.GONE
        configureFloatingMenuForCurrentPage()
    }

    private fun setProblemTabChrome(enabled: Boolean) {
        isOnProblemTabPage = enabled
        configureFloatingMenuForCurrentPage()
        if (!enabled) {
            setFloatingMenuExpanded(false)
            binding.floatingMenuPanel.visibility = View.GONE
        }
    }

    private fun toggleFloatingMenu() {
        if (!isOnProblemTabPage) return
        setFloatingMenuExpanded(!isFloatingMenuExpanded)
    }

    private fun setFloatingMenuExpanded(expanded: Boolean) {
        if (isFloatingMenuExpanded == expanded && (expanded || binding.floatingMenuPanel.visibility != View.VISIBLE)) {
            if (!expanded) return
        }
        isFloatingMenuExpanded = expanded
        binding.menuButton.animate().rotation(if (expanded) 90f else 0f).setDuration(220).start()
        if (expanded) {
            binding.floatingMenuPanel.visibility = View.VISIBLE
            binding.floatingMenuPanel.alpha = 0f
            binding.floatingMenuPanel.translationY = -12f
            binding.floatingMenuPanel.scaleX = 0.94f
            binding.floatingMenuPanel.scaleY = 0.94f
            binding.floatingMenuPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            binding.floatingMenuPanel.animate()
                .alpha(0f)
                .translationY(-12f)
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(180)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    binding.floatingMenuPanel.visibility = View.GONE
                }
                .start()
        }
    }

    private fun updateFloatingMenuSelection(activeItemId: Int) {
        val palette = ThemePaletteManager.currentPalette(this)
        val activeBackground = palette.primary
        val inactiveBackground = palette.card
        val activeText = palette.onPrimary
        val inactiveText = palette.text

        applyFloatingMenuButtonState(binding.floatingMenuHome, activeItemId == R.id.menu_home, activeBackground, inactiveBackground, activeText, inactiveText)
        applyFloatingMenuButtonState(binding.floatingMenuProfile, activeItemId == 1, activeBackground, inactiveBackground, activeText, inactiveText)
        applyFloatingMenuButtonState(binding.floatingMenuSettings, activeItemId == 2, activeBackground, inactiveBackground, activeText, inactiveText)
    }

    private fun applyFloatingMenuButtonState(
        button: MaterialButton,
        isActive: Boolean,
        activeBackground: Int,
        inactiveBackground: Int,
        activeText: Int,
        inactiveText: Int
    ) {
        val background = if (isActive) activeBackground else inactiveBackground
        button.setBackgroundColor(background)
        button.setTextColor(
            if (androidx.core.graphics.ColorUtils.calculateContrast(activeText, background) >= 4.5) {
                if (isActive) activeText else inactiveText
            } else if (androidx.core.graphics.ColorUtils.calculateContrast(inactiveText, background) >= 4.5) {
                inactiveText
            } else if (androidx.core.graphics.ColorUtils.calculateLuminance(background) > 0.45) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        )
        button.strokeWidth = if (isActive) 0 else 2.dp
        button.strokeColor = android.content.res.ColorStateList.valueOf(
            if (isActive) activeBackground else ThemePaletteManager.currentPalette(this).accent
        )
    }

    private fun handleFloatingMenuButtonClick(button: MaterialButton) {
        val action = button.tag as? FloatingMenuAction ?: return
        setFloatingMenuExpanded(false)
        when (action) {
            FloatingMenuAction.GO_HOME -> showHomePage()
            FloatingMenuAction.GO_TAB_ROOT -> handleFloatingMenuPrimaryTabAction()
            FloatingMenuAction.GO_TAB_SECONDARY -> handleFloatingMenuSecondaryTabAction()
        }
    }

    private fun bindFloatingMenuButton(button: MaterialButton, label: String, action: FloatingMenuAction) {
        button.visibility = View.VISIBLE
        button.text = label
        button.tag = action
    }

    private fun configureFloatingMenuForCurrentPage() {
        if (!isOnProblemTabPage) {
            binding.floatingMenuPanel.visibility = View.GONE
            return
        }

        binding.floatingMenuHome.visibility = View.GONE
        binding.floatingMenuProfile.visibility = View.GONE
        binding.floatingMenuSettings.visibility = View.GONE
        binding.floatingMenuPremium.visibility = View.GONE
        binding.floatingMenuLogout.visibility = View.GONE

        when (currentProblemArea) {
            ProblemArea.WAKE_UP -> {
                bindFloatingMenuButton(
                    binding.floatingMenuHome,
                    getString(R.string.drawer_home),
                    FloatingMenuAction.GO_HOME
                )
                if (currentFloatingDestination == FloatingDestination.TAB_ROOT) {
                    bindFloatingMenuButton(
                        binding.floatingMenuProfile,
                        getString(R.string.wake_history_full_title),
                        FloatingMenuAction.GO_TAB_SECONDARY
                    )
                } else {
                    bindFloatingMenuButton(
                        binding.floatingMenuProfile,
                        getString(R.string.problem_wake_up),
                        FloatingMenuAction.GO_TAB_ROOT
                    )
                }
                updateFloatingMenuSelection(
                    when (currentFloatingDestination) {
                        FloatingDestination.HOME -> R.id.menu_home
                        FloatingDestination.TAB_ROOT -> 1
                        FloatingDestination.TAB_SECONDARY -> 2
                    }
                )
            }
            null -> {
                updateFloatingMenuSelection(R.id.menu_home)
            }
            else -> {
                bindFloatingMenuButton(
                    binding.floatingMenuHome,
                    getString(R.string.drawer_home),
                    FloatingMenuAction.GO_HOME
                )
                updateFloatingMenuSelection(
                    when (currentFloatingDestination) {
                        FloatingDestination.HOME -> R.id.menu_home
                        else -> 1
                    }
                )
            }
        }
    }

    private fun handleFloatingMenuPrimaryTabAction() {
        when (currentProblemArea) {
            null -> showHomePage()
            else -> showProblemTab(currentProblemArea!!)
        }
    }

    private fun handleFloatingMenuSecondaryTabAction() {
        if (currentProblemArea == ProblemArea.WAKE_UP) {
            showWakeHistoryPage()
        }
    }

    fun showWakeHistoryPage() {
        currentProblemArea = ProblemArea.WAKE_UP
        currentFloatingDestination = FloatingDestination.TAB_SECONDARY
        clearDrawerSelection()
        selectBottomNavigationItem(ProblemArea.WAKE_UP.menuItemId)
        binding.screenTitle.text = getString(ProblemArea.WAKE_UP.titleRes)
        setProblemTabChrome(enabled = true)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, WakeHistoryFragment())
            .commit()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
