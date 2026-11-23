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
import com.example.MainActivity
import com.example.neptuno.app.NeptunoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
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
                wakeEngine.detections().collect {
                    updateNotification("Frase detectada. La conexión con el chat es la siguiente fase.")
                }
            }
            wakeEngine.state.collect { state ->
                if (state == WakeWordState.ERROR) {
                    updateNotification("El detector encontró un error. Detén y vuelve a iniciar la escucha.")
                }
            }
        }
        return START_NOT_STICKY
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
    }
}
