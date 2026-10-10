package com.lumidot.app.service

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Registro breve, sólo en memoria, de lo que hizo LumiDot con cada notificación.
 * Sirve para diagnosticar por qué el LED no apareció. Nunca guarda el contenido
 * de las notificaciones: sólo el nombre del paquete y el estado de la pantalla.
 */
object DiagnosticsLog {
    data class Entry(val time: Long, val text: String)

    private const val MAX_ENTRIES = 40

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(text: String) {
        val entry = Entry(System.currentTimeMillis(), text)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** "pantalla apagada · bloqueado" */
    fun screenState(context: Context): String {
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive == true
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        return (if (interactive) "pantalla encendida" else "pantalla apagada") + " · " +
            (if (locked) "bloqueado" else "desbloqueado")
    }
}
