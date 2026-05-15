package com.example.fixd

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

class SocialControlAccessibilityService : AccessibilityService() {
    private var lastInstagramRedirectAt = 0L
    private var lastYoutubeRedirectAt = 0L
    private var currentForegroundPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pendingInstagramRedirects = mutableListOf<Runnable>()
    private val pendingInstagramRechecks = mutableListOf<Runnable>()
    private val pendingYoutubeRechecks = mutableListOf<Runnable>()
    private var instagramForegroundWatchdog: Runnable? = null
    private var youtubeForegroundWatchdog: Runnable? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
            }
            notificationTimeout = 50
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callback = createAccessibilityButtonCallback()
            accessibilityButtonCallback = callback
            accessibilityButtonController.registerAccessibilityButtonCallback(callback)
        }
   }

    override fun onDestroy() {
        clearInstagramRedirects()
        clearInstagramRechecks()
        clearYoutubeRechecks()
        clearInstagramForegroundWatchdog()
        clearYoutubeForegroundWatchdog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonCallback?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        }
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAccessibilityButtonCallback() = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            SocialControlManager.toggleQuickSettings(
                this@SocialControlAccessibilityService,
                targetApp = currentForegroundPackage
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        if (!isTransientOverlayPackage(packageName)) {
            currentForegroundPackage = packageName
        }
        val settings = SocialControlPreferences.load(this)
        if (!settings.appControlEnabled) {
            clearInstagramForegroundWatchdog()
            clearYoutubeForegroundWatchdog()
            hideBlocker()
            return
        }

        val effectivePackage = when {
            isSocialTargetPackage(packageName) -> packageName
            isTransientOverlayPackage(packageName) -> currentForegroundPackage.orEmpty()
            else -> packageName
        }

        when {
            effectivePackage.contains("youtube", ignoreCase = true) -> {
                ensureYoutubeForegroundWatchdog()
                clearInstagramForegroundWatchdog()
                handleYoutube(settings, event)
            }
            effectivePackage.contains("instagram", ignoreCase = true) -> {
                ensureInstagramForegroundWatchdog()
                clearYoutubeForegroundWatchdog()
                handleInstagram(settings, event)
            }
            else -> {
                clearInstagramForegroundWatchdog()
                clearYoutubeForegroundWatchdog()
                hideBlocker()
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun handleYoutube(settings: SocialControlSettings, event: AccessibilityEvent?) {
        if (!settings.youtubeBlockShorts) {
            hideBlocker()
            return
        }
        val root = findRootForPackage(YOUTUBE_PACKAGE, event?.source)
        if (root == null) {
            Log.d(TAG, "YouTube check skipped: no YouTube root found; scheduling recheck")
            scheduleYoutubeRecheck()
            return
        }
        clearYoutubeRechecks()
        val onShortsTab = isNodeSelectedMatchingAny(root, YOUTUBE_SHORTS_TAB_SIGNALS)
        val inShortsViewer = isYoutubeShortsViewer(root, event)
        Log.d(TAG, "YouTube check: onShortsTab=$onShortsTab inShortsViewer=$inShortsViewer")
        if (onShortsTab || inShortsViewer) {
            redirectYoutubeToHome(root)
            hideBlocker()
        } else {
            if (eventSuggestsYoutubeShorts(event)) {
                scheduleYoutubeRecheck()
            }
            hideBlocker()
        }
    }

    private fun handleInstagram(settings: SocialControlSettings, event: AccessibilityEvent?) {
        if (!settings.instagramBlockReels) {
            clearInstagramRedirects()
            clearInstagramRechecks()
            hideBlocker()
            return
        }

        val root = findRootForPackage(INSTAGRAM_PACKAGE, event?.source)
        if (root == null) {
            Log.d(TAG, "Instagram check skipped: no Instagram root found; scheduling recheck")
            scheduleInstagramRecheck()
            return
        }
        clearInstagramRechecks()
        val inReelViewer = isInstagramReelViewer(root, event)

        Log.d(TAG, "Instagram check: inReelViewer=$inReelViewer")

        if (inReelViewer) {
            redirectInstagramToMessages(root, backOutOfViewerFirst = true)
        } else {
            clearInstagramRedirects()
            hideBlocker()
        }
    }

    private fun redirectInstagramToMessages(
        root: AccessibilityNodeInfo,
        backOutOfViewerFirst: Boolean = false
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInstagramRedirectAt < INSTAGRAM_REDIRECT_COOLDOWN_MS) {
            return true
        }
        clearInstagramRedirects()
        clearInstagramRechecks()
        lastInstagramRedirectAt = now
        val immediateRedirect = runInstagramMessagesRedirectAttempt(root, backOutOfViewerFirst)
        scheduleInstagramMessagesRedirectAttempts()
        hideBlocker()
        return immediateRedirect
    }

    private fun runInstagramMessagesRedirectAttempt(
        root: AccessibilityNodeInfo,
        backOutOfViewerFirst: Boolean
    ): Boolean {
        if (isInstagramMessagesContext(root)) {
            clearInstagramRedirects()
            return true
        }

        val clickedMessages = clickNode(findInstagramMessagesTarget(root))
        if (clickedMessages) {
            Log.d(TAG, "Instagram redirect: clicked Messages tab")
            return true
        }

        return backOutOfViewerFirst && performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun scheduleInstagramMessagesRedirectAttempts() {
        val attempts = listOf(90L, 220L, 420L, 760L, 1150L)
        attempts.forEach { delayMs ->
            val runnable = Runnable {
                val root = findRootForPackage(INSTAGRAM_PACKAGE)
                if (root == null) return@Runnable
                runInstagramMessagesRedirectAttempt(root, backOutOfViewerFirst = false)
            }
            pendingInstagramRedirects += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun clearInstagramRedirects() {
        pendingInstagramRedirects.forEach { handler.removeCallbacks(it) }
        pendingInstagramRedirects.clear()
    }

    private fun scheduleInstagramRecheck() {
        clearInstagramRechecks()
        listOf(120L, 280L, 520L).forEach { delayMs ->
            val runnable = Runnable {
                val settings = SocialControlPreferences.load(this)
                if (currentForegroundPackage?.contains("instagram", ignoreCase = true) == true) {
                    handleInstagram(settings, null)
                }
            }
            pendingInstagramRechecks += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun clearInstagramRechecks() {
        pendingInstagramRechecks.forEach { handler.removeCallbacks(it) }
        pendingInstagramRechecks.clear()
    }

    private fun ensureInstagramForegroundWatchdog() {
        if (instagramForegroundWatchdog != null) return
        val runnable = object : Runnable {
            override fun run() {
                val settings = SocialControlPreferences.load(this@SocialControlAccessibilityService)
                if (!settings.appControlEnabled || currentForegroundPackage?.contains("instagram", ignoreCase = true) != true) {
                    clearInstagramForegroundWatchdog()
                    return
                }
                handleInstagram(settings, null)
                handler.postDelayed(this, INSTAGRAM_FOREGROUND_WATCHDOG_MS)
            }
        }
        instagramForegroundWatchdog = runnable
        handler.postDelayed(runnable, INSTAGRAM_FOREGROUND_WATCHDOG_MS)
    }

    private fun clearInstagramForegroundWatchdog() {
        instagramForegroundWatchdog?.let { handler.removeCallbacks(it) }
        instagramForegroundWatchdog = null
    }

    private fun scheduleYoutubeRecheck() {
        clearYoutubeRechecks()
        listOf(120L, 280L, 520L, 820L).forEach { delayMs ->
            val runnable = Runnable {
                val settings = SocialControlPreferences.load(this)
                if (currentForegroundPackage?.contains("youtube", ignoreCase = true) == true) {
                    handleYoutube(settings, null)
                }
            }
            pendingYoutubeRechecks += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun clearYoutubeRechecks() {
        pendingYoutubeRechecks.forEach { handler.removeCallbacks(it) }
        pendingYoutubeRechecks.clear()
    }

    private fun ensureYoutubeForegroundWatchdog() {
        if (youtubeForegroundWatchdog != null) return
        val runnable = object : Runnable {
            override fun run() {
                val settings = SocialControlPreferences.load(this@SocialControlAccessibilityService)
                if (!settings.appControlEnabled || currentForegroundPackage?.contains("youtube", ignoreCase = true) != true) {
                    clearYoutubeForegroundWatchdog()
                    return
                }
                handleYoutube(settings, null)
                handler.postDelayed(this, YOUTUBE_FOREGROUND_WATCHDOG_MS)
            }
        }
        youtubeForegroundWatchdog = runnable
        handler.postDelayed(runnable, YOUTUBE_FOREGROUND_WATCHDOG_MS)
    }

    private fun clearYoutubeForegroundWatchdog() {
        youtubeForegroundWatchdog?.let { handler.removeCallbacks(it) }
        youtubeForegroundWatchdog = null
    }

    private fun redirectYoutubeToHome(root: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastYoutubeRedirectAt < YOUTUBE_REDIRECT_COOLDOWN_MS) {
            return true
        }
        val target = findYoutubeHomeTarget(root)
        val clicked = clickNode(target)
        if (clicked) {
            lastYoutubeRedirectAt = now
            hideBlocker()
            return true
        }
        val relaunched = launchYoutubeHome()
        if (relaunched) {
            lastYoutubeRedirectAt = now
            hideBlocker()
            return true
        }
        val backedOut = performGlobalAction(GLOBAL_ACTION_BACK)
        if (backedOut) {
            lastYoutubeRedirectAt = now
        }
        return backedOut
    }

    private fun findRootForPackage(
        targetPackage: String,
        eventSource: AccessibilityNodeInfo? = null
    ): AccessibilityNodeInfo? {
        rootFromNode(eventSource)?.let { sourceRoot ->
            val sourcePackage = sourceRoot.packageName?.toString().orEmpty()
            if (sourcePackage == targetPackage) {
                return sourceRoot
            }
        }

        windows
            ?.asReversed()
            ?.forEach { window ->
                val root = window.root ?: return@forEach
                val packageName = root.packageName?.toString().orEmpty()
                if (packageName == targetPackage) {
                    return root
                }
            }

        val activeRoot = rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString().orEmpty()
        return activeRoot?.takeIf { activePackage == targetPackage }
    }

    private fun rootFromNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var last = node
        while (current != null) {
            last = current
            current = current.parent
        }
        return last
    }

    private fun isInstagramReelViewer(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?
    ): Boolean {
        if (isInstagramExploreReelGrid(root)) {
            return false
        }

        val signalText = buildString {
            append(flattenNodeText(root))
            append(' ')
            append(event?.text?.joinToString(" ").orEmpty())
            append(' ')
            append(event?.contentDescription?.toString().orEmpty())
            append(' ')
            append(event?.className?.toString().orEmpty())
        }

        if (INSTAGRAM_REEL_VIEWER_STRONG_SIGNALS.any { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        }) return true

        val hasViewerChrome = INSTAGRAM_REEL_VIEWER_CHROME_SIGNALS.count { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        } >= 2
        val hasReelMediaSignal = INSTAGRAM_REEL_MEDIA_SIGNALS.any { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        }
        return hasViewerChrome && hasReelMediaSignal
    }

    private fun isInstagramMessagesContext(root: AccessibilityNodeInfo): Boolean {
        return isInstagramTabSelected(root, INSTAGRAM_DIRECT_TAB_IDS) ||
            isNodeSelectedMatchingAny(root, INSTAGRAM_MESSAGES_TAB_SIGNALS)
    }

    private fun isInstagramSearchContext(root: AccessibilityNodeInfo): Boolean {
        return isInstagramTabSelected(root, INSTAGRAM_SEARCH_TAB_IDS) ||
            isNodeSelectedMatchingAny(root, INSTAGRAM_SEARCH_TAB_SIGNALS)
    }

    private fun isInstagramExploreReelGrid(root: AccessibilityNodeInfo): Boolean {
        return isInstagramSearchContext(root) &&
            (countNodesByViewId(root, INSTAGRAM_REEL_GRID_PLAY_COUNT_ID) >= 2 ||
                countNodesContainingAny(root, INSTAGRAM_REEL_GRID_PREVIEW_SIGNALS) >= 3)
    }

    private fun isYoutubeShortsViewer(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?
    ): Boolean {
        val signalText = buildString {
            append(flattenNodeText(root))
            append(' ')
            append(event?.text?.joinToString(" ").orEmpty())
            append(' ')
            append(event?.contentDescription?.toString().orEmpty())
            append(' ')
            append(event?.className?.toString().orEmpty())
        }

        val hasShortsViewerSignal = YOUTUBE_SHORTS_VIEWER_SIGNALS.any { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        }
        if (!hasShortsViewerSignal) return false

        return true
    }

    private fun eventSuggestsYoutubeShorts(event: AccessibilityEvent?): Boolean {
        if (event == null) return false
        val signalText = buildString {
            append(event.text?.joinToString(" ").orEmpty())
            append(' ')
            append(event.contentDescription?.toString().orEmpty())
            append(' ')
            append(event.className?.toString().orEmpty())
        }
        return YOUTUBE_SHORTS_EVENT_SIGNALS.any { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        }
    }

    private fun flattenNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        return buildString {
            append(node.text ?: "")
            append(' ')
            append(node.contentDescription ?: "")
            append(' ')
            append(node.viewIdResourceName ?: "")
            for (index in 0 until node.childCount) {
                append(' ')
                append(flattenNodeText(node.getChild(index)))
            }
        }
    }

    private fun treeContains(node: AccessibilityNodeInfo, keyword: String): Boolean {
        val text = buildString {
            append(node.text ?: "")
            append(' ')
            append(node.contentDescription ?: "")
            append(' ')
            append(node.viewIdResourceName ?: "")
        }
        if (text.contains(keyword, ignoreCase = true)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (treeContains(child, keyword)) return true
        }
        return false
    }

    private fun treeContainsAny(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        return keywords.any { keyword -> treeContains(node, keyword) }
    }

    private fun findNodeContainingAny(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = buildString {
            append(node.text ?: "")
            append(' ')
            append(node.contentDescription ?: "")
            append(' ')
            append(node.viewIdResourceName ?: "")
        }
        if (keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }) {
            return node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNodeContainingAny(child, keywords)
            if (match != null) return match
        }
        return null
    }

    private fun findNodeByViewId(node: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName == viewId) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNodeByViewId(child, viewId)
            if (match != null) return match
        }
        return null
    }

    private fun countNodesByViewId(node: AccessibilityNodeInfo?, viewId: String): Int {
        if (node == null) return 0
        var count = if (node.viewIdResourceName == viewId) 1 else 0
        for (index in 0 until node.childCount) {
            count += countNodesByViewId(node.getChild(index), viewId)
        }
        return count
    }

    private fun countNodesContainingAny(node: AccessibilityNodeInfo?, keywords: List<String>): Int {
        if (node == null) return 0
        var count = if (nodeMatchesAny(node, keywords)) 1 else 0
        for (index in 0 until node.childCount) {
            count += countNodesContainingAny(node.getChild(index), keywords)
        }
        return count
    }

    private fun isInstagramTabSelected(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return isInstagramTabSelected(root, listOf(viewId))
    }

    private fun isInstagramTabSelected(root: AccessibilityNodeInfo, viewIds: List<String>): Boolean {
        return viewIds.any { viewId -> findNodeByViewId(root, viewId)?.isSelected == true }
    }

    private fun isNodeSelectedMatchingAny(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        if (node.isSelected && nodeMatchesAny(node, keywords)) {
            return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (isNodeSelectedMatchingAny(child, keywords)) return true
        }
        return false
    }

    private fun nodeMatchesAny(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = buildString {
            append(node.text ?: "")
            append(' ')
            append(node.contentDescription ?: "")
            append(' ')
            append(node.viewIdResourceName ?: "")
        }
        return keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun launchYoutubeHome(): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return false
        return runCatching {
            startActivity(launchIntent)
            true
        }.getOrElse { false }
    }

    private fun hideBlocker() {
        // No visible blocker is shown now; enforcement redirects silently.
    }

    private fun findInstagramMessagesTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        INSTAGRAM_DIRECT_TAB_IDS.forEach { viewId ->
            val directMatch = findNodeByViewId(root, viewId)
            if (directMatch != null) {
                return directMatch
            }
        }
        return findNodeContainingAny(root, INSTAGRAM_MESSAGES_TAB_SIGNALS)
    }

    private fun findYoutubeHomeTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeContainingAny(root, listOf("pivot_home", "tab_home", "navigation_bar_item_large_label_view"))
            ?.takeIf { nodeMatchesAny(it, YOUTUBE_HOME_TAB_SIGNALS) }
            ?: findNodeContainingAny(root, YOUTUBE_HOME_TAB_SIGNALS)
    }

    companion object {
        private const val TAG = "FixdSocialControl"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private val INSTAGRAM_DIRECT_TAB_IDS = listOf(
            "com.instagram.android:id/direct_tab",
            "com.instagram.android:id/messaging_tab"
        )
        private val INSTAGRAM_SEARCH_TAB_IDS = listOf(
            "com.instagram.android:id/search_tab",
            "com.instagram.android:id/explore_tab"
        )
        private const val INSTAGRAM_REEL_GRID_PLAY_COUNT_ID = "com.instagram.android:id/preview_clip_play_count"
        private const val INSTAGRAM_REDIRECT_COOLDOWN_MS = 900L
        private const val YOUTUBE_REDIRECT_COOLDOWN_MS = 900L
        private const val INSTAGRAM_FOREGROUND_WATCHDOG_MS = 250L
        private const val YOUTUBE_FOREGROUND_WATCHDOG_MS = 350L
        private val INSTAGRAM_SEARCH_TAB_SIGNALS = listOf(
            "search",
            "search and explore",
            "explore",
            "discover"
        )
        private val INSTAGRAM_MESSAGES_TAB_SIGNALS = listOf(
            "direct",
            "messages",
            "messenger"
        )
        private val INSTAGRAM_REEL_VIEWER_STRONG_SIGNALS = listOf(
            "reel_viewer",
            "clips_viewer",
            "clips viewer",
            "reels viewer",
            "reel_player",
            "reel player",
            "reel_watch",
            "reel watch"
        )
        private val INSTAGRAM_REEL_VIEWER_CHROME_SIGNALS = listOf(
            "like",
            "comment",
            "share",
            "send",
            "more options",
            "audio",
            "follow"
        )
        private val INSTAGRAM_REEL_MEDIA_SIGNALS = listOf(
            "reel by",
            "reel audio",
            "reels audio",
            "watch and browse reels",
            "send reel",
            "share reel",
            "comment on reel"
        )
        private val INSTAGRAM_REEL_GRID_PREVIEW_SIGNALS = listOf(
            "preview_clip_play_count",
            "image_preview",
            "reel by"
        )
        private val YOUTUBE_HOME_TAB_SIGNALS = listOf(
            "home",
            "pivot_home",
            "tab_home"
        )
        private val YOUTUBE_SHORTS_TAB_SIGNALS = listOf(
            "shorts",
            "pivot_shorts",
            "tab_shorts"
        )
        private val YOUTUBE_SHORTS_VIEWER_SIGNALS = listOf(
            "shorts_player",
            "shorts player",
            "reel_player",
            "reel player",
            "reel_watch",
            "reel watch",
            "reel_watch_fragment",
            "reel_player_view",
            "shorts video",
            "watch shorts",
            "shorts comment",
            "shorts sound",
            "shorts remix",
            "use this sound",
            "remix this short",
            "shorts creation"
        )
        private val YOUTUBE_SHORTS_EVENT_SIGNALS = listOf(
            "shorts",
            "watch shorts",
            "remix this short",
            "use this sound"
        )

        private val TRANSIENT_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.example.fixd"
        )
    }

    private fun isSocialTargetPackage(packageName: String): Boolean {
        return packageName.contains("instagram", ignoreCase = true) ||
            packageName.contains("youtube", ignoreCase = true)
    }

    private fun isTransientOverlayPackage(packageName: String): Boolean {
        return packageName in TRANSIENT_OVERLAY_PACKAGES
    }
}
