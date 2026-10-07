package com.smartsilentzone.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.smartsilentzone.ringer.RingerModeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class FlipSensorState(
    val proximityCm: Float = 5.0f,
    val lightLux: Float = 100.0f,
    val tiltZ: Float = 9.8f,
    val isFaceDownHeld: Boolean = false,
    val isSilenceApplied: Boolean = false
)

data class FlipSettings(
    val proximityEnabled: Boolean = true,
    val lightEnabled: Boolean = true,
    val accelerometerEnabled: Boolean = true
)

@Singleton
class FlipToShhhDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ringerModeManager: RingerModeManager
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val scope = CoroutineScope(Dispatchers.Main)
    private var holdJob: Job? = null
    private var previousRingerMode: Int? = null

    private val _sensorState = MutableStateFlow(FlipSensorState())
    val sensorState: StateFlow<FlipSensorState> = _sensorState.asStateFlow()

    var settings = FlipSettings()

    fun startListening() {
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        holdJob?.cancel()
        holdJob = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                _sensorState.value = _sensorState.value.copy(proximityCm = event.values[0])
            }
            Sensor.TYPE_LIGHT -> {
                _sensorState.value = _sensorState.value.copy(lightLux = event.values[0])
            }
            Sensor.TYPE_ACCELEROMETER -> {
                _sensorState.value = _sensorState.value.copy(tiltZ = event.values[2])
            }
        }
        evaluateFlipCondition()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun evaluateFlipCondition() {
        val state = _sensorState.value

        val proxOk = !settings.proximityEnabled || state.proximityCm < 1.0f
        val lightOk = !settings.lightEnabled || state.lightLux < 5.0f
        val tiltOk = !settings.accelerometerEnabled || state.tiltZ <= -8.8f

        val isFaceDown = proxOk && lightOk && tiltOk

        if (isFaceDown) {
            if (holdJob == null && !_sensorState.value.isSilenceApplied) {
                holdJob = scope.launch {
                    delay(1000) // Debounce hold for 1 second
                    if (!isFaceDown) return@launch

                    // Save current mode before silencing
                    previousRingerMode = ringerModeManager.getCurrentRingerMode()
                    ringerModeManager.setSilent()

                    _sensorState.value = _sensorState.value.copy(
                        isFaceDownHeld = true,
                        isSilenceApplied = true
                    )
                }
            }
        } else {
            // Cancel pending debounce if picked up before 1s
            holdJob?.cancel()
            holdJob = null

            // If it was silenced by flip, restore previous mode
            if (_sensorState.value.isSilenceApplied) {
                previousRingerMode?.let { ringerModeManager.restoreRingerMode(it) }
                _sensorState.value = _sensorState.value.copy(
                    isFaceDownHeld = false,
                    isSilenceApplied = false
                )
            }
        }
    }
}
