package com.example.neptuno.voice.wake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

/**
 * Prototipo experimental: segmenta voz por energía y transcribe cada segmento con Moonshine
 * español totalmente local. No envía audio a Gemini ni a ningún servicio de transcripción.
 *
 * Moonshine se usa como ASR local para reconocer la frase; no es un modelo KWS dedicado.
 * La detección ocurre al terminar cada segmento de voz, por lo que puede tener latencia.
 */
class SherpaMoonshineWakeWordEngine(context: Context) : WakeWordEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WakeWordState.UNAVAILABLE_PENDING_MODEL)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()
    private val _detections = MutableSharedFlow<Long>(extraBufferCapacity = 2)
    override fun detections(): SharedFlow<Long> = _detections.asSharedFlow()

    @Volatile private var config = WakeWordConfig()
    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var recognitionJob: Job? = null
    private var segmentChannel: Channel<FloatArray>? = null

    override suspend fun configure(config: WakeWordConfig) {
        this.config = config.copy(
            sensitivity = config.sensitivity.coerceIn(0.1f, 0.95f),
            keyword = config.keyword.ifBlank { "Hey Neptuno" }
        )
    }

    override suspend fun start(): Result<Unit> = mutex.withLock {
        if (_state.value == WakeWordState.LISTENING) return Result.success(Unit)
        _state.value = WakeWordState.INITIALIZING
        withContext(Dispatchers.IO) {
            runCatching {
                check(
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                ) { "Debes conceder el permiso de micrófono a NEPTUNO." }

                val modelFiles = MoonshineSpanishModelFiles.ensureAvailable(appContext)
                val modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        encoder = modelFiles.encoder.absolutePath,
                        mergedDecoder = modelFiles.decoder.absolutePath
                    ),
                    tokens = modelFiles.tokens.absolutePath,
                    numThreads = 1,
                    provider = "cpu"
                )
                val localRecognizer = OfflineRecognizer(
                    config = OfflineRecognizerConfig(modelConfig = modelConfig)
                )

                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBuffer > 0) { "Android no pudo calcular el búfer del micrófono." }
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer * 2, SAMPLE_RATE / 2)
                )
                check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                    "No se pudo inicializar el micrófono."
                }

                recognizer = localRecognizer
                audioRecord = recorder
                segmentChannel = Channel(capacity = 3)
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Android no inició la captura de audio."
                }
                _state.value = WakeWordState.LISTENING
                recognitionJob = scope.launch { recognizeSegments() }
                captureJob = scope.launch { captureAndSegment(recorder) }
            }.onFailure {
                releaseResources()
                _state.value = WakeWordState.ERROR
            }.map { Unit }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            _state.value = WakeWordState.DISABLED
            runCatching {
                audioRecord?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                }
            }
            segmentChannel?.close()
            captureJob?.cancelAndJoin()
            recognitionJob?.cancelAndJoin()
            releaseResources()
        }
    }

    private suspend fun captureAndSegment(recorder: AudioRecord) {
        val buffer = ShortArray(CHUNK_SAMPLES)
        val segment = ArrayList<Float>(SAMPLE_RATE * MAX_SEGMENT_SECONDS)
        var speechStarted = false
        var lastVoiceAt = 0L
        val threshold = (0.022f - config.sensitivity * 0.015f).coerceIn(0.004f, 0.02f)
        try {
            while (_state.value == WakeWordState.LISTENING) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count <= 0) {
                    if (_state.value != WakeWordState.LISTENING) break
                    throw IOException("La captura de audio se interrumpió (código $count).")
                }
                val samples = FloatArray(count)
                var energy = 0.0
                for (i in 0 until count) {
                    val value = buffer[i] / 32768.0f
                    samples[i] = value
                    energy += value * value
                }
                val rms = sqrt(energy / count).toFloat()
                val now = android.os.SystemClock.elapsedRealtime()
                if (rms >= threshold) {
                    speechStarted = true
                    lastVoiceAt = now
                    appendSamples(segment, samples)
                } else if (speechStarted) {
                    appendSamples(segment, samples)
                    if (now - lastVoiceAt >= SILENCE_END_MS) {
                        submitSegment(segment)
                        segment.clear()
                        speechStarted = false
                    }
                }
                if (speechStarted && segment.size >= SAMPLE_RATE * MAX_SEGMENT_SECONDS) {
                    submitSegment(segment)
                    segment.clear()
                    speechStarted = false
                }
            }
        } catch (error: Exception) {
            if (_state.value == WakeWordState.LISTENING) _state.value = WakeWordState.ERROR
        } finally {
            segmentChannel?.close()
        }
    }

    private fun appendSamples(target: ArrayList<Float>, source: FloatArray) {
        source.forEach(target::add)
    }

    private fun submitSegment(segment: ArrayList<Float>) {
        if (segment.size >= SAMPLE_RATE / 2) segmentChannel?.trySend(segment.toFloatArray())
    }

    private suspend fun recognizeSegments() {
        var lastDetectionAt = 0L
        try {
            for (samples in segmentChannel ?: return) {
                val localRecognizer = recognizer ?: break
                val stream = localRecognizer.createStream()
                val transcript = try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    localRecognizer.decode(stream)
                    localRecognizer.getResult(stream).text
                } finally {
                    stream.release()
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (
                    now - lastDetectionAt >= DETECTION_COOLDOWN_MS &&
                    WakeWordPhraseMatcher.matches(transcript, config.keyword)
                ) {
                    lastDetectionAt = now
                    _detections.emit(System.currentTimeMillis())
                }
            }
        } catch (error: Exception) {
            if (_state.value == WakeWordState.LISTENING) _state.value = WakeWordState.ERROR
        }
    }

    private fun releaseResources() {
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { recognizer?.release() }
        recognizer = null
        captureJob = null
        recognitionJob = null
        segmentChannel = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 1_600
        private const val MAX_SEGMENT_SECONDS = 5
        private const val SILENCE_END_MS = 550L
        private const val DETECTION_COOLDOWN_MS = 3_500L
    }
}

