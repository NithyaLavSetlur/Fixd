package com.example.fixd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeRoute(
    selectedProblems: List<ProblemArea>,
    onOpenArea: (ProblemArea) -> Unit,
    onOpenTabDisplayChooser: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(18.dp)) }

        if (selectedProblems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.home_empty_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onOpenTabDisplayChooser,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(id = R.string.home_choose_tabs_button))
                        }
                    }
                }
            }
        } else {
            items(selectedProblems, key = { it.name }) { area ->
                HomeFocusCard(area = area, onClick = { onOpenArea(area) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HomeFocusCard(
    area: ProblemArea,
    onClick: () -> Unit
) {
    val palette = ThemePaletteManager.currentPalette(LocalContext.current)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(palette.primary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = area.iconRes),
                    contentDescription = null,
                    tint = Color(ThemePaletteManager.readableTextColorOn(palette.primary, palette)),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f)
            ) {
                Text(
                    text = stringResource(id = area.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = area.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PremiumRoute() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentProfile by remember { mutableStateOf(UserProfile()) }

    LaunchedEffect(Unit) {
        val user = auth.currentUser ?: return@LaunchedEffect
        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile -> currentProfile = profile },
            onFailure = { currentProfile = UserProfile() }
        )
    }

    val isPremium = currentProfile.isPremium
    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        if (isPremium) stringResource(R.string.premium_status_active)
                        else stringResource(R.string.premium_status_free)
                    )
                }
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.premium_feature_one),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.premium_feature_two), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.premium_feature_three), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.premium_placeholder_price),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (isPremium) R.string.premium_already_unlocked else R.string.premium_placeholder_button))
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.premium_placeholder_secondary))
            }
        }
    }
}

@Composable
fun ProblemAreaPlaceholderRoute(area: ProblemArea) {
    val palette = ThemePaletteManager.currentPalette(LocalContext.current)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(palette.primary), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = area.iconRes),
                contentDescription = null,
                tint = Color(ThemePaletteManager.readableTextColorOn(palette.primary, palette)),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(id = area.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(id = R.string.problem_placeholder_button))
        }
    }
}

@Composable
fun SocialMediaDistractionRoute() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var settings by remember { mutableStateOf(SocialControlPreferences.load(context)) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val overlayGranted = SocialControlManager.canDrawOverlays(context)

    LaunchedEffect(Unit) {
        val user = auth.currentUser ?: return@LaunchedEffect
        UserSocialControlRepository.getSettings(
            userId = user.uid,
            onSuccess = {
                settings = it
                SocialControlPreferences.save(context, it)
                if (it.floatingBubbleEnabled && overlayGranted) {
                    SocialControlManager.showFloatingBubble(context)
                } else {
                    SocialControlManager.hideFloatingBubble(context)
                }
                SocialControlManager.refreshOverlay(context)
                saveError = null
            },
            onFailure = {
                saveError = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    fun updateSettings(transform: (SocialControlSettings) -> SocialControlSettings) {
        val updated = transform(settings)
        settings = updated
        SocialControlPreferences.save(context, updated)
        if (updated.floatingBubbleEnabled && overlayGranted) {
            SocialControlManager.showFloatingBubble(context)
        } else {
            SocialControlManager.hideFloatingBubble(context)
        }
        SocialControlManager.refreshOverlay(context)
        val user = auth.currentUser ?: return
        UserSocialControlRepository.saveSettings(
            userId = user.uid,
            settings = updated,
            onSuccess = { saveError = null },
            onFailure = {
                saveError = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.social_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (!saveError.isNullOrBlank()) {
            item {
                Text(
                    text = saveError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.social_master_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = settings.appControlEnabled,
                            onCheckedChange = {
                                updateSettings { current -> current.copy(appControlEnabled = it) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                stringResource(
                                    if (settings.appControlEnabled) {
                                        R.string.social_status_ready
                                    } else {
                                        R.string.social_status_paused
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }

        item {
            SocialSettingsCard(
                title = stringResource(R.string.social_permissions_title),
                body = stringResource(R.string.social_permissions_body)
            ) {
                Text(
                    text = stringResource(
                        if (overlayGranted) {
                            R.string.social_overlay_permission_granted
                        } else {
                            R.string.social_overlay_permission_missing
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { context.startActivity(SocialControlManager.overlayPermissionIntent(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.social_overlay_permission_button))
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { context.startActivity(SocialControlManager.accessibilitySettingsIntent()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.social_accessibility_permission_button))
                }
            }
        }

        item {
            SocialSettingsCard(
                title = stringResource(R.string.social_instagram_title),
                body = stringResource(R.string.social_instagram_body)
            ) {
                SocialSettingRow(
                    title = stringResource(R.string.social_instagram_reels_title),
                    body = stringResource(R.string.social_instagram_reels_body),
                    checked = settings.instagramBlockReels,
                    enabled = settings.appControlEnabled
                ) {
                    updateSettings { current -> current.copy(instagramBlockReels = it) }
                }
            }
        }

        item {
            SocialSettingsCard(
                title = stringResource(R.string.social_youtube_title),
                body = stringResource(R.string.social_youtube_body)
            ) {
                SocialSettingRow(
                    title = stringResource(R.string.social_youtube_shorts_title),
                    body = stringResource(R.string.social_youtube_shorts_body),
                    checked = settings.youtubeBlockShorts,
                    enabled = settings.appControlEnabled
                ) {
                    updateSettings { current -> current.copy(youtubeBlockShorts = it) }
                }
            }
        }

        item {
            SocialSettingsCard(
                title = stringResource(R.string.social_bubble_title),
                body = stringResource(R.string.social_bubble_body)
            ) {
                SocialSettingRow(
                    title = stringResource(R.string.social_bubble_toggle_title),
                    body = stringResource(R.string.social_bubble_toggle_body),
                    checked = settings.floatingBubbleEnabled,
                    enabled = overlayGranted
                ) {
                    updateSettings { current -> current.copy(floatingBubbleEnabled = it) }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { SocialControlManager.showQuickSettings(context) },
                    enabled = overlayGranted && settings.floatingBubbleEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.social_bubble_open_button))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SocialSettingsCard(
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SocialSettingRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
}
