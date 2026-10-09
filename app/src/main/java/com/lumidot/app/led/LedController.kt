package com.lumidot.app.led

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import com.lumidot.app.data.LedSettings
import com.lumidot.app.service.AlertTracker
import java.util.Calendar

/**
 * Decide si corresponde mostrar el LED y lo lanza.
 *
 * Restricciones de Android que se respetan (sin trucos):
 *  - Una app en segundo plano sólo puede abrir una actividad si el usuario le
 *    concedió "Mostrar sobre otras apps" (excepción oficial a las restricciones
 *    de inicio de actividades en segundo plano). Sin ese acceso el LED no se
 *    lanza automáticamente; la vista previa manual siempre funciona.
 *  - El LED sólo se muestra con la pantalla apagada: si el usuario está usando
 *    el teléfono ya ve la notificación normalmente.
 */
object LedController {

    @Volatile
    var ledVisible: Boolean = false

    fun onNewAlert(context: Context, settings: LedSettings) {
        val reason = blockReason(context, settings)
        AlertTracker.setSkipReason(reason)
        if (reason != null) return
        // Si el LED ya está visible, el intent llega a onNewIntent y reinicia su temporizador.
        launch(context, newAlert = true)
    }

    /** Motivo por el que no se debe mostrar el LED ahora, o null si se puede. */
    fun blockReason(context: Context, s: LedSettings): String? {
        if (!s.enabled) return "LumiDot está desactivado"
        val pm = context.getSystemService(PowerManager::class.java)
        // Con la pantalla encendida (y sin el LED en primer plano) el usuario ya ve la notificación.
        if (pm != null && pm.isInteractive && !ledVisible) return "La pantalla estaba encendida"
        if (!Settings.canDrawOverlays(context)) return "Falta el acceso \"Mostrar sobre otras apps\""
        if (s.skipInCall && isInCall(context)) return "Hay una llamada en curso"
        val cal = Calendar.getInstance()
        if (s.isInQuietHours(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))) {
            return "Horario silencioso"
        }
        if (s.lowBatteryCutoff > 0 && isBatteryLow(context, s.lowBatteryCutoff)) return "Batería baja"
        return null
    }

    fun launch(context: Context, newAlert: Boolean = false, preview: Boolean = false) {
        val intent = Intent(context, LedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(LedActivity.EXTRA_PREVIEW, preview)
            putExtra(LedActivity.EXTRA_NEW_ALERT, newAlert)
        }
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            AlertTracker.setSkipReason("Android bloqueó la apertura del LED")
        }
    }

    private fun isInCall(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == AudioManager.MODE_RINGTONE
    }

    private fun isBatteryLow(context: Context, cutoff: Int): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (plugged) return false
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return false
        return level * 100 / scale < cutoff
    }
}
