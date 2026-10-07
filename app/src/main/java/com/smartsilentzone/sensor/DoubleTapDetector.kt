package com.smartsilentzone.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoubleTapDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var tapCount = 0
    private var lastTapTime = 0L
    private var isListening = false
    private var onDoubleTapCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "DoubleTapDetector"
        private const val SHAKE_THRESHOLD = 11.5f // Acceleration delta for phone knock/tap
        private const val DOUBLE_TAP_MAX_DELAY = 600L // 600ms between two taps
    }

    /**
     * Sends a haptic vibration prompt to the user and listens for a physical double-tap knock
     * for [timeoutSeconds]. If user double-taps, [onConfirmedSilent] is called.
     * If timeout expires without double-tap, [onTimeoutUnsilent] is called.
     */
    fun promptUserWithHaptics(
        timeoutSeconds: Int = 5,
        onConfirmedSilent: () -> Unit,
        onTimeoutUnsilent: () -> Unit
    ) {
        // 1. Send haptic vibration pulse
        triggerPromptVibration()

        // 2. Start listening for physical double-tap
        startListening {
            onConfirmedSilent()
        }

        // 3. Timeout handler
        CoroutineScope(Dispatchers.Default).launch {
            delay(timeoutSeconds * 1000L)
            if (isListening) {
                stopListening()
                Log.d(TAG, "Double-tap timeout expired. Action: UNSILENT (Normal sound).")
                withContext(Dispatchers.Main) {
                    onTimeoutUnsilent()
                }
            }
        }
    }

    private fun startListening(callback: () -> Unit) {
        onDoubleTapCallback = callback
        tapCount = 0
        lastTapTime = 0L
        isListening = true
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        isListening = false
        onDoubleTapCallback = null
        sensorManager.unregisterListener(this)
    }

    private fun triggerPromptVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 150, 100, 150)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isListening || event == null) return

        val z = event.values[2]
        if (Math.abs(z - 9.8f) > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < DOUBLE_TAP_MAX_DELAY) {
                tapCount++
                if (tapCount >= 2) {
                    Log.i(TAG, "Physical Double-Tap detected! User confirmed SILENT mode.")
                    stopListening()
                    onDoubleTapCallback?.invoke()
                }
            } else {
                tapCount = 1
            }
            lastTapTime = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
