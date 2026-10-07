package com.smartsilentzone.sensor

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

enum class AcousticEnvironment {
    PIN_DROP_SILENT,   // < 35 dB (e.g. Library / Study Hall) -> AUTO SILENT
    LECTURE_IN_SESSION, // 35 - 55 dB (e.g. Professor lecturing) -> AUTO SILENT
    RECESS_BREAK,      // > 65 dB (e.g. Students chatter during break) -> UNSILENT
    AMBIGUOUS_NORMAL   // 55 - 65 dB -> Prompt vibration & wait for Double-Tap!
}

@Singleton
class AmbientNoiseClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AcousticClassifier"
        private const val SAMPLE_RATE = 44100
    }

    /**
     * Samples 1.5 seconds of ambient sound pressure level (SPL).
     * Calculates decibels (dB) locally. ZERO audio data is recorded or stored.
     */
    suspend fun sampleAmbientDecibels(): Double = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize <= 0) return@withContext 45.0 // fallback

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val buffer = ShortArray(bufferSize)
            record.startRecording()

            var sumSquare = 0.0
            var totalSamples = 0
            val readCount = record.read(buffer, 0, buffer.size)

            if (readCount > 0) {
                for (i in 0 until readCount) {
                    val s = buffer[i].toDouble()
                    sumSquare += s * s
                }
                totalSamples += readCount
            }

            val rms = if (totalSamples > 0) sqrt(sumSquare / totalSamples) else 1.0
            // Calculate decibels relative to reference amplitude
            val db = 20 * log10(maxOf(rms, 1.0))
            Log.d(TAG, "Measured Ambient Sound Pressure Level: ${db.toInt()} dB")
            return@withContext db
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio permission not granted, defaulting to normal")
            return@withContext 45.0
        } catch (e: Exception) {
            Log.e(TAG, "Acoustic sensing error", e)
            return@withContext 45.0
        } finally {
            try {
                record?.stop()
                record?.release()
            } catch (_: Exception) {}
        }
    }

    fun classify(decibels: Double): AcousticEnvironment = when {
        decibels < 35.0 -> AcousticEnvironment.PIN_DROP_SILENT
        decibels in 35.0..55.0 -> AcousticEnvironment.LECTURE_IN_SESSION
        decibels > 65.0 -> AcousticEnvironment.RECESS_BREAK
        else -> AcousticEnvironment.AMBIGUOUS_NORMAL
    }
}
