package com.example.fixd

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

class SocialQuickSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        val targetApp = intent.getStringExtra(SocialControlManager.EXTRA_TARGET_APP).orEmpty()

        setContent {
            FixdComposeTheme {
                SocialQuickSettingsScreen(
                    targetApp = targetApp,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SocialQuickSettingsScreen(
    targetApp: String,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var settings by remember { mutableStateOf(SocialControlPreferences.load(context)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val user = auth.currentUser ?: return@LaunchedEffect
        UserSocialControlRepository.getSettings(
            userId = user.uid,
            onSuccess = {
                settings = it
                SocialControlPreferences.save(context, it)
                SocialControlManager.refreshOverlay(context)
                errorText = null
            },
            onFailure = {
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    fun updateSettings(transform: (SocialControlSettings) -> SocialControlSettings) {
        val updated = transform(settings)
        settings = updated
        SocialControlPreferences.save(context, updated)
        SocialControlManager.refreshOverlay(context)
        val user = auth.currentUser ?: return
        UserSocialControlRepository.saveSettings(
            userId = user.uid,
            settings = updated,
            onSuccess = { errorText = null },
            onFailure = {
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    val appContext = remember(targetApp) { socialTargetForPackage(targetApp) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = appContext.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = appContext.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!errorText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                SocialQuickSettingToggle(
                    title = "Enable device control",
                    checked = settings.appControlEnabled
                ) {
                    updateSettings { current -> current.copy(appControlEnabled = it) }
                }
                when (appContext.kind) {
                    SocialTargetKind.INSTAGRAM -> {
                        SocialQuickSettingToggle(
                            title = "Disable Reels",
                            checked = settings.instagramBlockReels
                        ) {
                            updateSettings { current -> current.copy(instagramBlockReels = it) }
                        }
                        Text(
                            text = "Feed browsing stays allowed. Reels opened from the feed are sent to Messages, and reels opened from chats are closed back to the same chat thread.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SocialTargetKind.YOUTUBE -> {
                        SocialQuickSettingToggle(
                            title = "Disable Shorts",
                            checked = settings.youtubeBlockShorts
                        ) {
                            updateSettings { current -> current.copy(youtubeBlockShorts = it) }
                        }
                        Text(
                            text = "Blocked Shorts tabs and opened Shorts are sent back to YouTube Home. Normal Home scrolling is allowed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SocialTargetKind.GENERIC -> {
                        SocialQuickSettingToggle(
                            title = "Instagram: disable Reels",
                            checked = settings.instagramBlockReels
                        ) {
                            updateSettings { current -> current.copy(instagramBlockReels = it) }
                        }
                        Text(
                            text = "Instagram feed browsing stays allowed. Feed-opened reels redirect to Messages, while chat-opened reels are closed back to the chat thread.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SocialQuickSettingToggle(
                            title = "YouTube: disable Shorts",
                            checked = settings.youtubeBlockShorts
                        ) {
                            updateSettings { current -> current.copy(youtubeBlockShorts = it) }
                        }
                        Text(
                            text = "YouTube blocked Shorts redirect back to Home while normal Home scrolling stays allowed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun SocialQuickSettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

private enum class SocialTargetKind {
    INSTAGRAM,
    YOUTUBE,
    GENERIC
}

private data class SocialQuickContext(
    val kind: SocialTargetKind,
    val title: String,
    val body: String
)

private fun socialTargetForPackage(packageName: String): SocialQuickContext {
    val normalized = packageName.lowercase()
    return when {
        normalized.contains("instagram") -> SocialQuickContext(
            kind = SocialTargetKind.INSTAGRAM,
            title = "Instagram controls",
            body = "Quick settings for Instagram only."
        )
        normalized.contains("youtube") -> SocialQuickContext(
            kind = SocialTargetKind.YOUTUBE,
            title = "YouTube controls",
            body = "Quick settings for YouTube only."
        )
        else -> SocialQuickContext(
            kind = SocialTargetKind.GENERIC,
            title = "Social controls",
            body = "Quick settings for Instagram and YouTube."
        )
    }
}
