package com.example.fixd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun SettingsRoute(
    onTabOrderSaved: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var currentAppearance by remember { mutableStateOf(UserAppearanceSettings()) }
    var currentProfile by remember { mutableStateOf(UserProfile()) }
    var pendingSeedColor by remember { mutableStateOf(ThemePaletteManager.DEFAULT_SEED_COLOR) }
    var themeMode by remember { mutableStateOf(UserPreferences.THEME_SYSTEM) }
    val pendingTabOrder = remember { mutableStateListOf<ProblemArea>() }
    var showTabOrderEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = auth.currentUser ?: return@LaunchedEffect
        UserAppearanceRepository.getAppearance(
            userId = user.uid,
            onSuccess = { settings ->
                currentAppearance = settings
                themeMode = settings.themeMode
                pendingSeedColor = settings.themeSeedColor
            },
            onFailure = {
                themeMode = UserPreferences.THEME_SYSTEM
                pendingSeedColor = ThemePaletteManager.DEFAULT_SEED_COLOR
                toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready))
            }
        )

        UserProfileRepository.getEffectiveProfile(
            user = user,
            onSuccess = { profile ->
                currentProfile = profile
                pendingTabOrder.clear()
                pendingTabOrder += profile.selectedProblems.mapNotNull { ProblemArea.fromName(it) }
                showTabOrderEmpty = pendingTabOrder.isEmpty()
            },
            onFailure = {
                showTabOrderEmpty = true
                toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready))
            }
        )
    }

    fun updateThemeMode(newMode: String) {
        if (newMode == currentAppearance.themeMode) {
            themeMode = newMode
            return
        }
        val user = auth.currentUser ?: return
        themeMode = newMode
        UserAppearanceRepository.saveThemeMode(
            userId = user.uid,
            themeMode = newMode,
            onSuccess = {
                currentAppearance = currentAppearance.copy(themeMode = newMode)
                ThemePaletteManager.updateSettings(currentAppearance)
                UserPreferences.saveThemeMode(context, newMode)
                UserPreferences.applyThemeMode(newMode)
                (context as? android.app.Activity)?.recreate()
            },
            onFailure = {
                themeMode = currentAppearance.themeMode
                toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready))
            }
        )
    }

    fun applyPalette() {
        val userId = auth.currentUser?.uid ?: return
        UserAppearanceRepository.saveThemeSeedColor(
            userId = userId,
            themeSeedColor = pendingSeedColor,
            onSuccess = { saved ->
                currentAppearance = currentAppearance.copy(themeSeedColor = saved.themeSeedColor)
                ThemePaletteManager.updateSettings(currentAppearance)
                UserPreferences.saveThemeSeedColor(context, saved.themeSeedColor)
                (context as? android.app.Activity)?.recreate()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun saveTabOrder() {
        val userId = auth.currentUser?.uid ?: return
        val orderedTabs = pendingTabOrder.map { it.name }
        if (orderedTabs.isEmpty()) {
            toast(context, context.getString(R.string.settings_tab_order_empty))
            return
        }
        UserProfileRepository.saveProfile(
            userId = userId,
            profile = PremiumAccess.sanitizeProfile(currentProfile.copy(selectedProblems = orderedTabs)),
            onSuccess = {
                currentProfile = currentProfile.copy(selectedProblems = orderedTabs)
                toast(context, context.getString(R.string.settings_tab_order_saved))
                onTabOrderSaved()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    val palette = ThemePaletteManager.paletteFor(
        currentAppearance.copy(themeSeedColor = pendingSeedColor),
        UserPreferences.isDarkMode(context)
    )

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(modifier = Modifier.height(18.dp)) }
        item {
            SettingsCard(stringResource(R.string.settings_theme_title), stringResource(R.string.settings_theme_body)) {
                SettingsThemeOption(stringResource(R.string.settings_theme_system), themeMode == UserPreferences.THEME_SYSTEM) {
                    updateThemeMode(UserPreferences.THEME_SYSTEM)
                }
                SettingsThemeOption(stringResource(R.string.settings_theme_light), themeMode == UserPreferences.THEME_LIGHT) {
                    updateThemeMode(UserPreferences.THEME_LIGHT)
                }
                SettingsThemeOption(stringResource(R.string.settings_theme_dark), themeMode == UserPreferences.THEME_DARK) {
                    updateThemeMode(UserPreferences.THEME_DARK)
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_tab_order_title), stringResource(R.string.settings_tab_order_body)) {
                if (showTabOrderEmpty) {
                    Text(
                        text = stringResource(id = R.string.settings_tab_order_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pendingTabOrder.forEachIndexed { index, area ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Icon(
                                        painter = painterResource(id = area.iconRes),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .weight(1f)
                                    ) {
                                        Text(
                                            text = stringResource(id = area.titleRes),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(id = R.string.settings_tab_order_position, index + 1),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        enabled = index > 0,
                                        onClick = {
                                            val moved = pendingTabOrder.removeAt(index)
                                            pendingTabOrder.add(index - 1, moved)
                                        }
                                    ) { Text("Up") }
                                    TextButton(
                                        enabled = index < pendingTabOrder.lastIndex,
                                        onClick = {
                                            val moved = pendingTabOrder.removeAt(index)
                                            pendingTabOrder.add(index + 1, moved)
                                        }
                                    ) { Text("Down") }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { saveTabOrder() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pendingTabOrder.isNotEmpty()
                ) {
                    Text(stringResource(id = R.string.settings_tab_order_save))
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_palette_title), stringResource(R.string.settings_palette_body)) {
                ComposeColorWheel(
                    selectedColor = pendingSeedColor,
                    onColorChanged = { pendingSeedColor = it },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(
                        id = R.string.settings_palette_selected,
                        String.format("#%06X", 0xFFFFFF and pendingSeedColor)
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsPaletteSwatch(color = palette.primary, modifier = Modifier.weight(1f))
                    SettingsPaletteSwatch(color = palette.secondary, modifier = Modifier.weight(1f))
                    SettingsPaletteSwatch(color = palette.accent, modifier = Modifier.weight(1f))
                    SettingsPaletteSwatch(color = palette.card, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { applyPalette() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.settings_palette_apply))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SettingsPaletteSwatch(color: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(46.dp).background(Color(color), RoundedCornerShape(20.dp)))
}

@Composable
private fun ComposeColorWheel(
    selectedColor: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val wheelBitmap = remember(size) { buildColorWheelBitmap(size) }
    val hsv = remember(selectedColor) { FloatArray(3).apply { AndroidColor.colorToHSV(selectedColor, this) } }
    val hue = hsv[0]
    val saturation = hsv[1]
    val selectorColor = if (AndroidColor.luminance(selectedColor) < 0.4f) Color.White else Color.Black

    fun updateFromPoint(offset: Offset) {
        val radius = min(size.width, size.height) / 2f
        if (radius <= 0f) return
        val dx = offset.x - size.width / 2f
        val dy = offset.y - size.height / 2f
        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
        val nextHue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
        val nextSaturation = (distance / radius).coerceIn(0f, 1f)
        onColorChanged(AndroidColor.HSVToColor(floatArrayOf(nextHue, nextSaturation, 1f)))
    }

    Canvas(
        modifier = modifier
            .size(240.dp)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { updateFromPoint(it) },
                    onDrag = { change, _ -> updateFromPoint(change.position) }
                )
            }
    ) {
        wheelBitmap?.let { drawImage(it) }
        val radius = min(size.width, size.height) / 2f
        val selectorRadius = radius * saturation
        val angle = Math.toRadians(hue.toDouble())
        val cx = size.width / 2f + (selectorRadius * cos(angle)).toFloat()
        val cy = size.height / 2f + (selectorRadius * sin(angle)).toFloat()
        drawCircle(color = Color.Black.copy(alpha = 0.32f), radius = 20.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = selectorColor, radius = 16.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun buildColorWheelBitmap(size: IntSize): ImageBitmap? {
    if (size.width <= 0 || size.height <= 0) return null
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val radius = min(size.width, size.height) / 2f
    for (x in 0 until size.width) {
        for (y in 0 until size.height) {
            val dx = x - size.width / 2f
            val dy = y - size.height / 2f
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > radius) {
                bitmap.setPixel(x, y, AndroidColor.TRANSPARENT)
            } else {
                val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                val saturation = (distance / radius).coerceIn(0f, 1f)
                bitmap.setPixel(x, y, AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f)))
            }
        }
    }
    return bitmap.asImageBitmap()
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