internal object WakeWordPhraseMatcher {
    fun matches(transcript: String, configuredKeyword: String): Boolean {
        val normalizedTranscript = normalize(transcript)
        if (normalizedTranscript.isBlank()) return false
        val phrases = setOf(
            normalize(configuredKeyword), "hey neptuno", "ey neptuno",
            "ei neptuno", "hey netuno", "hey net uno"
        ).filter { it.isNotBlank() }
        return phrases.any { phrase -> normalizedTranscript.contains(phrase) }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
}

private data class MoonshineSpanishFiles(val encoder: File, val decoder: File, val tokens: File)

private object MoonshineSpanishModelFiles {
    private const val MODEL_REPOSITORY =
        "https://huggingface.co/csukuangfj2/sherpa-onnx-moonshine-base-es-quantized-2026-02-27/resolve/main"

    fun ensureAvailable(context: Context): MoonshineSpanishFiles {
        val directory = File(context.filesDir, "models/moonshine-spanish-v2")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("No se pudo crear el directorio del modelo local.")
        }
        val encoder = download("$MODEL_REPOSITORY/encoder_model.ort", File(directory, "encoder_model.ort"), MIN_ENCODER_BYTES)
        val decoder = download("$MODEL_REPOSITORY/decoder_model_merged.ort", File(directory, "decoder_model_merged.ort"), MIN_DECODER_BYTES)
        val tokens = download("$MODEL_REPOSITORY/tokens.txt", File(directory, "tokens.txt"), MIN_TOKENS_BYTES)
        download("$MODEL_REPOSITORY/LICENSE", File(directory, "LICENSE"), MIN_LICENSE_BYTES)
        return MoonshineSpanishFiles(encoder, decoder, tokens)
    }

    private fun download(url: String, destination: File, minimumBytes: Long): File {
        if (destination.exists() && destination.length() >= minimumBytes) return destination
        val temporary = File(destination.parentFile, destination.name + ".part")
        temporary.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NEPTUNO-Android/1.0")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "No se pudo descargar el modelo español (HTTP ${connection.responseCode})."
            }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
            check(temporary.length() >= minimumBytes) {
                "El archivo descargado está incompleto: ${destination.name}."
            }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) {
                "No se pudo guardar ${destination.name} en el almacenamiento privado."
            }
            return destination
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private const val MIN_ENCODER_BYTES = 10_000_000L
    private const val MIN_DECODER_BYTES = 20_000_000L
    private const val MIN_TOKENS_BYTES = 300_000L
    private const val MIN_LICENSE_BYTES = 10_000L
}
