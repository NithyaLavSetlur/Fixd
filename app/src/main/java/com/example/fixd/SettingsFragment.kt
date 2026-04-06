package com.example.fixd

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import com.example.fixd.databinding.FragmentSettingsBinding
import com.example.fixd.databinding.ViewTabOrderItemBinding
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    interface Host {
        fun onTabOrderSaved()
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var currentAppearance = UserAppearanceSettings()
    private var currentProfile = UserProfile()
    private var pendingSeedColor = ThemePaletteManager.DEFAULT_SEED_COLOR
    private val pendingTabOrder = mutableListOf<ProblemArea>()
    private lateinit var tabOrderAdapter: TabOrderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemePaletteManager.applyToView(binding.root, ThemePaletteManager.currentPalette(requireContext()))
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        setupTabOrderEditor()

        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                currentAppearance = settings
                pendingSeedColor = settings.themeSeedColor
                ThemePaletteManager.updateSettings(settings)
                syncCurrentThemeSelection(settings.themeMode)
                bindColorPreview(settings.themeSeedColor)
                bindListeners(user.uid)
            },
            onFailure = {
                syncCurrentThemeSelection(UserPreferences.THEME_SYSTEM)
                bindColorPreview(pendingSeedColor)
                bindListeners(user.uid)
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )

        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                currentProfile = profile
                bindTabOrderPreview(profile)
            },
            onFailure = {
                binding.tabOrderEmptyText.visibility = View.VISIBLE
                binding.saveTabOrderButton.isEnabled = false
                toast(it.localizedMessage ?: getString(R.string.firebase_not_ready))
            }
        )
    }

    private fun bindListeners(userId: String) {
        binding.themeModeGroup.setOnCheckedChangeListener(themeCheckedListener)
        binding.colorWheel.onColorChanged = { color ->
            pendingSeedColor = color
            bindColorPreview(color)
        }
        binding.applyPaletteButton.setOnClickListener {
            applyPalette(userId)
        }
        binding.saveTabOrderButton.setOnClickListener {
            saveTabOrder(userId)
        }
    }

    private fun setupTabOrderEditor() {
        tabOrderAdapter = TabOrderAdapter(pendingTabOrder)
        binding.tabOrderRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.tabOrderRecyclerView.adapter = tabOrderAdapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val moved = pendingTabOrder.removeAt(from)
                pendingTabOrder.add(to, moved)
                tabOrderAdapter.notifyItemMoved(from, to)
                tabOrderAdapter.notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(from - to) + 1)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = true
        }).attachToRecyclerView(binding.tabOrderRecyclerView)
    }

    private fun bindTabOrderPreview(profile: UserProfile) {
        pendingTabOrder.clear()
        pendingTabOrder += profile.selectedProblems.mapNotNull { ProblemArea.fromName(it) }
        tabOrderAdapter.notifyDataSetChanged()

        val hasTabs = pendingTabOrder.isNotEmpty()
        binding.tabOrderRecyclerView.visibility = if (hasTabs) View.VISIBLE else View.GONE
        binding.tabOrderEmptyText.visibility = if (hasTabs) View.GONE else View.VISIBLE
        binding.saveTabOrderButton.isEnabled = hasTabs
    }

    private fun syncCurrentThemeSelection(themeMode: String) {
        binding.themeModeGroup.check(
            when (themeMode) {
                UserPreferences.THEME_LIGHT -> R.id.themeLight
                UserPreferences.THEME_DARK -> R.id.themeDark
                else -> R.id.themeSystem
            }
        )
    }

    private val themeCheckedListener = RadioGroup.OnCheckedChangeListener { _, checkedId ->
        val newMode = when (checkedId) {
            R.id.themeLight -> UserPreferences.THEME_LIGHT
            R.id.themeDark -> UserPreferences.THEME_DARK
            else -> UserPreferences.THEME_SYSTEM
        }
        if (newMode == currentAppearance.themeMode) return@OnCheckedChangeListener
        val user = auth.currentUser ?: return@OnCheckedChangeListener

        UserAppearanceRepository.saveThemeMode(
            userId = user.uid,
            themeMode = newMode,
            onSuccess = {
                currentAppearance = currentAppearance.copy(themeMode = newMode)
                ThemePaletteManager.updateSettings(currentAppearance)
                UserPreferences.saveThemeMode(requireContext(), newMode)
                UserPreferences.applyThemeMode(newMode)
                requireActivity().recreate()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun bindColorPreview(seedColor: Int) {
        binding.colorWheel.selectedColor = seedColor
        binding.selectedColorLabel.text = getString(
            R.string.settings_palette_selected,
            String.format("#%06X", 0xFFFFFF and seedColor)
        )

        val palette = ThemePaletteManager.paletteFor(
            currentAppearance.copy(themeSeedColor = seedColor),
            UserPreferences.isDarkMode(requireContext())
        )
        tintSwatch(binding.swatchPrimary, palette.primary)
        tintSwatch(binding.swatchSecondary, palette.secondary)
        tintSwatch(binding.swatchSurface, palette.accent)
        tintSwatch(binding.swatchCard, palette.card)
    }

    private fun tintSwatch(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * resources.displayMetrics.density
            setColor(color)
            if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.92) {
                setStroke((1.5f * resources.displayMetrics.density).toInt(), 0xFFE4E4E0.toInt())
            }
        }
    }

    private fun applyPalette(userId: String) {
        UserAppearanceRepository.saveThemeSeedColor(
            userId = userId,
            themeSeedColor = pendingSeedColor,
            onSuccess = { saved ->
                currentAppearance = currentAppearance.copy(themeSeedColor = saved.themeSeedColor)
                ThemePaletteManager.updateSettings(currentAppearance)
                UserPreferences.saveThemeSeedColor(requireContext(), saved.themeSeedColor)
                requireActivity().recreate()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun saveTabOrder(userId: String) {
        val orderedTabs = pendingTabOrder.map { it.name }
        if (orderedTabs.isEmpty()) {
            toast(R.string.settings_tab_order_empty)
            return
        }

        UserProfileRepository.saveProfile(
            userId = userId,
            profile = PremiumAccess.sanitizeProfile(
                currentProfile.copy(selectedProblems = orderedTabs)
            ),
            onSuccess = {
                currentProfile = currentProfile.copy(selectedProblems = orderedTabs)
                toast(R.string.settings_tab_order_saved)
                (activity as? Host)?.onTabOrderSaved()
            },
            onFailure = { toast(it.localizedMessage ?: getString(R.string.firebase_not_ready)) }
        )
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class TabOrderAdapter(
        private val items: List<ProblemArea>
    ) : RecyclerView.Adapter<TabOrderAdapter.TabOrderViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabOrderViewHolder {
            val binding = ViewTabOrderItemBinding.inflate(layoutInflater, parent, false)
            return TabOrderViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TabOrderViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        private inner class TabOrderViewHolder(
            private val itemBinding: ViewTabOrderItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(area: ProblemArea, position: Int) {
                itemBinding.tabOrderIcon.setImageResource(area.iconRes)
                itemBinding.tabOrderTitle.setText(area.titleRes)
                itemBinding.tabOrderPosition.text = getString(R.string.settings_tab_order_position, position + 1)
                ThemePaletteManager.applyToView(itemBinding.root, ThemePaletteManager.currentPalette(itemBinding.root.context))
            }
        }
    }
}
