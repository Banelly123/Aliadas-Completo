package com.aliadas.contacts

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aliadas.R
import com.aliadas.MainActivity

class AlertService : Service() {

    companion object {
        const val CHANNEL_ID = "aliadas_alert_channel"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AlertService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AlertManager.stopAlert(this)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 Alerta activa - Aliadas")
            .setContentText("Tus contactos están siendo notificados de tu ubicación")
            .setSmallIcon(R.drawable.ic_aliadas_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setColor(0xE53935)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas de seguridad",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones cuando el botón de pánico está activo"
            enableLights(true)
            lightColor = 0xE53935
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
