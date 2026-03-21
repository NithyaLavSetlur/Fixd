package com.example.fixd

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.example.fixd.databinding.ActivityAlarmChallengeBinding
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class AlarmChallengeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmChallengeBinding
    private lateinit var auth: FirebaseAuth
    private var currentPhotoFile: File? = null
    private var localImagePath: String = ""
    private var imageBytes: ByteArray? = null
    private var validationPassed = false
    private var isLaunchingCamera = false
    private val handler = Handler(Looper.getMainLooper())

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            isLaunchingCamera = false
            if (success) {
                val file = currentPhotoFile ?: return@registerForActivityResult
                imageBytes = WakeValidationApi.compressImage(file.absolutePath)
                binding.photoStatus.text = getString(R.string.alarm_photo_ready)
            } else {
                currentPhotoFile = null
                localImagePath = ""
                imageBytes = null
                binding.photoStatus.text = getString(R.string.alarm_photo_pending)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
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

        binding.capturePhotoButton.setOnClickListener { launchCamera() }
        binding.submitButton.setOnClickListener { submitChallenge() }
        binding.alarmNameText.text = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_NAME).orEmpty()
    }

    private fun launchCamera() {
        val imageDir = File(cacheDir, "images").apply { mkdirs() }
        val imageFile = File(imageDir, "wake-${System.currentTimeMillis()}.jpg")
        currentPhotoFile = imageFile
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        localImagePath = imageFile.absolutePath
        isLaunchingCamera = true
        takePictureLauncher.launch(photoUri)
    }

    private fun submitChallenge() {
        val text = binding.challengeInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank() && imageBytes == null) {
            toast(R.string.alarm_submission_required)
            return
        }

        sendAlarmServiceAction(AlarmRingingService.ACTION_PAUSE)
        binding.progressBar.isVisible = true
        binding.feedbackText.text = ""
        binding.submitButton.isEnabled = false

        val user = auth.currentUser
        if (user == null) {
            sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
            binding.progressBar.isVisible = false
            binding.submitButton.isEnabled = true
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

        WakeValidationApi.validate(
            endpoint = endpoint,
            text = text,
            imageBytes = imageBytes,
            onSuccess = { result ->
                runOnUiThread {
                    handleValidationResult(userId, text, localPath, result, triggeredAt)
                }
            },
            onFailure = { exception ->
                runOnUiThread {
                    binding.progressBar.isVisible = false
                    binding.submitButton.isEnabled = true
                    sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
                    val errorMessage = exception.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.alarm_validation_backend_unavailable)
                    binding.feedbackText.text = errorMessage
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
            onSuccess = { },
            onFailure = { }
        )

        binding.progressBar.isVisible = false
        binding.submitButton.isEnabled = true
        binding.feedbackText.text = result.feedback
        if (result.passed) {
            validationPassed = true
            sendAlarmServiceAction(AlarmRingingService.ACTION_STOP)
            rescheduleAlarm()
            finish()
        } else {
            sendAlarmServiceAction(AlarmRingingService.ACTION_RESUME)
            binding.challengeInput.text?.clear()
            imageBytes = null
            localImagePath = ""
            binding.photoStatus.text = getString(R.string.alarm_retry_needed)
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
        if (!validationPassed && !isLaunchingCamera) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && !isLaunchingCamera) {
                    startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            }, 350)
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun sendAlarmServiceAction(action: String) {
        startService(android.content.Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID).orEmpty())
        })
    }
}
