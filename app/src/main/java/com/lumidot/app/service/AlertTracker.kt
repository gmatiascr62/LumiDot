package com.lumidot.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Notificación pendiente que mantiene el LED encendido. Por privacidad sólo se
 * guarda en memoria la clave, el paquete y el color: nunca título ni texto.
 */
data class PendingAlert(
    val key: String,
    val packageName: String,
    val color: Int,
    val postTime: Long,
    /** Notification.when: en apps de mensajería, la hora del último mensaje. */
    val whenTime: Long = 0L,
    /** Última vez que el sistema alertó (sonido/vibración) con esta notificación; 0 si no se sabe. */
    val lastAlerted: Long = 0L,
)

/** Estado en memoria compartido entre el servicio, el LED y la interfaz. */
object AlertTracker {
    private val _alerts = MutableStateFlow<Map<String, PendingAlert>>(emptyMap())
    val alerts: StateFlow<Map<String, PendingAlert>> = _alerts.asStateFlow()

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    /** Último motivo por el que el LED no pudo mostrarse (para diagnóstico en la UI). */
    private val _lastSkipReason = MutableStateFlow<String?>(null)
    val lastSkipReason: StateFlow<String?> = _lastSkipReason.asStateFlow()

    fun contains(key: String) = key in _alerts.value

    fun get(key: String): PendingAlert? = _alerts.value[key]

    fun put(alert: PendingAlert) = _alerts.update { it + (alert.key to alert) }

    fun remove(key: String) = _alerts.update { if (key in it) it - key else it }

    fun replaceAll(list: Collection<PendingAlert>) {
        _alerts.value = list.associateBy { it.key }
    }

    fun clear() {
        _alerts.value = emptyMap()
    }

    fun setConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun setSkipReason(reason: String?) {
        _lastSkipReason.value = reason
    }

    /** Colores distintos de las notificaciones pendientes, de la más reciente a la más antigua. */
    fun pendingColors(): List<Int> =
        _alerts.value.values.sortedByDescending { it.postTime }.map { it.color }.distinct()
}
