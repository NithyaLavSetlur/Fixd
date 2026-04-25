package com.example.fixd

import android.content.Context

object SocialControlPreferences {
    private const val PREFS_NAME = "fixd_social_controls"
    private const val KEY_APP_CONTROL_ENABLED = "app_control_enabled"
    private const val KEY_FLOATING_BUBBLE_ENABLED = "floating_bubble_enabled"
    private const val KEY_INSTAGRAM_BLOCK_REELS = "instagram_block_reels"
    private const val KEY_INSTAGRAM_DISABLE_DISCOVER = "instagram_disable_discover"
    private const val KEY_YOUTUBE_BLOCK_SHORTS = "youtube_block_shorts"

    fun load(context: Context): SocialControlSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SocialControlSettings(
            appControlEnabled = prefs.getBoolean(KEY_APP_CONTROL_ENABLED, false),
            floatingBubbleEnabled = prefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, false),
            instagramBlockReels = prefs.getBoolean(KEY_INSTAGRAM_BLOCK_REELS, true),
            instagramDisableDiscover = prefs.getBoolean(KEY_INSTAGRAM_DISABLE_DISCOVER, true),
            youtubeBlockShorts = prefs.getBoolean(KEY_YOUTUBE_BLOCK_SHORTS, true)
        )
    }

    fun save(context: Context, settings: SocialControlSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_CONTROL_ENABLED, settings.appControlEnabled)
            .putBoolean(KEY_FLOATING_BUBBLE_ENABLED, settings.floatingBubbleEnabled)
            .putBoolean(KEY_INSTAGRAM_BLOCK_REELS, settings.instagramBlockReels)
            .putBoolean(KEY_INSTAGRAM_DISABLE_DISCOVER, settings.instagramDisableDiscover)
            .putBoolean(KEY_YOUTUBE_BLOCK_SHORTS, settings.youtubeBlockShorts)
            .apply()
    }
}
