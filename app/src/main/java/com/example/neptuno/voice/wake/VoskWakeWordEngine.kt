package com.example.neptuno.voice.wake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.neptuno.audio.engine.AndroidBluetoothAudioRouter
import com.example.neptuno.data.local.UserPreferencesRepository
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
class VoskWakeWordEngine(
    context: Context,
    private val preferencesRepository: UserPreferencesRepository? = null
) : WakeWordEngine {
    private val appContext = context.applicationContext
    private val audioRouter = AndroidBluetoothAudioRouter(appContext)
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
    private var audioDeviceCallback: AudioDeviceCallback? = null
    @Volatile private var lastInputSignature: String = ""

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
            try {
                check(
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                ) { "Debes conceder el permiso de micrófono a NEPTUNO." }

                val audioPreferences = try {
                    preferencesRepository?.userPreferences?.first()
                } catch (_: Exception) {
                    null
                }

                val modelDirectory = VoskSpanishModel.ensureAvailable(appContext)
                val localModel = Model(modelDirectory.absolutePath)
                val localRecognizer = Recognizer(
                    localModel,
                    SAMPLE_RATE.toFloat(),
                    VoskPhraseMatcher.grammarJson(config.keyword)
                ).also { it.setWords(true) } // confianza y tiempos por palabra, para filtrar falsos positivos

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

                // Preferencia explícita del usuario; si no hay selección, usa Bluetooth cuando Android
                // expone un micrófono compatible. A2DP no se considera entrada de micrófono.
                audioRouter.configureInput(
                    recorder = recorder,
                    preferredInputId = audioPreferences?.preferredAudioInputId,
                    preferBluetooth = audioPreferences?.preferBluetoothAudio ?: true
                )

                model = localModel
                recognizer = localRecognizer
                audioRecord = recorder
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Android no inició la captura de audio."
                }
                lastInputSignature = audioRouter.currentInputSignature()
                audioDeviceCallback = audioRouter.registerInputDeviceCallback {
                    scheduleInputRouteRefresh()
                }
                _state.value = WakeWordState.LISTENING
                listenJob = scope.launch { listenLoop(recorder, localRecognizer) }
                Result.success(Unit)
            } catch (error: Exception) {
                releaseResources()
                _state.value = WakeWordState.ERROR
                Result.failure(error)
            }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            _state.value = WakeWordState.DISABLED
            audioRouter.unregisterInputDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
            runCatching {
                audioRecord?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                }
            }
            listenJob?.cancelAndJoin()
            releaseResources()
        }
    }

    /**
     * Reinicia únicamente AudioRecord cuando Android informa una entrada añadida o retirada.
     * El reconocedor Vosk y el modelo se conservan, evitando descargar/recrear el modelo en cada cambio.
     */
    private fun scheduleInputRouteRefresh() {
        scope.launch {
            kotlinx.coroutines.delay(INPUT_CHANGE_DEBOUNCE_MS)
            mutex.withLock {
                if (_state.value != WakeWordState.LISTENING) return@withLock
                val newSignature = audioRouter.currentInputSignature()
                if (newSignature == lastInputSignature) return@withLock

                val activeRecognizer = recognizer ?: return@withLock
                _state.value = WakeWordState.INITIALIZING
                runCatching {
                    audioRecord?.let { old ->
                        if (old.recordingState == AudioRecord.RECORDSTATE_RECORDING) old.stop()
                    }
                }
                listenJob?.cancelAndJoin()
                runCatching { audioRecord?.release() }
                audioRecord = null

                try {
                    val prefs = runCatching { preferencesRepository?.userPreferences?.first() }.getOrNull()
                    val minBuffer = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )
                    check(minBuffer > 0) { "Android no pudo calcular el búfer del micrófono." }
                    val replacement = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuffer * 2, SAMPLE_RATE)
                    )
                    check(replacement.state == AudioRecord.STATE_INITIALIZED) {
                        "No se pudo reinicializar el micrófono tras cambiar el dispositivo."
                    }
                    audioRouter.configureInput(
                        recorder = replacement,
                        preferredInputId = prefs?.preferredAudioInputId,
                        preferBluetooth = prefs?.preferBluetoothAudio ?: true
                    )
                    replacement.startRecording()
                    check(replacement.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        "Android no reanudó la captura tras cambiar el dispositivo de audio."
                    }
                    activeRecognizer.reset()
                    audioRecord = replacement
                    lastInputSignature = audioRouter.currentInputSignature()
                    _state.value = WakeWordState.LISTENING
                    listenJob = scope.launch { listenLoop(replacement, activeRecognizer) }
                } catch (error: Exception) {
                    audioRouter.release()
                    runCatching { audioRecord?.release() }
                    audioRecord = null
                    _state.value = WakeWordState.ERROR
                }
            }
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
                // Solo se miran resultados FINALES (tras una pausa): los parciales de Vosk con gramática reducida
                // "alucinan" la palabra clave con casi cualquier sonido.
                if (!activeRecognizer.acceptWaveForm(buffer, count)) continue
                val json = activeRecognizer.getResult()

                val now = SystemClock.elapsedRealtime()
                if (
                    now - lastDetectionAt >= DETECTION_COOLDOWN_MS &&
                    VoskPhraseMatcher.matchesResult(json, config.keyword, config.sensitivity)
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
        audioRouter.unregisterInputDeviceCallback(audioDeviceCallback)
        audioDeviceCallback = null
        audioRouter.release()
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
        private const val INPUT_CHANGE_DEBOUNCE_MS = 300L
    }
}

