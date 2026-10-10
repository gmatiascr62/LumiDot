package com.lumidot.app.led

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.lumidot.app.data.LedSettings
import com.lumidot.app.service.AlertTracker
import com.lumidot.app.service.DiagnosticsLog
import com.lumidot.app.util.Permissions
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

    const val REASON_IN_USE = "Estabas usando el teléfono"

    @Volatile
    var ledVisible: Boolean = false

    private val handler = Handler(Looper.getMainLooper())

    /** Reintento disponible si el sistema apaga la pantalla justo después de mostrar el LED. */
    @Volatile
    var retryAvailable: Boolean = false
        private set

    fun consumeRetry(): Boolean {
        if (!retryAvailable) return false
        retryAvailable = false
        return true
    }

    fun onNewAlert(context: Context, settings: LedSettings) {
        val reason = blockReason(context, settings)
        AlertTracker.setSkipReason(reason)
        if (reason != null) {
            DiagnosticsLog.add("LED no se muestra: $reason")
            return
        }
        retryAvailable = true
        if (ledVisible) {
            // El LED ya está visible: el intent llega a onNewIntent y reinicia su temporizador.
            launch(context, newAlert = true)
            return
        }
        // Esperar un instante: varias capas (HyperOS, One UI) encienden la pantalla o
        // muestran su propio efecto al llegar la notificación y luego la apagan.
        val app = context.applicationContext
        handler.postDelayed({
            if (AlertTracker.alerts.value.isEmpty()) {
                DiagnosticsLog.add("LED cancelado: la notificación ya se leyó")
                return@postDelayed
            }
            DiagnosticsLog.add("Abriendo LED (${DiagnosticsLog.screenState(app)})")
            launch(app, newAlert = true)
        }, LAUNCH_DELAY_MS)
    }

    /** Motivo por el que no se debe mostrar el LED ahora, o null si se puede. */
    fun blockReason(context: Context, s: LedSettings): String? {
        if (!s.enabled) return "LumiDot está desactivado"
        val pm = context.getSystemService(PowerManager::class.java)
        // Si el teléfono está desbloqueado y en uso, el usuario ya ve la notificación.
        // Con la pantalla encendida pero bloqueada (algunas capas, como HyperOS, la
        // encienden al llegar una notificación) el LED sí se muestra.
        val km = context.getSystemService(KeyguardManager::class.java)
        val locked = km?.isKeyguardLocked == true
        if (pm != null && pm.isInteractive && !locked && !ledVisible) return REASON_IN_USE
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
            DiagnosticsLog.add("Android rechazó abrir el LED: ${e.javaClass.simpleName}")
            return
        }
        // Algunas capas (MIUI/HyperOS) descartan el inicio en silencio, sin excepción.
        if (!preview && !ledVisible) checkLaunched()
    }

    /**
     * Prueba del camino real: lanza el LED desde segundo plano a los pocos segundos,
     * para que el usuario bloquee el teléfono y vea si el sistema lo permite.
     */
    fun scheduleLockedTest(context: Context) {
        val app = context.applicationContext
        AlertTracker.setSkipReason(null)
        handler.postDelayed({
            if (!Settings.canDrawOverlays(app)) {
                AlertTracker.setSkipReason("Falta el acceso \"Mostrar sobre otras apps\"")
            } else {
                launch(app, preview = true)
                checkLaunched()
            }
        }, LOCKED_TEST_DELAY_MS)
    }

    private fun checkLaunched() {
        handler.postDelayed({
            if (!ledVisible) {
                DiagnosticsLog.add("El LED no llegó a verse (bloqueado por el sistema o cerrado enseguida)")
                AlertTracker.setSkipReason(
                    if (Permissions.isXiaomi()) "HyperOS bloqueó la pantalla del LED. Revisá \"Permisos de Xiaomi\""
                    else "El sistema no permitió abrir la pantalla del LED"
                )
            }
        }, LAUNCH_CHECK_MS)
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

    private const val LAUNCH_CHECK_MS = 3000L
    private const val LOCKED_TEST_DELAY_MS = 5000L
    private const val LAUNCH_DELAY_MS = 1200L
}
