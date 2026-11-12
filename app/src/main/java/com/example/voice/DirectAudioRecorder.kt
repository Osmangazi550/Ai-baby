package com.example.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

private const val TAG = "DirectAudioRecorder"

/**
 * Direct hardware audio capture using standard Android AudioRecord.
 * Bypasses Google Speech Services dependencies to guarantee voice input works
 * on all Android devices, emulators, and environments without Error 13.
 */
class DirectAudioRecorder(
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onAudioRecorded: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onSpeechStart: (() -> Unit)? = null,
    private val onSpeechEnd: (() -> Unit)? = null
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, sampleRate * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                onError("Microphone hardware could not be initialized.")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "AudioRecord started recording at 16kHz mono PCM.")
            onSpeechStart?.invoke()

            recordingJob = scope.launch {
                val outputStream = ByteArrayOutputStream()
                val buffer = ShortArray(1024)
                var silenceFrames = 0
                var hasSpeechStarted = false
                var totalFramesRead = 0

                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readCount > 0) {
                        totalFramesRead++
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            val sample = buffer[i]
                            sum += sample * sample
                            outputStream.write(sample.toInt() and 0xFF)
                            outputStream.write((sample.toInt() shr 8) and 0xFF)
                        }

                        val rms = sqrt(sum / readCount)
                        val normalized = (rms / 3200.0).coerceIn(0.0, 1.0).toFloat()
                        withContext(Dispatchers.Main) {
                            onAmplitudeChanged(normalized)
                        }

                        if (normalized > 0.07f) {
                            if (!hasSpeechStarted) {
                                hasSpeechStarted = true
                                withContext(Dispatchers.Main) {
                                    onSpeechStart?.invoke()
                                }
                            }
                            silenceFrames = 0
                        } else if (hasSpeechStarted) {
                            silenceFrames++
                            // 16000 / 1024 = ~15.6 frames/sec. ~25 frames = ~1.6s silence after speech
                            if (silenceFrames > 25) {
                                Log.d(TAG, "Silence detected after speech, finalizing recording buffer.")
                                withContext(Dispatchers.Main) {
                                    onSpeechEnd?.invoke()
                                }
                                break
                            }
                        }

                        // Max 15s recording safety cap (15 * 15.6 = ~234 frames)
                        if (totalFramesRead > 234) {
                            Log.d(TAG, "Max recording duration (15s) reached, finalizing.")
                            withContext(Dispatchers.Main) {
                                onSpeechEnd?.invoke()
                            }
                            break
                        }
                    }
                }

                val pcmBytes = outputStream.toByteArray()
                val wavBytes = encodeWav(pcmBytes, sampleRate)
                Log.d(TAG, "Recording completed. Total WAV bytes: ${wavBytes.size}")

                withContext(Dispatchers.Main) {
                    if (wavBytes.size > 200) {
                        onAudioRecorded(wavBytes)
                    } else {
                        onError("No speech was detected. Tap mic and speak clearly.")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: RECORD_AUDIO permission missing", e)
            onError("Microphone permission is required to speak with AI BABY.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting DirectAudioRecorder", e)
            onError("Microphone capture error: ${e.message}")
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        Log.d(TAG, "stopRecording invoked. Finalizing audio stream...")
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }

    fun destroy() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    private fun encodeWav(pcmBytes: ByteArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcmBytes.size
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * 2 * channels

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Linear PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        return header + pcmBytes
    }
}
