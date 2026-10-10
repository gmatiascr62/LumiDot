package com.lumidot.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lumidot.app.data.LedSettings
import com.lumidot.app.led.LedController
import com.lumidot.app.lumiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Detecta notificaciones con la API oficial NotificationListenerService.
 *
 * Privacidad: no se lee ni se guarda el contenido (título/texto/extras) de
 * ninguna notificación. Sólo se usan metadatos (paquete, clave, flags,
 * importancia) para decidir si encender el LED. La app no tiene permiso de
 * Internet, por lo que nada sale del dispositivo.
 */
class LumiNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Última vez (elapsedRealtime) que cada app encendió el LED; evita ráfagas duplicadas. */
    private val lastTriggerByPackage = HashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        AlertTracker.setConnected(true)
        DiagnosticsLog.add("Servicio de notificaciones conectado")
        scope.launch {
            rebuildFromActive(lumiSettings.current())
            // Si cambia la configuración (apps permitidas, colores...), recalcular pendientes.
            lumiSettings.settings.drop(1).collect { rebuildFromActive(it) }
        }
    }

    override fun onListenerDisconnected() {
        AlertTracker.setConnected(false)
        DiagnosticsLog.add("Servicio de notificaciones desconectado por el sistema")
        AlertTracker.clear()
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        // Pedirle al sistema que vuelva a vincular el servicio (API oficial).
        try {
            requestRebind(componentName(this))
        } catch (_: RuntimeException) {
            // El usuario revocó el acceso: no hay nada que reconectar.
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        val importance = ranking?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        val matchesDnd = ranking?.matchesInterruptionFilter() ?: true
        val lastAlerted = ranking?.lastAlertedCompat() ?: 0L
        scope.launch { handlePosted(sbn, importance, matchesDnd, lastAlerted) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, currentRankingSafe())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        AlertTracker.remove(sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        AlertTracker.remove(sbn.key)
    }

    override fun onDestroy() {
        scope.cancel()
        AlertTracker.setConnected(false)
        super.onDestroy()
    }

    private suspend fun handlePosted(
        sbn: StatusBarNotification,
        importance: Int,
        matchesDnd: Boolean,
        lastAlerted: Long,
    ) {
        val settings = lumiSettings.current()

        if (sbn.packageName == packageName) return
        if (!isRelevant(sbn)) {
            DiagnosticsLog.add("${sbn.packageName}: ignorada (persistente, de servicio o resumen de grupo)")
            return
        }
        if (sbn.packageName !in settings.seenPackages) lumiSettings.recordSeenPackage(sbn.packageName)

        if (settings.ignoreSilent && importance < NotificationManager.IMPORTANCE_DEFAULT) {
            DiagnosticsLog.add("${sbn.packageName}: ignorada (notificación silenciosa)")
            AlertTracker.remove(sbn.key)
            return
        }
        if (!settings.isAppAllowed(sbn.packageName)) {
            DiagnosticsLog.add("${sbn.packageName}: ignorada (app desactivada en LumiDot)")
            AlertTracker.remove(sbn.key)
            return
        }

        val previous = AlertTracker.get(sbn.key)
        val alert = sbn.toAlert(settings, lastAlerted)
        AlertTracker.put(alert)

        // Las apps de mensajería (WhatsApp, Telegram...) actualizan la MISMA notificación
        // con cada mensaje nuevo y la marcan como "alertar sólo una vez". Se considera
        // novedad si avanzó la hora del contenido o si el sistema volvió a alertar;
        // si no, es una edición/progreso y el LED no cambia.
        if (previous != null && sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) {
            val newer = alert.whenTime > previous.whenTime || alert.lastAlerted > previous.lastAlerted
            if (!newer) {
                DiagnosticsLog.add("${sbn.packageName}: actualización sin contenido nuevo, el LED no cambia")
                return
            }
        }
        if (settings.respectDnd && !matchesDnd) {
            AlertTracker.setSkipReason("No molestar está activo")
            DiagnosticsLog.add("${sbn.packageName}: no se muestra (No molestar)")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastTriggerByPackage[sbn.packageName] ?: 0L
        if (now - last < DEBOUNCE_MS) {
            DiagnosticsLog.add("${sbn.packageName}: repetida en menos de ${DEBOUNCE_MS / 1000} s, se agrupa")
            return
        }
        lastTriggerByPackage[sbn.packageName] = now
        DiagnosticsLog.add("${sbn.packageName}: notificación nueva (${DiagnosticsLog.screenState(this)})")

        LedController.onNewAlert(this, settings)
    }

    private fun rebuildFromActive(settings: LedSettings) {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (_: SecurityException) {
            emptyArray()
        } catch (_: RuntimeException) {
            emptyArray()
        }
        val ranking = currentRankingSafe()
        val alerts = active.mapNotNull { sbn ->
            if (!isRelevant(sbn) || sbn.packageName == packageName) return@mapNotNull null
            if (!settings.isAppAllowed(sbn.packageName)) return@mapNotNull null
            val r = ranking?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
            if (settings.ignoreSilent && r != null && r.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                return@mapNotNull null
            }
            sbn.toAlert(settings, r?.lastAlertedCompat() ?: 0L)
        }
        AlertTracker.replaceAll(alerts)
    }

    private fun currentRankingSafe(): RankingMap? = try {
        currentRanking
    } catch (_: RuntimeException) {
        null
    }

    private fun StatusBarNotification.toAlert(settings: LedSettings, lastAlerted: Long) = PendingAlert(
        key = key,
        packageName = packageName,
        color = settings.colorFor(packageName),
        postTime = postTime,
        whenTime = notification?.`when` ?: 0L,
        lastAlerted = lastAlerted,
    )

    private fun Ranking.lastAlertedCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) lastAudiblyAlertedMillis else 0L

    companion object {
        private const val DEBOUNCE_MS = 2500L

        private val IGNORED_CATEGORIES = setOf(
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_LOCATION_SHARING,
        )

        /** Filtra notificaciones persistentes, de servicio, resúmenes de grupo, etc. */
        fun isRelevant(sbn: StatusBarNotification): Boolean {
            val n = sbn.notification ?: return false
            if (sbn.isOngoing || !sbn.isClearable) return false
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
            if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
            if (n.category in IGNORED_CATEGORIES) return false
            return true
        }

        fun componentName(context: Context) = ComponentName(context, LumiNotificationListener::class.java)
    }
}
