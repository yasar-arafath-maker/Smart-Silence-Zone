package com.smartsilentzone.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

enum class CrowdDensityLevel {
    PIN_DROP_QUIET,     // < 35 dB (Library, Silent study room)
    MODERATE_LECTURE,   // 35 - 55 dB (Professor speaking, attentive classroom)
    NORMAL_AMBIENT,     // 55 - 68 dB (Normal hallway, low chatter)
    CHAOTIC_CROWD       // > 70 dB (Loud canteen crowd, sports recess, chaos)
}

data class AcousticState(
    val currentDecibels: Double = 35.0,
    val peakDecibels: Double = 35.0,
    val crowdLevel: CrowdDensityLevel = CrowdDensityLevel.PIN_DROP_QUIET,
    val waveformBars: FloatArray = FloatArray(8) { 0.1f },
    val isStreaming: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AcousticState
        return currentDecibels == other.currentDecibels &&
                peakDecibels == other.peakDecibels &&
                crowdLevel == other.crowdLevel &&
                waveformBars.contentEquals(other.waveformBars) &&
                isStreaming == other.isStreaming
    }

    override fun hashCode(): Int {
        var result = currentDecibels.hashCode()
        result = 31 * result + peakDecibels.hashCode()
        result = 31 * result + crowdLevel.hashCode()
        result = 31 * result + waveformBars.contentHashCode()
        result = 31 * result + isStreaming.hashCode()
        return result
    }
}

@Singleton
class AcousticCrowdEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AcousticCrowdEngine"
        private const val SAMPLE_RATE = 44100
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private val _acousticState = MutableStateFlow(AcousticState())
    val acousticState: StateFlow<AcousticState> = _acousticState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLiveAcousticStream() {
        if (streamJob?.isActive == true) return

        streamJob = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBuf, 2048)
            var audioRecord: AudioRecord? = null

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }

                audioRecord.startRecording()
                _acousticState.value = _acousticState.value.copy(isStreaming = true)

                val buffer = ShortArray(bufferSize)
                val bars = FloatArray(8)
                var peakDb = 30.0

                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / read)
                        val db = 20.0 * log10(maxOf(rms, 1.0))

                        // Calibrate roughly to SPL decibels (20-95 dB range)
                        val normalizedDb = maxOf(20.0, minOf(95.0, db + 10.0))
                        peakDb = maxOf(peakDb * 0.95, normalizedDb)

                        // Generate 8 frequency-like reactive bar heights for visualizer
                        val chunk = read / 8
                        for (b in 0 until 8) {
                            var chunkSum = 0.0
                            val start = b * chunk
                            val end = minOf(read, start + chunk)
                            for (j in start until end) {
                                val s = buffer[j].toFloat() / 32768f
                                chunkSum += s * s
                            }
                            val barRms = sqrt(chunkSum / maxOf(1, end - start)).toFloat()
                            bars[b] = minOf(1.0f, maxOf(0.05f, barRms * 4.5f))
                        }

                        val density = when {
                            normalizedDb < 38.0 -> CrowdDensityLevel.PIN_DROP_QUIET
                            normalizedDb < 55.0 -> CrowdDensityLevel.MODERATE_LECTURE
                            normalizedDb < 70.0 -> CrowdDensityLevel.NORMAL_AMBIENT
                            else -> CrowdDensityLevel.CHAOTIC_CROWD
                        }

                        _acousticState.value = AcousticState(
                            currentDecibels = normalizedDb,
                            peakDecibels = peakDb,
                            crowdLevel = density,
                            waveformBars = bars.clone(),
                            isStreaming = true
                        )
                    }
                    delay(120) // ~8 fps smooth reactive UI ticker
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in acoustic recording loop", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                _acousticState.value = _acousticState.value.copy(isStreaming = false)
            }
        }
    }

    fun stopLiveAcousticStream() {
        streamJob?.cancel()
        streamJob = null
        _acousticState.value = _acousticState.value.copy(isStreaming = false)
    }

    /**
     * Dynamic Multi-Sensor Decision Matrix:
     * Fuses GPS active zone, Wi-Fi floor profile, and real-time live acoustic dB.
     */
    fun evaluateAcousticPolicy(
        activeZone: Zone?,
        wifiPolicy: String?,
        currentDb: Double
    ): Pair<RingerAction, String> {
        // Condition 1: Loud Canteen/Break zone with chaotic noise surge > 72dB
        if (activeZone?.type == ZoneType.CANTEEN || activeZone?.type == ZoneType.BREAK_ZONE) {
            return if (currentDb > 72.0) {
                Pair(RingerAction.VIBRATE, "Loud Crowd Detected (${currentDb.toInt()} dB) • Adaptive Haptic Vibrate")
            } else {
                Pair(RingerAction.NORMAL, "Canteen Moderate Ambient (${currentDb.toInt()} dB) • Normal Ringer")
            }
        }

        // Condition 2: Classroom or Library
        if (activeZone?.type == ZoneType.LIBRARY || wifiPolicy == "SILENT") {
            return Pair(RingerAction.SILENT, "Quiet Zone / Library Policy • Pin-Drop Silent")
        }

        if (activeZone?.type == ZoneType.CLASSROOM) {
            return if (currentDb > 68.0) {
                // Unexpected high noise during classroom hours (e.g. recess break)
                Pair(RingerAction.VIBRATE, "Classroom Recess Chatter (${currentDb.toInt()} dB) • Adaptive Vibrate")
            } else {
                Pair(RingerAction.SILENT, "Lecture In Session (${currentDb.toInt()} dB) • Auto-Silenced")
            }
        }

        if (activeZone?.type == ZoneType.EXAM_HALL) {
            return Pair(RingerAction.DND, "Examination In Progress • Strict DND Mode")
        }

        // Fallback default
        return Pair(RingerAction.NORMAL, "Outside Silent Zones • Normal Audio")
    }
}
