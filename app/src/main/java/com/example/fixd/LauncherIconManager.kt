package com.example.fixd

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

data class LauncherIconOption(
    val id: String,
    val labelRes: Int,
    val componentClassName: String,
    val previewColor: Long
)

object LauncherIconManager {
    const val ICON_DEFAULT = "default"
    const val ICON_MINT = "mint"
    const val ICON_OCEAN = "ocean"
    const val ICON_ORCHID = "orchid"
    const val ICON_SUNSET = "sunset"

    val options = listOf(
        LauncherIconOption(
            id = ICON_DEFAULT,
            labelRes = R.string.settings_launcher_icon_default,
            componentClassName = "com.example.fixd.LauncherIconDefault",
            previewColor = 0xFF83E7CD
        ),
        LauncherIconOption(
            id = ICON_MINT,
            labelRes = R.string.settings_launcher_icon_mint,
            componentClassName = "com.example.fixd.LauncherIconMint",
            previewColor = 0xFF26B98F
        ),
        LauncherIconOption(
            id = ICON_OCEAN,
            labelRes = R.string.settings_launcher_icon_ocean,
            componentClassName = "com.example.fixd.LauncherIconOcean",
            previewColor = 0xFF2A8CEB
        ),
        LauncherIconOption(
            id = ICON_ORCHID,
            labelRes = R.string.settings_launcher_icon_orchid,
            componentClassName = "com.example.fixd.LauncherIconOrchid",
            previewColor = 0xFFB05BEA
        ),
        LauncherIconOption(
            id = ICON_SUNSET,
            labelRes = R.string.settings_launcher_icon_sunset,
            componentClassName = "com.example.fixd.LauncherIconSunset",
            previewColor = 0xFFFF8A47
        )
    )

    fun optionFor(id: String): LauncherIconOption {
        return options.firstOrNull { it.id == id } ?: options.first()
    }

    fun apply(context: Context, iconId: String) {
        val selected = optionFor(iconId)
        val packageManager = context.packageManager
        options.forEach { option ->
            val state = if (option.id == selected.id) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, option.componentClassName),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
        UserPreferences.saveLauncherIconId(context, selected.id)
    }

    fun reconcile(context: Context) {
        apply(context, UserPreferences.getLauncherIconId(context))
    }
}