/** Lógica de gramática y coincidencia (JUnit puro; org.json se usa solo en matchesResult). */
internal object VoskPhraseMatcher {
    private const val UNKNOWN = "[unk]"
    private val GREETINGS = listOf("hey", "ey", "oye", "hola")

    /** Duración máxima (segundos) de la frase hablada; una frase más larga es conversación normal, no la activación. */
    const val MAX_PHRASE_SECONDS = 2.2

    /** Palabra principal de la frase: la última ("hey neptuno" -> "neptuno"). */
    fun coreWord(keyword: String): String =
        normalize(keyword).split(' ').lastOrNull { it.isNotBlank() } ?: "neptuno"

    /** Solo frases con saludo + palabra principal: la palabra suelta se disparaba con casi cualquier sonido. */
    fun phrases(keyword: String): List<String> {
        val core = coreWord(keyword)
        return (listOf(normalize(keyword)) + GREETINGS.map { "$it $core" })
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Gramática para Vosk: lista JSON con las frases admitidas y "[unk]" para todo lo demás. */
    fun grammarJson(keyword: String): String =
        (phrases(keyword) + UNKNOWN).joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

    /** Confianza mínima por palabra: a más sensibilidad, menos exigente (0.6 -> 0.71). */
    fun minConfidence(sensitivity: Float): Double =
        (0.95 - 0.4 * sensitivity.coerceIn(0.1f, 0.95f)).coerceIn(0.5, 0.95)

    /** El texto, sin "[unk]", debe ser exactamente saludo(s) + palabra principal. */
    fun matches(transcript: String, keyword: String): Boolean {
        val words = normalize(transcript).split(' ').filter { it.isNotBlank() && it != "unk" }
        if (words.size < 2) return false
        return words.last() == coreWord(keyword) && words.dropLast(1).all { it in GREETINGS }
    }

    /** Resultado final de Vosk (con setWords(true)): texto correcto + confianza por palabra + frase corta. */
    fun matchesResult(json: String, keyword: String, sensitivity: Float): Boolean = runCatching {
        val obj = org.json.JSONObject(json)
        if (!matches(obj.optString("text"), keyword)) return@runCatching false
        val words = obj.optJSONArray("result") ?: return@runCatching false
        val threshold = minConfidence(sensitivity)
        var first = Double.MAX_VALUE
        var last = 0.0
        for (i in 0 until words.length()) {
            val w = words.getJSONObject(i)
            if (normalize(w.optString("word")) == "unk") continue
            if (w.optDouble("conf", 0.0) < threshold) return@runCatching false
            first = minOf(first, w.optDouble("start", 0.0))
            last = maxOf(last, w.optDouble("end", 0.0))
        }
        last - first <= MAX_PHRASE_SECONDS
    }.getOrDefault(false)

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
