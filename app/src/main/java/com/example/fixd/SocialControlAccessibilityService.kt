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
            hideBlocker()
            return
        }

        val effectivePackage = when {
            isSocialTargetPackage(packageName) -> packageName
            isTransientOverlayPackage(packageName) -> currentForegroundPackage.orEmpty()
            else -> packageName
        }

        when {
            effectivePackage.contains("youtube", ignoreCase = true) -> handleYoutube(settings, event)
            effectivePackage.contains("instagram", ignoreCase = true) -> handleInstagram(settings, event)
            else -> hideBlocker()
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
        val root = findRootForPackage(INSTAGRAM_PACKAGE, event?.source)
        if (root == null) {
            Log.d(TAG, "Instagram check skipped: no Instagram root found; scheduling recheck")
            scheduleInstagramRecheck()
            return
        }
        clearInstagramRechecks()
        val onReelsTab = settings.instagramBlockReels && isInstagramTabSelected(root, INSTAGRAM_REELS_TAB_ID)
        val inMessages = isInstagramTabSelected(root, INSTAGRAM_DIRECT_TAB_ID)
        val inReelViewer = settings.instagramBlockReels && isInstagramReelViewer(root, event)
        val inChatReelViewer = inReelViewer && isInstagramMessagesContext(root)

        Log.d(
            TAG,
            "Instagram check: onReelsTab=$onReelsTab inReelViewer=$inReelViewer inChatReelViewer=$inChatReelViewer inMessages=$inMessages"
        )

        when {
            inChatReelViewer -> {
                redirectInstagramBackToChat()
                hideBlocker()
            }
            inReelViewer -> {
                redirectInstagramToMessages(root)
                hideBlocker()
            }
            inMessages -> {
                clearInstagramRedirects()
                hideBlocker()
            }
            onReelsTab -> {
                redirectInstagramToMessages(root)
                hideBlocker()
            }
            else -> hideBlocker()
        }
    }

    private fun redirectInstagramToMessages(root: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInstagramRedirectAt < INSTAGRAM_REDIRECT_COOLDOWN_MS) {
            return true
        }
        clearInstagramRedirects()
        lastInstagramRedirectAt = now
        val immediateRedirect = runInstagramMessagesRedirectAttempt(root)
        scheduleInstagramRedirectAttempts()
        hideBlocker()
        return immediateRedirect
    }

    private fun redirectInstagramBackToChat(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInstagramRedirectAt < INSTAGRAM_REDIRECT_COOLDOWN_MS) {
            return true
        }
        clearInstagramRedirects()
        clearInstagramRechecks()
        lastInstagramRedirectAt = now
        val backedOut = performGlobalAction(GLOBAL_ACTION_BACK)
        if (backedOut) {
            Log.d(TAG, "Instagram redirect: backed out of reel to chat")
            scheduleInstagramChatReturnAttempts()
        }
        hideBlocker()
        return backedOut
    }

    private fun scheduleInstagramRedirectAttempts() {
        val attempts = listOf(90L, 220L, 420L, 760L, 1150L)
        attempts.forEach { delayMs ->
            val runnable = Runnable {
                val root = findRootForPackage(INSTAGRAM_PACKAGE)
                if (root == null) return@Runnable
                runInstagramMessagesRedirectAttempt(root)
            }
            pendingInstagramRedirects += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun scheduleInstagramChatReturnAttempts() {
        val attempts = listOf(140L, 320L, 620L)
        attempts.forEach { delayMs ->
            val runnable = Runnable {
                val root = findRootForPackage(INSTAGRAM_PACKAGE) ?: return@Runnable
                if (isInstagramReelViewer(root, null)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            pendingInstagramRedirects += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun runInstagramMessagesRedirectAttempt(root: AccessibilityNodeInfo): Boolean {
        if (isInstagramTabSelected(root, INSTAGRAM_DIRECT_TAB_ID)) {
            clearInstagramRedirects()
            return true
        }

        val clickedDirect = clickNode(findNodeByViewId(root, INSTAGRAM_DIRECT_TAB_ID))
        if (clickedDirect) {
            Log.d(TAG, "Instagram redirect: clicked Messages tab")
            return true
        }

        return false
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
        val signalText = buildString {
            append(flattenNodeText(root))
            append(' ')
            append(event?.text?.joinToString(" ").orEmpty())
            append(' ')
            append(event?.contentDescription?.toString().orEmpty())
            append(' ')
            append(event?.className?.toString().orEmpty())
        }
        val onFeedTab = isInstagramTabSelected(root, INSTAGRAM_HOME_TAB_ID)
        val inMessages = isInstagramMessagesContext(root)

        val hasStrongViewerSignal = INSTAGRAM_REEL_VIEWER_STRONG_SIGNALS.any { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        }
        if (hasStrongViewerSignal) {
            return if (onFeedTab && !inMessages) {
                INSTAGRAM_FEED_REEL_VIEWER_CONFIRM_SIGNALS.any { keyword ->
                    signalText.contains(keyword, ignoreCase = true)
                }
            } else {
                true
            }
        }

        val weakSignalMatches = INSTAGRAM_REEL_VIEWER_WEAK_SIGNALS.count { keyword ->
            signalText.contains(keyword, ignoreCase = true)
        }
        return if (onFeedTab && !inMessages) weakSignalMatches >= 3 else weakSignalMatches >= 2
    }

    private fun isInstagramMessagesContext(root: AccessibilityNodeInfo): Boolean {
        return isInstagramTabSelected(root, INSTAGRAM_DIRECT_TAB_ID)
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

    private fun isInstagramTabSelected(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return findNodeByViewId(root, viewId)?.isSelected == true
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

    private fun findYoutubeHomeTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeContainingAny(root, listOf("pivot_home", "tab_home", "navigation_bar_item_large_label_view"))
            ?.takeIf { nodeMatchesAny(it, YOUTUBE_HOME_TAB_SIGNALS) }
            ?: findNodeContainingAny(root, YOUTUBE_HOME_TAB_SIGNALS)
    }

    companion object {
        private const val TAG = "FixdSocialControl"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val INSTAGRAM_HOME_TAB_ID = "com.instagram.android:id/feed_tab"
        private const val INSTAGRAM_DIRECT_TAB_ID = "com.instagram.android:id/direct_tab"
        private const val INSTAGRAM_REELS_TAB_ID = "com.instagram.android:id/clips_tab"
        private const val INSTAGRAM_REDIRECT_COOLDOWN_MS = 900L
        private const val YOUTUBE_REDIRECT_COOLDOWN_MS = 900L
        private val INSTAGRAM_REEL_VIEWER_STRONG_SIGNALS = listOf(
            "reel_viewer",
            "clips_viewer",
            "clips viewer",
            "reels viewer"
        )
        private val INSTAGRAM_FEED_REEL_VIEWER_CONFIRM_SIGNALS = listOf(
            "reel_viewer",
            "clips_viewer",
            "clips viewer",
            "reels viewer",
            "reel by",
            "reel audio",
            "reels audio"
        )
        private val INSTAGRAM_REEL_VIEWER_WEAK_SIGNALS = listOf(
            "reel by",
            "send reel",
            "share reel",
            "reel audio",
            "reels audio"
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
