@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.fixd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

class ChallengeSceneryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixdComposeTheme {
                ChallengeSceneryFullScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun ChallengeSceneryFullScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val userId = auth.currentUser?.uid
    var snapshot by remember(userId) { mutableStateOf(ChallengeSnapshot()) }
    var loading by remember(userId) { mutableStateOf(true) }
    var errorText by remember(userId) { mutableStateOf<String?>(null) }
    var draftSceneryId by remember(userId) { mutableStateOf("sunny_meadow") }
    var draftAccessoryIds by remember(userId) { mutableStateOf<List<String>>(emptyList()) }
    var draftPlacements by remember(userId) { mutableStateOf<Map<String, ChallengeScenePlacement>>(emptyMap()) }

    fun loadSnapshot() {
        val safeUserId = userId
        if (safeUserId == null) {
            loading = false
            errorText = context.getString(R.string.firebase_not_ready)
            return
        }
        ChallengeRepository.loadSnapshot(
            userId = safeUserId,
            onSuccess = {
                snapshot = it
                draftSceneryId = it.displaySettings.equippedSceneryId
                draftAccessoryIds = it.displaySettings.equippedAccessoryIds
                draftPlacements = it.displaySettings.scenePlacements
                loading = false
                errorText = null
            },
            onFailure = {
                loading = false
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    fun saveScene() {
        val safeUserId = userId ?: return
        ChallengeRepository.equipScenery(
            userId = safeUserId,
            sceneryId = draftSceneryId,
            onSuccess = {
                ChallengeRepository.saveEquippedAccessories(
                    userId = safeUserId,
                    accessoryIds = draftAccessoryIds,
                    onSuccess = {
                        ChallengeRepository.saveScenePlacements(
                            userId = safeUserId,
                            placements = draftPlacements,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.challenge_scene_saved), Toast.LENGTH_SHORT).show()
                                loadSnapshot()
                            },
                            onFailure = { Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready), Toast.LENGTH_SHORT).show() }
                        )
                    },
                    onFailure = { Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready), Toast.LENGTH_SHORT).show() }
                )
            },
            onFailure = { Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready), Toast.LENGTH_SHORT).show() }
        )
    }

    LaunchedEffect(userId) {
        loadSnapshot()
    }

    val ownedSceneryIds = remember(snapshot.sceneryUnlocks) {
        (snapshot.sceneryUnlocks.map { it.sceneryId } + "sunny_meadow").toSet()
    }
    val ownedAccessoryIds = remember(snapshot.accessoryUnlocks) {
        snapshot.accessoryUnlocks.map { it.accessoryId }.toSet()
    }
    val scenery = ChallengeGameEngine.sceneryCatalog.firstOrNull { it.id == draftSceneryId }
        ?: ChallengeGameEngine.sceneryCatalog.first { it.id == "sunny_meadow" }
    val equippedAccessories = ChallengeGameEngine.accessoryCatalog.filter { draftAccessoryIds.contains(it.id) }
    val figures = remember(snapshot.unlocks) {
        snapshot.unlocks.mapNotNull { unlock -> ChallengeGameEngine.shopCatalog.firstOrNull { it.id == unlock.figureId } }
    }
    val objects = remember(figures, equippedAccessories) { challengeSceneObjects(figures, equippedAccessories) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            when {
                loading -> Text(stringResource(R.string.challenge_loading), color = MaterialTheme.colorScheme.onBackground)
                !errorText.isNullOrBlank() -> Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.error)
                else -> ChallengeInteractiveScenery(
                    scenery = scenery,
                    objects = objects,
                    placements = draftPlacements,
                    editable = true,
                    modifier = Modifier.fillMaxWidth(),
                    onPlacementsChanged = { draftPlacements = it }
                )
            }
        }
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.challenge_scene_fullscreen_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.challenge_scene_fullscreen_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.challenge_gallery_count, figures.size)) })
            Text(stringResource(R.string.challenge_scene_scenery_picker), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeGameEngine.sceneryCatalog.filter { ownedSceneryIds.contains(it.id) }.forEach { option ->
                    FilterChip(
                        selected = option.id == draftSceneryId,
                        onClick = { draftSceneryId = option.id },
                        label = { Text("${option.accentEmoji} ${option.name}") }
                    )
                }
            }
            Text(stringResource(R.string.challenge_scene_accessory_picker), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeGameEngine.accessoryCatalog.filter { ownedAccessoryIds.contains(it.id) }.forEach { accessory ->
                    val selected = draftAccessoryIds.contains(accessory.id)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            draftAccessoryIds = if (selected) {
                                draftAccessoryIds - accessory.id
                            } else if (draftAccessoryIds.size < ChallengeGameEngine.maxEquippedAccessories()) {
                                draftAccessoryIds + accessory.id
                            } else {
                                Toast.makeText(context, context.getString(R.string.challenge_accessory_limit, ChallengeGameEngine.maxEquippedAccessories()), Toast.LENGTH_SHORT).show()
                                draftAccessoryIds
                            }
                        },
                        label = { Text("${accessory.emoji} ${accessory.name}") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = ::saveScene, modifier = Modifier.fillMaxWidth(), enabled = !loading && errorText.isNullOrBlank()) {
                Text(stringResource(R.string.challenge_scene_save))
            }
            OutlinedButton(
                onClick = {
                    draftPlacements = emptyMap()
                    Toast.makeText(context, context.getString(R.string.challenge_scene_reset), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.challenge_scene_reset_button))
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.challenge_scene_close))
            }
        }
    }
}
