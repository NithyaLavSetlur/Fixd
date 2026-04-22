package com.example.fixd

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream
import java.io.File

class AlarmChallengeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var currentPhotoFile: File? = null
    private var localImagePath: String = ""
    private var imageBytes: ByteArray? = null
    private var validationPassed = false
    private var isLaunchingCamera = false
    private var entryUnlocked = false
    private var challengeInput by mutableStateOf("")
    private var photoStatus by mutableStateOf("")
    private var feedbackText by mutableStateOf("")
    private var isSubmitting by mutableStateOf(false)
    private var alarmName by mutableStateOf("")
    private val handler = Handler(Looper.getMainLooper())
    private val entryTimeoutRunnable = Runnable {
        if (!validationPassed && !isLaunchingCamera) {
            lockEntryForInactivity()
        }
    }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            isLaunchingCamera = false
            if (success) {
                val file = currentPhotoFile ?: return@registerForActivityResult
                val compressed = runCatching { WakeValidationApi.compressImage(file.absolutePath) }.getOrNull()
                if (compressed != null && compressed.isNotEmpty()) {
                    imageBytes = compressed
                    photoStatus = getString(R.string.alarm_photo_ready)
                    resetEntryTimeout()
                } else {
                    currentPhotoFile = null
                    localImagePath = ""
                    imageBytes = null
                    photoStatus = getString(R.string.alarm_photo_failed)
                    toast(R.string.alarm_photo_failed)
                }
            } else {
                currentPhotoFile = null
                localImagePath = ""
                imageBytes = null
                photoStatus = getString(R.string.alarm_photo_pending)
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                photoStatus = getString(R.string.alarm_camera_permission_required)
                toast(R.string.alarm_camera_permission_required)
            }
        }

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            handlePickedImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        UserPreferences.applyTheme(this)
        ThemePaletteManager.loadCachedSettings(this)
        ThemePaletteManager.applyOverlay(this)
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        alarmName = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_NAME).orEmpty()
        photoStatus = getString(R.string.alarm_photo_pending)
        setContent {
            FixdComposeTheme {
                AlarmChallengeScreen()
            }
        }

        auth.currentUser?.let { user ->
            UserAppearanceRepository.getAppearance(
                userId = user.uid,
                onSuccess = { settings ->
                    ThemePaletteManager.updateSettings(settings)
                    ThemePaletteManager.syncFromAppearance(this)
                },
                onFailure = { }
            )
        }
        NotificationHelper.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmChallengeActivity, R.string.alarm_exit_blocked, Toast.LENGTH_SHORT).show()
            }
        })

        setEntryMode(unlocked = false, showTimeoutMessage = false)
    }

    @Composable
    private fun AlarmChallengeScreen() {
        val palette = ThemePaletteManager.currentPalette(this)
        val gradient = rememberAlarmGradient(
            start = Color(palette.surface),
            middle = Color(palette.card),
            end = Color(palette.primary).copy(alpha = 0.22f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            if (!entryUnlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(88.dp)
                                    .aspectRatio(1f)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(28.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "!",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = getString(R.string.alarm_challenge_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = alarmName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getString(R.string.alarm_start_entry_body),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { unlockEntryMode() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(getString(R.string.alarm_start_entry))
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = getString(R.string.alarm_challenge_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = alarmName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = getString(R.string.alarm_challenge_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            OutlinedTextField(
                                value = challengeInput,
                                onValueChange = {
                                    challengeInput = it
                                    if (entryUnlocked) {
                                        resetEntryTimeout()
                                    }
                                },
                                label = { Text(getString(R.string.alarm_input_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = getString(R.string.alarm_photo_section_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { openCameraForNotePhoto() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(getString(R.string.alarm_capture_photo))
                                }
                                OutlinedButton(
                                    onClick = { openGalleryForPhoto() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(getString(R.string.alarm_choose_photo))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = photoStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { submitChallenge() },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(getString(R.string.alarm_submit))
                            }
                            if (isSubmitting) {
                                Spacer(modifier = Modifier.height(14.dp))
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                    }

                    if (feedbackText.isNotBlank()) {
                        Text(
                            text = feedbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (validationPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    private fun openCameraForNotePhoto() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        launchCamera()
    }

    private fun launchCamera() {
        resetEntryTimeout()
        val photoUri = runCatching {
            val imageDir = File(cacheDir, "images").apply { mkdirs() }
            val imageFile = File(imageDir, "wake-${System.currentTimeMillis()}.jpg")
            currentPhotoFile = imageFile
            localImagePath = imageFile.absolutePath
            FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        }.getOrElse { error ->
            currentPhotoFile = null
            localImagePath = ""
            imageBytes = null
            photoStatus = getString(R.string.alarm_photo_failed)
            toast(error.localizedMessage ?: getString(R.string.alarm_photo_failed))
            return
        }

        try {
            isLaunchingCamera = true
            takePictureLauncher.launch(photoUri)
        } catch (error: Exception) {
            isLaunchingCamera = false
            currentPhotoFile = null
            localImagePath = ""
            imageBytes = null
            photoStatus = getString(R.string.alarm_photo_failed)
            toast(error.localizedMessage ?: getString(R.string.alarm_photo_failed))
        }
    }

    private fun openGalleryForPhoto() {
        resetEntryTimeout()
        pickPhotoLauncher.launch("image/*")
    }

    private fun handlePickedImage(uri: Uri?) {
        if (uri == null) {
            photoStatus = getString(R.string.alarm_photo_pending)
            return
        }
        val compressed = runCatching { compressPickedImage(uri) }.getOrNull()
        if (compressed != null && compressed.isNotEmpty()) {
            imageBytes = compressed
            localImagePath = uri.toString()
            photoStatus = getString(R.string.alarm_photo_selected)
        } else {
            imageBytes = null
            localImagePath = ""
            photoStatus = getString(R.string.alarm_photo_failed)
            toast(R.string.alarm_photo_failed)
        }
    }

    private fun compressPickedImage(uri: Uri): ByteArray {
        val bitmap = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error(getString(R.string.alarm_photo_failed))
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, output)
            output.toByteArray()
        }
    }

    private fun submitChallenge() {
        if (!entryUnlocked || isSubmitting) return

        val text = challengeInput.trim()
        if (text.isBlank() && imageBytes == null) {
            toast(R.string.alarm_submission_required)
            return
        }

        sendAlarmServiceAction(AlarmRingingService.ACTION_LOW_VOLUME)
        isSubmitting = true
        feedbackText = ""

        val user = auth.currentUser
        if (user == null) {
            sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
            isSubmitting = false
            toast(R.string.firebase_not_ready)
            return
        }

        validateSubmission(user.uid, text, localImagePath)
    }

    private fun validateSubmission(userId: String, text: String, localPath: String) {
        val triggeredAt = intent.getLongExtra(AlarmReceiver.EXTRA_TRIGGERED_AT, System.currentTimeMillis())
        val endpoint = getString(R.string.wake_validation_endpoint)
        if (endpoint.isBlank()) {
            handleValidationResult(
                userId = userId,
                text = text,
                localPath = localPath,
                result = WakeValidationApi.localValidate(text = text, imageBytes = imageBytes),
                triggeredAt = triggeredAt
            )
            return
        }

        AlarmRepository.getRecentSubmissions(
            userId = userId,
            onSuccess = { recentSubmissions ->
                WakeValidationApi.validate(
                    endpoint = endpoint,
                    text = text,
                    imageBytes = imageBytes,
                    recentSubmissions = recentSubmissions.map { submission ->
                        WakeValidationApi.RecentSubmissionPayload(
                            type = submission.type,
                            text = submission.text,
                            verdict = submission.verdict,
                            wakeStatus = submission.wakeStatus,
                            createdAt = submission.createdAt
                        )
                    },
                    onSuccess = { result ->
                        runOnUiThread {
                            handleValidationResult(userId, text, localPath, result, triggeredAt)
                        }
                    },
                    onFailure = { exception ->
                        runOnUiThread {
                            isSubmitting = false
                            sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
                            val errorMessage = exception.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.alarm_validation_backend_unavailable)
                            feedbackText = errorMessage
                            toast(errorMessage)
                        }
                    }
                )
            },
            onFailure = { exception ->
                runOnUiThread {
                    isSubmitting = false
                    sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
                    val errorMessage = exception.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.alarm_validation_backend_unavailable)
                    feedbackText = errorMessage
                    toast(errorMessage)
                }
            }
        )
    }

    private fun handleValidationResult(
        userId: String,
        text: String,
        localPath: String,
        result: WakeValidationResult,
        triggeredAt: Long
    ) {
        val completedAt = System.currentTimeMillis()
        AlarmRepository.saveSubmission(
            userId = userId,
            submission = WakeSubmission(
                alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID).orEmpty(),
                type = if (localPath.isBlank()) "text" else "image",
                text = text,
                imagePath = localPath,
                verdict = if (result.passed) "passed" else "retry",
                feedback = result.feedback,
                alarmHour = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_HOUR, 0),
                alarmMinute = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, 0),
                triggeredAt = triggeredAt,
                completedAt = completedAt,
                responseDurationMs = completedAt - triggeredAt
            ),
            onSuccess = { savedSubmission ->
                WakeSubmissionCache.upsertSubmission(this, savedSubmission)
                WakeWidgetUpdater.updateAll(this)
                if (result.passed) {
                    WakeFollowUpScheduler.schedule(
                        context = this,
                        userId = userId,
                        submissionId = savedSubmission.id,
                        triggerAtMillis = completedAt + WakeFollowUpScheduler.FOLLOW_UP_DELAY_MS
                    )
                }
            },
            onFailure = { }
        )

        isSubmitting = false
        feedbackText = result.feedback
        if (result.passed) {
            validationPassed = true
            handler.removeCallbacks(entryTimeoutRunnable)
            sendAlarmServiceAction(AlarmRingingService.ACTION_STOP)
            rescheduleAlarm()
            finish()
        } else {
            sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
            setEntryMode(unlocked = false, showTimeoutMessage = false)
            challengeInput = ""
            imageBytes = null
            localImagePath = ""
            photoStatus = getString(R.string.alarm_retry_needed)
        }
    }

    private fun rescheduleAlarm() {
        val alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID).orEmpty()
        val user = auth.currentUser ?: return
        if (alarmId.isBlank()) return
        AlarmRepository.getAlarms(
            userId = user.uid,
            onSuccess = { alarms ->
                alarms.firstOrNull { it.id == alarmId && it.enabled }?.let {
                    AlarmScheduler.schedule(this, it)
                }
            },
            onFailure = { }
        )
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(entryTimeoutRunnable)
        if (!validationPassed && !isLaunchingCamera) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && !isLaunchingCamera) {
                    val relaunchIntent = Intent(intent).apply {
                        addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    startActivity(relaunchIntent)
                }
            }, 350)
        }
    }

    override fun onResume() {
        super.onResume()
        if (entryUnlocked) {
            resetEntryTimeout()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (entryUnlocked && ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            resetEntryTimeout()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun unlockEntryMode() {
        setEntryMode(unlocked = true, showTimeoutMessage = false)
        sendAlarmServiceAction(AlarmRingingService.ACTION_LOW_VOLUME)
        resetEntryTimeout()
    }

    private fun lockEntryForInactivity() {
        setEntryMode(unlocked = false, showTimeoutMessage = true)
        sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
    }

    private fun setEntryMode(unlocked: Boolean, showTimeoutMessage: Boolean) {
        entryUnlocked = unlocked
        if (!unlocked) {
            handler.removeCallbacks(entryTimeoutRunnable)
            isSubmitting = false
            if (showTimeoutMessage) {
                feedbackText = getString(R.string.alarm_entry_expired)
            }
        } else {
            feedbackText = ""
        }
    }

    private fun resetEntryTimeout() {
        handler.removeCallbacks(entryTimeoutRunnable)
        if (entryUnlocked) {
            handler.postDelayed(entryTimeoutRunnable, ENTRY_TIMEOUT_MS)
        }
    }

    private fun sendAlarmServiceAction(action: String) {
        startService(android.content.Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID).orEmpty())
        })
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ENTRY_TIMEOUT_MS = 3L * 60L * 1000L
    }
}

@Composable
private fun rememberAlarmGradient(
    start: Color,
    middle: Color,
    end: Color
): Brush {
    return Brush.verticalGradient(
        colors = listOf(start, middle, end)
    )
}
