package com.example.neptuno.audio.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.neptuno.core.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Implementación real de AudioCaptureEngine usando android.media.AudioRecord (PCM 16-bit mono 16kHz).
 * Lista para canalizar audio crudo hacia futuros motores de audio bidireccional (Gemini Live / WakeWord).
 */
class AndroidAudioCaptureEngine(
    private val context: Context,
    private val sampleRateHz: Int = 16_000
) : AudioCaptureEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _isCapturing = MutableStateFlow(false)
    override val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _pcmFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    val pcmFrames: SharedFlow<ByteArray> = _pcmFrames.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    @SuppressLint("MissingPermission")
    override suspend fun startCapture(): Result<Unit> = mutex.withLock {
        if (!PermissionHelper.hasRecordAudioPermission(appContext)) {
            return Result.failure(SecurityException("Permiso RECORD_AUDIO requerido para capturar audio."))
        }
        if (_isCapturing.value) {
            return Result.success(Unit)
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuf, sampleRateHz / 5 * 2).coerceAtLeast(2048)
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    error("No se pudo inicializar AudioRecord en este dispositivo.")
                }

                audioRecord = recorder
                recorder.startRecording()
                _isCapturing.value = true

                captureJob = scope.launch {
                    val buffer = ByteArray(bufferSize)
                    while (isActive && _isCapturing.value) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            _pcmFrames.tryEmit(buffer.copyOf(read))
                        }
                    }
                }
            }.onFailure {
                _isCapturing.value = false
                runCatching { audioRecord?.release() }
                audioRecord = null
            }
        }
    }

    override suspend fun stopCapture() {
        mutex.withLock {
            _isCapturing.value = false
            captureJob?.cancel()
            captureJob = null
            withContext(Dispatchers.IO) {
                runCatching {
                    audioRecord?.let { rec ->
                        if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            rec.stop()
                        }
                        rec.release()
                    }
                }
                audioRecord = null
            }
        }
    }

    override fun release() {
        _isCapturing.value = false
        captureJob?.cancel()
        runCatching { audioRecord?.release() }
        audioRecord = null
        scope.cancel()
    }
}

/**
 * Implementación real de AudioPlaybackEngine usando android.media.AudioTrack (PCM 16-bit mono).
 * Permite reproducción directa de fragmentos PCM y detención inmediata ante barge-in.
 */
class AndroidAudioPlaybackEngine(
    private val sampleRateHz: Int = 24_000
) : AudioPlaybackEngine {

    private val mutex = Mutex()
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var audioTrack: AudioTrack? = null

    override suspend fun playPcmChunk(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val track = ensureTrack() ?: return@withContext
                _isPlaying.value = true
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    runCatching { track.play() }
                }
                runCatching {
                    track.write(pcmData, 0, pcmData.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    override suspend fun stopImmediately() {
        _isPlaying.value = false
        withContext(Dispatchers.IO) {
            runCatching {
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                    }
                    track.flush()
                    track.stop()
                }
            }
        }
    }

    override fun release() {
        _isPlaying.value = false
        runCatching {
            audioTrack?.let { track ->
                track.flush()
                track.release()
            }
        }
        audioTrack = null
    }

    private fun ensureTrack(): AudioTrack? {
        audioTrack?.let { if (it.state == AudioTrack.STATE_INITIALIZED) return it }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf)
            .build()

        return if (track.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack = track
            track
        } else {
            track.release()
            null
        }
    }
}
