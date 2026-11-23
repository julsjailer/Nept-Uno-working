package com.example.neptuno.voice.wake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Detector local de la frase de activación ("Hey Neptuno") con Vosk (Apache 2.0) y su modelo pequeño en español.
 *
 * Vosk se usa con una gramática reducida: solo reconoce unas pocas variantes de la frase y manda todo lo demás a
 * "[unk]". Así consume poca CPU y no necesita entrenar un modelo propio. Todo ocurre en el dispositivo: el audio no
 * se envía a ningún servicio. El modelo (unos 40 MB) se descarga una sola vez la primera vez que se activa.
 *
 * Límite conocido (sin probar en teléfono): si una palabra de la gramática no está en el vocabulario del modelo,
 * Vosk la ignora y esa variante no se detectará.
 */
class VoskWakeWordEngine(context: Context) : WakeWordEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WakeWordState.UNAVAILABLE_PENDING_MODEL)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()
    private val _detections = MutableSharedFlow<Long>(extraBufferCapacity = 2)
    override fun detections(): SharedFlow<Long> = _detections.asSharedFlow()

    @Volatile private var config = WakeWordConfig()
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var listenJob: Job? = null

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

                val modelDirectory = VoskSpanishModel.ensureAvailable(appContext)
                val localModel = Model(modelDirectory.absolutePath)
                val localRecognizer = Recognizer(
                    localModel,
                    SAMPLE_RATE.toFloat(),
                    VoskPhraseMatcher.grammarJson(config.keyword)
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
                    maxOf(minBuffer * 2, SAMPLE_RATE)
                )
                check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                    "No se pudo inicializar el micrófono."
                }

                model = localModel
                recognizer = localRecognizer
                audioRecord = recorder
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Android no inició la captura de audio."
                }
                _state.value = WakeWordState.LISTENING
                listenJob = scope.launch { listenLoop(recorder, localRecognizer) }
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
            listenJob?.cancelAndJoin()
            releaseResources()
        }
    }

    private fun listenLoop(recorder: AudioRecord, activeRecognizer: Recognizer) {
        val buffer = ShortArray(CHUNK_SAMPLES)
        var lastDetectionAt = 0L
        try {
            while (_state.value == WakeWordState.LISTENING) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count <= 0) {
                    if (_state.value != WakeWordState.LISTENING) break
                    throw IOException("La captura de audio se interrumpió (código $count).")
                }
                val isFinal = activeRecognizer.acceptWaveForm(buffer, count)
                val json = if (isFinal) activeRecognizer.getResult() else activeRecognizer.getPartialResult()
                val text = VoskJson.text(json)
                if (text.isBlank()) continue

                val now = SystemClock.elapsedRealtime()
                if (
                    now - lastDetectionAt >= DETECTION_COOLDOWN_MS &&
                    VoskPhraseMatcher.matches(text, config.keyword, config.sensitivity)
                ) {
                    lastDetectionAt = now
                    activeRecognizer.reset()
                    _detections.tryEmit(System.currentTimeMillis())
                }
            }
        } catch (error: Exception) {
            if (_state.value == WakeWordState.LISTENING) _state.value = WakeWordState.ERROR
        }
    }

    private fun releaseResources() {
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { recognizer?.close() }
        recognizer = null
        runCatching { model?.close() }
        model = null
        listenJob = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 1_600
        private const val DETECTION_COOLDOWN_MS = 3_000L
    }
}

private object VoskJson {
    /** Extrae el texto de un resultado de Vosk: {"text": "..."} (final) o {"partial": "..."} (parcial). */
    fun text(json: String): String = runCatching {
        val obj = JSONObject(json)
        obj.optString("text").ifBlank { obj.optString("partial") }
    }.getOrDefault("")
}

/** Lógica de gramática y coincidencia, sin dependencias de Android (se prueba con JUnit puro). */
internal object VoskPhraseMatcher {
    private const val UNKNOWN = "[unk]"

    /** Palabra principal de la frase: la última ("hey neptuno" -> "neptuno"). */
    fun coreWord(keyword: String): String =
        normalize(keyword).split(' ').lastOrNull { it.isNotBlank() } ?: "neptuno"

    fun phrases(keyword: String): List<String> {
        val core = coreWord(keyword)
        return listOf(normalize(keyword), "hey $core", "ey $core", "oye $core", "hola $core", core)
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Gramática para Vosk: lista JSON con las frases admitidas y "[unk]" para todo lo demás. */
    fun grammarJson(keyword: String): String =
        (phrases(keyword) + UNKNOWN).joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

    /**
     * Con sensibilidad alta basta oír la palabra principal; con sensibilidad baja se exige la frase completa
     * (una palabra de saludo antes de la principal) para evitar falsos positivos.
     */
    fun matches(transcript: String, keyword: String, sensitivity: Float): Boolean {
        val words = normalize(transcript).split(' ').filter { it.isNotBlank() && it != "unk" }
        val core = coreWord(keyword)
        val index = words.indexOf(core)
        if (index < 0) return false
        if (sensitivity >= 0.5f) return true
        return index > 0
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
}

private object VoskSpanishModel {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    private const val MODEL_FOLDER = "vosk-model-small-es-0.42"
    private const val MIN_ZIP_BYTES = 30_000_000L

    /** Devuelve la carpeta del modelo; lo descarga y descomprime solo si todavía no está. */
    fun ensureAvailable(context: Context): File {
        val root = File(context.filesDir, "models/vosk-es")
        val modelDirectory = File(root, MODEL_FOLDER)
        if (isComplete(modelDirectory)) return modelDirectory

        if (!root.exists() && !root.mkdirs()) {
            throw IOException("No se pudo crear el directorio del modelo local.")
        }
        modelDirectory.deleteRecursively()
        val zip = File(root, "$MODEL_FOLDER.zip.part")
        zip.delete()
        try {
            download(MODEL_URL, zip)
            check(zip.length() >= MIN_ZIP_BYTES) { "El modelo descargado está incompleto." }
            unzip(zip, root)
        } finally {
            zip.delete()
        }
        check(isComplete(modelDirectory)) { "El modelo descomprimido no tiene la estructura esperada." }
        return modelDirectory
    }

    private fun isComplete(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile && File(directory, "conf/model.conf").isFile

    private fun download(url: String, destination: File) {
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
                FileOutputStream(destination).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(zip: File, destinationRoot: File) {
        val rootPath = destinationRoot.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val target = File(destinationRoot, entry.name)
                // Protección contra rutas maliciosas dentro del zip ("zip slip").
                check(target.canonicalPath.startsWith(rootPath)) { "Entrada de zip no válida: ${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }
}
