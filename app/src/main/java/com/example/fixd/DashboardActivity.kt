package com.example.fixd

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.fixd.databinding.ActivityDashboardBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ProfileFragment.Host {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private var selectedProblems: List<ProblemArea> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navigationView.setNavigationItemSelectedListener(this)
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            ProblemArea.fromMenuItemId(item.itemId)?.let { area ->
                showProblemTab(area)
                true
            } ?: false
        }

        loadProfile(initialLoad = savedInstanceState == null)
    }

    private fun loadProfile(initialLoad: Boolean = false) {
        val user = auth.currentUser ?: return
        UserProfileRepository.getProfile(
            userId = user.uid,
            onSuccess = getProfileSuccess@{ profile ->
                selectedProblems = profile?.selectedProblems.orEmpty().mapNotNull { ProblemArea.fromName(it) }
                if (selectedProblems.isEmpty()) {
                    startActivity(Intent(this, ProblemSelectionActivity::class.java))
                    finish()
                    return@getProfileSuccess
                }

                refreshUserShell(profile)
                configureBottomNavigation()

                if (initialLoad) {
                    binding.navigationView.setCheckedItem(R.id.menu_home)
                    showHomePage()
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
        clearBottomNavSelection()
    }

    private fun clearBottomNavSelection() {
        repeat(binding.bottomNavigationView.menu.size()) { index ->
            binding.bottomNavigationView.menu.getItem(index).isChecked = false
        }
    }

    private fun showProblemTab(area: ProblemArea) {
        binding.navigationView.setCheckedItem(0)
        binding.screenTitle.text = getString(area.titleRes)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ProblemTabFragment.newInstance(area))
            .commit()
    }

    private fun showHomePage() {
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.home_page_title)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, HomeFragment.newInstance(selectedProblems))
            .commit()
    }

    private fun showInfoPage(title: String, body: String, cta: String? = null) {
        clearBottomNavSelection()
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
        clearBottomNavSelection()
        binding.screenTitle.text = getString(R.string.profile_page_title)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_home -> showHomePage()
            R.id.menu_profile -> showProfilePage()
            R.id.menu_settings -> showInfoPage(
                title = getString(R.string.settings_page_title),
                body = getString(R.string.settings_page_body),
                cta = getString(R.string.settings_page_cta)
            )
            R.id.menu_premium -> showInfoPage(
                title = getString(R.string.premium_page_title),
                body = getString(R.string.premium_page_body),
                cta = getString(R.string.premium_page_cta)
            )
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
}
