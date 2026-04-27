package com.example.fixd

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CountdownRoute() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val cachedCountdowns = remember { CountdownLocalCache.getCountdowns(context) }
    var countdowns by remember { mutableStateOf(cachedCountdowns) }
    var loading by remember { mutableStateOf(cachedCountdowns.isEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var editingCountdown by remember { mutableStateOf<CountdownEntry?>(null) }
    var creatingCountdown by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun syncWidget(items: List<CountdownEntry>) {
        CountdownLocalCache.saveCountdowns(context, items)
        CountdownWidgetCache.save(context, items)
        CountdownWidgetUpdater.updateAll(context)
    }

    fun loadCountdowns() {
        val user = auth.currentUser ?: return
        if (countdowns.isEmpty()) {
            loading = true
        }
        CountdownRepository.getCountdowns(
            userId = user.uid,
            onSuccess = {
                countdowns = it
                loading = false
                errorText = null
                syncWidget(it)
            },
            onFailure = {
                loading = false
                errorText = it.localizedMessage ?: context.getString(R.string.firebase_not_ready)
            }
        )
    }

    LaunchedEffect(Unit) {
        loadCountdowns()
    }

    LaunchedEffect(countdowns.isNotEmpty()) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun saveCountdown(existing: CountdownEntry?, title: String, targetAt: Long, notifyAt: Long) {
        if (title.isBlank()) {
            toast(context, context.getString(R.string.countdown_invalid_name))
            return
        }
        if (targetAt <= System.currentTimeMillis()) {
            toast(context, context.getString(R.string.countdown_target_past))
            return
        }
        if (notifyAt <= System.currentTimeMillis()) {
            toast(context, context.getString(R.string.countdown_notification_past))
            return
        }
        if (notifyAt > targetAt) {
            toast(context, context.getString(R.string.countdown_notification_after_target))
            return
        }
        val user = auth.currentUser ?: return
        val countdown = (existing ?: CountdownEntry(createdAt = System.currentTimeMillis()))
            .copy(title = title, targetAt = targetAt, notifyAt = notifyAt)
        CountdownRepository.saveCountdown(
            userId = user.uid,
            countdown = countdown,
            onSuccess = {
                CountdownReminderScheduler.cancel(context, it.id)
                CountdownReminderScheduler.schedule(context, it)
                toast(
                    context,
                    context.getString(if (existing == null) R.string.countdown_saved else R.string.countdown_updated)
                )
                editingCountdown = null
                creatingCountdown = false
                loadCountdowns()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    fun deleteCountdown(countdown: CountdownEntry) {
        val user = auth.currentUser ?: return
        CountdownRepository.deleteCountdown(
            userId = user.uid,
            countdownId = countdown.id,
            onSuccess = {
                CountdownReminderScheduler.cancel(context, countdown.id)
                toast(context, context.getString(R.string.countdown_deleted))
                editingCountdown = null
                creatingCountdown = false
                loadCountdowns()
            },
            onFailure = { toast(context, it.localizedMessage ?: context.getString(R.string.firebase_not_ready)) }
        )
    }

    val sortedCountdowns = remember(countdowns, now) {
        countdowns.sortedWith(
            compareBy<CountdownEntry> { it.targetAt <= now }
                .thenBy { it.targetAt }
                .thenBy { it.title.lowercase() }
        )
    }

    if (creatingCountdown || editingCountdown != null) {
        CountdownEditorDialog(
            initialCountdown = editingCountdown,
            onDismiss = {
                creatingCountdown = false
                editingCountdown = null
            },
            onDelete = editingCountdown?.let { current -> { deleteCountdown(current) } },
            onSave = { title, targetAt, notifyAt -> saveCountdown(editingCountdown, title, targetAt, notifyAt) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(18.dp)) }
        item {
            Button(
                onClick = { creatingCountdown = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.countdown_add))
            }
        }
        item {
            CountdownHeaderCard(activeCount = countdowns.count { it.targetAt > now })
        }
        when {
            loading -> item { CountdownMessageCard(text = context.getString(R.string.challenge_loading)) }
            !errorText.isNullOrBlank() -> item { CountdownMessageCard(text = errorText.orEmpty(), isError = true) }
            sortedCountdowns.isEmpty() -> item { CountdownMessageCard(text = context.getString(R.string.countdown_empty)) }
            else -> items(sortedCountdowns, key = { it.id }) { countdown ->
                CountdownCard(
                    countdown = countdown,
                    now = now,
                    onClick = { editingCountdown = countdown }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun CountdownHeaderCard(activeCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.countdown_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (activeCount == 1) "1 live countdown" else "$activeCount live countdowns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CountdownCard(
    countdown: CountdownEntry,
    now: Long,
    onClick: () -> Unit
) {
    val isExpired = countdown.targetAt <= now
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
    }
    val targetText = remember(countdown.targetAt) {
        Instant.ofEpochMilli(countdown.targetAt)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
    val notifyText = remember(countdown.notifyAt) {
        Instant.ofEpochMilli(countdown.notifyAt)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.86f, stiffness = 480f))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isExpired) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isExpired) "Done" else "Live",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = countdown.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(
                targetState = formatRemaining(countdown.targetAt - now),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "countdown_remaining"
            ) { remaining ->
                Text(
                    text = if (isExpired) stringResource(R.string.countdown_expired) else remaining,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.countdown_target_preview, targetText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.countdown_notification_preview, notifyText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountdownEditorDialog(
    initialCountdown: CountdownEntry?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Long, Long) -> Unit
) {
    val initialDateTime = remember(initialCountdown?.id) {
        Instant.ofEpochMilli(initialCountdown?.targetAt ?: (System.currentTimeMillis() + 86_400_000L))
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
    val initialNotifyDateTime = remember(initialCountdown?.id) {
        Instant.ofEpochMilli(initialCountdown?.notifyAt ?: (initialCountdown?.targetAt ?: (System.currentTimeMillis() + 86_400_000L)))
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
    var title by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialCountdown?.title.orEmpty()) }
    var year by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialDateTime.year.toString()) }
    var month by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialDateTime.monthValue.toString().padStart(2, '0')) }
    var day by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialDateTime.dayOfMonth.toString().padStart(2, '0')) }
    var hour by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialDateTime.hour.toString().padStart(2, '0')) }
    var minute by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialDateTime.minute.toString().padStart(2, '0')) }
    var second by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialDateTime.second.toString().padStart(2, '0')) }
    var notifyYear by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialNotifyDateTime.year.toString()) }
    var notifyMonth by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialNotifyDateTime.monthValue.toString().padStart(2, '0')) }
    var notifyDay by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialNotifyDateTime.dayOfMonth.toString().padStart(2, '0')) }
    var notifyHour by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialNotifyDateTime.hour.toString().padStart(2, '0')) }
    var notifyMinute by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialNotifyDateTime.minute.toString().padStart(2, '0')) }
    var notifySecond by rememberSaveable(initialCountdown?.id) { mutableStateOf(initialNotifyDateTime.second.toString().padStart(2, '0')) }

    val parsedTarget = remember(year, month, day, hour, minute, second) {
        runCatching {
            LocalDateTime.of(
                year.toInt(),
                month.toInt(),
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt()
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
    val parsedNotifyAt = remember(notifyYear, notifyMonth, notifyDay, notifyHour, notifyMinute, notifySecond) {
        runCatching {
            LocalDateTime.of(
                notifyYear.toInt(),
                notifyMonth.toInt(),
                notifyDay.toInt(),
                notifyHour.toInt(),
                notifyMinute.toInt(),
                notifySecond.toInt()
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
    val hasChanges =
        title.trim() != initialCountdown?.title.orEmpty() ||
            (parsedTarget != null && parsedTarget != initialCountdown?.targetAt) ||
            (parsedNotifyAt != null && parsedNotifyAt != (initialCountdown?.notifyAt ?: initialCountdown?.targetAt))
    val canSave = title.trim().isNotBlank() &&
        parsedTarget != null &&
        parsedNotifyAt != null &&
        parsedTarget > System.currentTimeMillis() &&
        parsedNotifyAt > System.currentTimeMillis() &&
        parsedNotifyAt <= parsedTarget &&
        hasChanges
    val targetPreview = parsedTarget?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofPattern("EEE, d MMM yyyy - hh:mm:ss a", Locale.getDefault())
            )
    }
    val notifyPreview = parsedNotifyAt?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofPattern("EEE, d MMM yyyy - hh:mm:ss a", Locale.getDefault())
            )
    }

    FixdDialog(
        title = stringResource(if (initialCountdown == null) R.string.countdown_editor_title else R.string.countdown_editor_edit_title),
        onDismiss = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.countdown_delete))
                    }
                }
                Button(
                    onClick = {
                        val safeTarget = parsedTarget
                        val safeNotifyAt = parsedNotifyAt
                        if (safeTarget != null && safeNotifyAt != null) {
                            onSave(title.trim(), safeTarget, safeNotifyAt)
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.countdown_save))
                }
            }
        },
        secondaryAction = null
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.countdown_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.countdown_date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CountdownNumberField(value = year, onValueChange = { year = it }, label = stringResource(R.string.countdown_year), weightFraction = 0.48f)
                CountdownNumberField(value = month, onValueChange = { month = it }, label = stringResource(R.string.countdown_month), weightFraction = 0.22f)
                CountdownNumberField(value = day, onValueChange = { day = it }, label = stringResource(R.string.countdown_day), weightFraction = 0.22f)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.countdown_time),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CountdownNumberField(value = hour, onValueChange = { hour = it }, label = stringResource(R.string.countdown_hour), weightFraction = 0.3f)
                CountdownNumberField(value = minute, onValueChange = { minute = it }, label = stringResource(R.string.countdown_minute), weightFraction = 0.3f)
                CountdownNumberField(value = second, onValueChange = { second = it }, label = stringResource(R.string.countdown_second), weightFraction = 0.3f)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = targetPreview?.let { stringResource(R.string.countdown_target_preview, it) }
                            ?: stringResource(R.string.countdown_invalid_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.countdown_notification_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.countdown_notification_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CountdownNumberField(value = notifyYear, onValueChange = { notifyYear = it }, label = stringResource(R.string.countdown_year), weightFraction = 0.48f)
                CountdownNumberField(value = notifyMonth, onValueChange = { notifyMonth = it }, label = stringResource(R.string.countdown_month), weightFraction = 0.22f)
                CountdownNumberField(value = notifyDay, onValueChange = { notifyDay = it }, label = stringResource(R.string.countdown_day), weightFraction = 0.22f)
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CountdownNumberField(value = notifyHour, onValueChange = { notifyHour = it }, label = stringResource(R.string.countdown_hour), weightFraction = 0.3f)
                CountdownNumberField(value = notifyMinute, onValueChange = { notifyMinute = it }, label = stringResource(R.string.countdown_minute), weightFraction = 0.3f)
                CountdownNumberField(value = notifySecond, onValueChange = { notifySecond = it }, label = stringResource(R.string.countdown_second), weightFraction = 0.3f)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = notifyPreview?.let { stringResource(R.string.countdown_notification_preview, it) }
                            ?: stringResource(R.string.countdown_invalid_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    weightFraction: Float
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(weightFraction)
    )
}

@Composable
private fun CountdownMessageCard(text: String, isError: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatRemaining(durationMillis: Long): String {
    if (durationMillis <= 0L) return "00d 00h 00m 00s"
    val totalSeconds = durationMillis / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02dd %02dh %02dm %02ds", days, hours, minutes, seconds)
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
