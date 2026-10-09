package com.lumidot.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
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
        scope.launch {
            rebuildFromActive(lumiSettings.current())
            // Si cambia la configuración (apps permitidas, colores...), recalcular pendientes.
            lumiSettings.settings.drop(1).collect { rebuildFromActive(it) }
        }
    }

    override fun onListenerDisconnected() {
        AlertTracker.setConnected(false)
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
        scope.launch { handlePosted(sbn, importance, matchesDnd) }
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

    private suspend fun handlePosted(sbn: StatusBarNotification, importance: Int, matchesDnd: Boolean) {
        val settings = lumiSettings.current()

        if (!isRelevant(sbn) || sbn.packageName == packageName) return
        if (sbn.packageName !in settings.seenPackages) lumiSettings.recordSeenPackage(sbn.packageName)

        if (settings.ignoreSilent && importance < NotificationManager.IMPORTANCE_DEFAULT) {
            AlertTracker.remove(sbn.key)
            return
        }
        if (!settings.isAppAllowed(sbn.packageName)) {
            AlertTracker.remove(sbn.key)
            return
        }

        val isUpdate = AlertTracker.contains(sbn.key)
        AlertTracker.put(sbn.toAlert(settings))

        // Actualizaciones de una notificación que pidió "alertar sólo una vez"
        // (progreso, ediciones) no vuelven a encender el LED.
        if (isUpdate && sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
        if (settings.respectDnd && !matchesDnd) {
            AlertTracker.setSkipReason("No molestar está activo")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastTriggerByPackage[sbn.packageName] ?: 0L
        if (now - last < DEBOUNCE_MS) return
        lastTriggerByPackage[sbn.packageName] = now

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
        val tmp = Ranking()
        val alerts = active.filter { sbn ->
            if (!isRelevant(sbn) || sbn.packageName == packageName) return@filter false
            if (!settings.isAppAllowed(sbn.packageName)) return@filter false
            if (settings.ignoreSilent && ranking != null && ranking.getRanking(sbn.key, tmp) &&
                tmp.importance < NotificationManager.IMPORTANCE_DEFAULT
            ) return@filter false
            true
        }.map { it.toAlert(settings) }
        AlertTracker.replaceAll(alerts)
    }

    private fun currentRankingSafe(): RankingMap? = try {
        currentRanking
    } catch (_: RuntimeException) {
        null
    }

    private fun StatusBarNotification.toAlert(settings: LedSettings) = PendingAlert(
        key = key,
        packageName = packageName,
        color = settings.colorFor(packageName),
        postTime = postTime,
    )

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
