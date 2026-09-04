package com.example.wakelock

import android.animation.ObjectAnimator
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.Executors

class ChallengeActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var counterText: TextView
    private lateinit var txtMotivation: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var completionOverlay: FrameLayout
    private lateinit var btnStartDay: MaterialButton

    private var mediaPlayer: MediaPlayer? = null
    private val targetSquats = 10
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastRecordedCount = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_challenge)

        previewView = findViewById(R.id.previewView)
        counterText = findViewById(R.id.counterText)
        txtMotivation = findViewById(R.id.txtMotivation)
        progressBar = findViewById(R.id.progressBar)
        completionOverlay = findViewById(R.id.completionOverlay)
        btnStartDay = findViewById(R.id.btnStartDay)

        btnStartDay.setOnClickListener {
            finish()
        }

        startAlarmSound()
        startCamera()
    }

    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, SquatPoseAnalyzer { currentCount ->
                        runOnUiThread {
                            updateSquatProgress(currentCount)
                            if (currentCount >= targetSquats && completionOverlay.visibility != View.VISIBLE) {
                                showCompletionScreen()
                            }
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                // Ignore errors on binding
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateSquatProgress(count: Int) {
        counterText.text = "$count / $targetSquats"

        // Tactile subtle bounce microinteraction when count increases
        if (count > lastRecordedCount && count > 0) {
            lastRecordedCount = count
            counterText.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .setDuration(120)
                .withEndAction {
                    counterText.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                }
                .start()
        }

        // Smooth progress bar update
        ObjectAnimator.ofInt(progressBar, "progress", count)
            .setDuration(300)
            .apply {
                interpolator = DecelerateInterpolator()
                start()
            }

        // Calm, clear motivational text progression
        txtMotivation.text = when {
            count == 0 -> "You've got this. Step back into full view."
            count in 1..3 -> "Keep moving. Great form!"
            count in 4..7 -> "You're in the rhythm. Halfway there!"
            count in 8..9 -> "Almost there! Push through."
            else -> "Final repetition!"
        }
    }

    private fun showCompletionScreen() {
        stopAlarmSound()
        completionOverlay.alpha = 0f
        completionOverlay.visibility = View.VISIBLE
        completionOverlay.animate()
            .alpha(1f)
            .setDuration(350)
            .start()
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.run {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        cameraExecutor.shutdown()
    }
}