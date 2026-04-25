package com.example.fixd

data class SocialControlSettings(
    val appControlEnabled: Boolean = false,
    val floatingBubbleEnabled: Boolean = false,
    val instagramBlockReels: Boolean = true,
    val instagramDisableDiscover: Boolean = true,
    val youtubeBlockShorts: Boolean = true
)
