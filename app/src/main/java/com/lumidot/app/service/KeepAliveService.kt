package com.lumidot.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lumidot.app.R
import com.lumidot.app.ui.MainActivity
import com.lumidot.app.util.Permissions

/**
 * Servicio en primer plano opcional (activado por defecto). Algunas capas del
 * fabricante (HyperOS/MIUI, One UI...) congelan el proceso de las apps en segundo
 * plano con la pantalla apagada, y el servicio de notificaciones deja de recibir
 * eventos hasta que se desbloquea el teléfono. Un servicio en primer plano, con su
 * notificación silenciosa visible, es la forma oficial de evitarlo.
 *
 * No hace trabajo propio: sólo mantiene el proceso activo mientras LumiDot está habilitado.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: RuntimeException) {
            DiagnosticsLog.add("No se pudo mantener LumiDot activo: ${e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LumiDot activo", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Mantiene a LumiDot atento a las notificaciones con la pantalla apagada."
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lumidot)
            .setContentTitle("LumiDot activo")
            .setContentText("Atento a tus notificaciones")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lumidot_status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.lumidot.app.action.STOP_KEEP_ALIVE"

        /**
         * Inicia o detiene el servicio según la configuración. Android sólo permite
         * iniciarlo con la app en primer plano (o en casos exceptuados), así que se
         * llama al abrir la app y desde el LED; si no se puede, no pasa nada.
         */
        fun sync(context: Context, enabled: Boolean) {
            val app = context.applicationContext
            val shouldRun = enabled && Permissions.hasNotificationAccess(app)
            try {
                if (shouldRun) {
                    ContextCompat.startForegroundService(app, Intent(app, KeepAliveService::class.java))
                } else {
                    app.stopService(Intent(app, KeepAliveService::class.java))
                }
            } catch (_: RuntimeException) {
                // ForegroundServiceStartNotAllowedException u otra restricción: se reintenta más tarde.
            }
        }
    }
}
