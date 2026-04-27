package com.example.fixd

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WakeAlarmEditorDialog(
    initialAlarm: WakeAlarm?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (name: String, hour: Int, minute: Int, repeatDays: List<Int>) -> Unit
) {
    var name by rememberSaveable(initialAlarm?.id) { mutableStateOf(initialAlarm?.name.orEmpty()) }
    var selectedDays by rememberSaveable(initialAlarm?.id) {
        mutableStateOf((initialAlarm?.repeatDays ?: listOf(2, 3, 4, 5, 6)).sorted().distinct())
    }
    val initialHour = initialAlarm?.hour ?: 7
    val initialMinute = initialAlarm?.minute ?: 0
    val initialDays = (initialAlarm?.repeatDays ?: listOf(2, 3, 4, 5, 6)).sorted().distinct()
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    val trimmedName = name.trim()
    val hasChanges =
        trimmedName != initialAlarm?.name.orEmpty() ||
            timeState.hour != initialHour ||
            timeState.minute != initialMinute ||
            selectedDays.sorted() != initialDays
    val canSave = trimmedName.isNotBlank() && selectedDays.isNotEmpty() && hasChanges

    FixdDialog(
        title = stringResource(
            if (initialAlarm == null) R.string.wake_editor_title else R.string.wake_editor_edit_title
        ),
        onDismiss = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.wake_editor_delete))
                    }
                }
                Button(
                    enabled = canSave,
                    onClick = {
                        onSave(
                            trimmedName,
                            timeState.hour,
                            timeState.minute,
                            selectedDays.sorted()
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.wake_save_short))
                }
            }
        },
        secondaryAction = null
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.wake_alarm_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.wake_picker_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                TimePicker(
                    state = timeState,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.82f
                        scaleY = 0.82f
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.wake_repeat_days),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WakeDayOptions.forEach { day ->
                    FilterChip(
                        selected = day.dayNumber in selectedDays,
                        onClick = {
                            selectedDays = if (day.dayNumber in selectedDays) {
                                selectedDays - day.dayNumber
                            } else {
                                (selectedDays + day.dayNumber).sorted()
                            }
                        },
                        label = { Text(stringResource(day.labelRes)) }
                    )
                }
            }
        }
    }
}

@Composable
fun WakeSubmissionDetailDialog(
    submission: WakeSubmission,
    onDismiss: () -> Unit,
    onSetWakeStatus: (String) -> Unit
) {
    val context = LocalContext.current
    var wakeStatus by remember(submission.id) { mutableStateOf(submission.wakeStatus) }
    val bitmap by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, submission.imagePath) {
        value = loadWakeSubmissionBitmap(submission.imagePath)
    }
    val loadedBitmap = bitmap
    val canSave = wakeStatus != submission.wakeStatus

    FixdDialog(
        title = stringResource(R.string.wake_history_detail_title),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSetWakeStatus(wakeStatus) }
            ) {
                Text(stringResource(R.string.challenge_save_changes))
            }
        }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Text(
                text = WakeSubmissionUi.formatDateTime(WakeSubmissionUi.primaryTimestamp(submission)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.getString(
                    R.string.wake_history_detail_meta,
                    WakeSubmissionUi.typeLabel(context, submission),
                    WakeSubmissionUi.formatAlarmTime(submission.alarmHour, submission.alarmMinute),
                    WakeSubmissionUi.formatDuration(context, submission.responseDurationMs),
                    submission.verdict.ifBlank { context.getString(R.string.wake_history_status_pending) }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = WakeSubmissionUi.wakeStatusLabel(context, submission),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WakeStatusChip(
                    label = stringResource(R.string.wake_follow_up_yes),
                    selected = wakeStatus == "awake"
                ) { wakeStatus = "awake" }
                WakeStatusChip(
                    label = stringResource(R.string.wake_history_status_pending),
                    selected = wakeStatus == "pending"
                ) { wakeStatus = "pending" }
                WakeStatusChip(
                    label = stringResource(R.string.wake_follow_up_no),
                    selected = wakeStatus == "asleep"
                ) { wakeStatus = "asleep" }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.wake_history_detail_ai_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = submission.feedback.ifBlank {
                    context.getString(R.string.wake_history_detail_ai_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.wake_history_detail_input_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = when {
                    submission.type == "image" && submission.text.isNotBlank() -> submission.text
                    submission.type == "image" && loadedBitmap == null -> context.getString(R.string.wake_history_image_unavailable)
                    submission.type == "image" -> context.getString(R.string.wake_history_detail_image_only)
                    submission.text.isBlank() -> context.getString(R.string.wake_history_detail_no_text)
                    else -> submission.text
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun loadWakeSubmissionBitmap(imagePath: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (imagePath.isBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            when {
                imagePath.startsWith("http://") || imagePath.startsWith("https://") ->
                    URL(imagePath).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                else -> {
                    val file = File(imagePath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
                }
            }
        }.getOrNull()
    }
}

@Composable
private fun WakeStatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

private data class WakeDayOption(val dayNumber: Int, val labelRes: Int)

private val WakeDayOptions = listOf(
    WakeDayOption(1, R.string.day_sun),
    WakeDayOption(2, R.string.day_mon),
    WakeDayOption(3, R.string.day_tue),
    WakeDayOption(4, R.string.day_wed),
    WakeDayOption(5, R.string.day_thu),
    WakeDayOption(6, R.string.day_fri),
    WakeDayOption(7, R.string.day_sat)
)
