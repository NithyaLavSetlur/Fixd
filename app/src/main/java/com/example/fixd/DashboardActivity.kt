package com.example.fixd

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.GravityCompat
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.fixd.databinding.ActivityDashboardBinding
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ProfileFragment.Host, HomeFragment.Host {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private var selectedProblems: List<ProblemArea> = emptyList()
    private var activeDrawerItemId: Int = R.id.menu_home

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppBackgroundManager.applyToActivity(this)
        applyTopChromeColors()

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                AppBackgroundManager.updateSettings(settings)
                ThemePaletteManager.syncFromAppearance(this)
                AppBackgroundManager.applyToActivity(this)
                applyTopChromeColors()
            },
            onFailure = { }
        )

        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
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
        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = getProfileSuccess@{ profile ->
                val effectiveSelection = profile?.selectedProblems
                    ?.takeIf { it.isNotEmpty() }
                    ?: profile?.availableProblems.orEmpty()
                selectedProblems = effectiveSelection.mapNotNull { ProblemArea.fromName(it) }
                if (selectedProblems.isEmpty()) {
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
        val user = auth.currentUser ?: return
        val displayName = profile?.preferredName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: getString(R.string.guest_label)

        binding.greetingText.text = getString(R.string.dashboard_greeting, displayName)
    }

    private fun configureBottomNavigation() {
        val menu = binding.bottomNavigationView.menu
        menu.clear()
        selectedProblems.forEachIndexed { index, area ->
            menu.add(0, area.menuItemId, index, area.titleRes).setIcon(area.iconRes)
        }
        binding.bottomNavigationView.itemBackground = ContextCompat.getDrawable(this, R.drawable.bg_bottom_nav_item)
        clearBottomNavSelection()
    }

    private fun clearBottomNavSelection() {
        repeat(binding.bottomNavigationView.menu.size()) { index ->
            binding.bottomNavigationView.menu.getItem(index).isChecked = false
        }
    }

    private fun showProblemTab(area: ProblemArea) {
        clearDrawerSelection()
        selectBottomNavigationItem(area.menuItemId)
        binding.screenTitle.text = getString(area.titleRes)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ProblemTabFragment.newInstance(area))
            .commit()
    }

    private fun showHomePage() {
        setDrawerSelection(R.id.menu_home)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.home_page_title)
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
        clearBottomNavSelection()
        clearDrawerSelection()
        binding.screenTitle.text = title
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
        setDrawerSelection(R.id.menu_profile)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.profile_page_title)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
    }

    private fun showSettingsPage() {
        setDrawerSelection(R.id.menu_settings)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.settings_page_title)
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
        repeat(binding.bottomNavigationView.menu.size()) { index ->
            val item = binding.bottomNavigationView.menu.getItem(index)
            item.isChecked = item.itemId == itemId
        }
    }

    companion object {
        const val EXTRA_OPEN_AREA = "open_area"
        const val EXTRA_OPEN_ACTION = "open_action"
        const val OPEN_ACTION_CREATE_ALARM = "create_alarm"
    }

    private fun showPremiumPage() {
        setDrawerSelection(R.id.menu_premium)
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.premium_page_title)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, PremiumFragment())
            .commit()
    }

    override fun openHomeArea(area: ProblemArea) {
        showProblemTab(area)
    }

    private fun setDrawerSelection(itemId: Int) {
        activeDrawerItemId = itemId
        binding.navigationView.setCheckedItem(itemId)
    }

    private fun clearDrawerSelection() {
        activeDrawerItemId = 0
        repeat(binding.navigationView.menu.size()) { index ->
            binding.navigationView.menu.getItem(index).isChecked = false
        }
    }

    private fun applyTopChromeColors() {
        val isDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val topColor = ContextCompat.getColor(this, if (isDarkMode) R.color.black else R.color.white)
        window.statusBarColor = topColor
        (binding.menuButton.parent as? View)?.let { header ->
            header.setBackgroundColor(topColor)
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
