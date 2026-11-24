package com.example.neptuno.voice.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.neptuno.app.NeptunoApplication
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Servicio iniciado explícitamente desde ajustes mientras NEPTUNO está visible. */
class WakeWordForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                engine().stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification("Preparando el detector español local…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val wakeEngine = engine()
            val result = wakeEngine.start()
            if (result.isFailure) {
                updateNotification(
                    result.exceptionOrNull()?.localizedMessage ?: "No se pudo iniciar la escucha local."
                )
                stopSelf(startId)
                return@launch
            }
            updateNotification("Escuchando localmente: «Hey Neptuno»")
            launch {
                wakeEngine.detections().collect { handleDetection(wakeEngine) }
            }
            wakeEngine.state.collect { state ->
                if (state == WakeWordState.ERROR) {
                    updateNotification("El detector encontró un error. Detén y vuelve a iniciar la escucha.")
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Frase detectada: libera el micrófono para el dictado del chat y publica una notificación normal.
     * El usuario abre la notificación explícitamente; Android exige desbloquear antes de mostrar la actividad.
     * La conversación de voz aún depende de ChatViewModel/UI y se completará en una fase posterior.
     */
    private suspend fun handleDetection(wakeEngine: WakeWordEngine) {
        wakeEngine.stop()
        val bus = (application as NeptunoApplication).appContainer.wakeActivationBus
        bus.request()
        updateNotification("Frase detectada. Toca esta notificación y desbloquea el teléfono para hablar.")
        // Si no se abre la notificación, reanuda el detector tras 45 s.
        // Si el chat consume la activación, mantiene Vosk detenido durante toda la conversación
        // (incluida la generación y el TTS) para que no compita por el micrófono.
        val opened = kotlinx.coroutines.withTimeoutOrNull(RESUME_TIMEOUT_MS) {
            bus.consumed.first { it }
            true
        } ?: false
        if (opened) {
            kotlinx.coroutines.withTimeoutOrNull(ACTIVE_SESSION_TIMEOUT_MS) { bus.turnEnded.first() }
        } else {
            kotlinx.coroutines.withTimeoutOrNull(RESUME_TIMEOUT_MS) { bus.turnEnded.first() }
        }
        kotlinx.coroutines.delay(1_500)
        val result = wakeEngine.start()
        if (result.isSuccess) {
            updateNotification("Escuchando localmente: «Hey Neptuno»")
        } else {
            updateNotification(result.exceptionOrNull()?.localizedMessage ?: "No se pudo reanudar la escucha.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        runCatching { runBlocking(Dispatchers.IO) { engine().stop() } }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun engine(): WakeWordEngine =
        (application as NeptunoApplication).appContainer.wakeWordEngine

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Escucha local de NEPTUNO", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indica cuándo NEPTUNO mantiene activo el micrófono para detectar su frase."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NEPTUNO · escucha local")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Detener", stopService)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    companion object {
        const val ACTION_START = "com.example.neptuno.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.example.neptuno.action.STOP_WAKE_WORD"
        private const val CHANNEL_ID = "neptuno_wake_word"
        private const val NOTIFICATION_ID = 7204
        private const val RESUME_TIMEOUT_MS = 45_000L
        private const val ACTIVE_SESSION_TIMEOUT_MS = 10 * 60_000L
    }
}
