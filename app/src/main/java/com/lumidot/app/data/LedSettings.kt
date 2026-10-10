package com.lumidot.app.data

enum class AppFilterMode(val label: String) {
    ALL_EXCEPT_EXCLUDED("Todas, excepto las desactivadas"),
    ONLY_SELECTED("Sólo las seleccionadas");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: ALL_EXCEPT_EXCLUDED
    }
}

/** Configuración por aplicación. [color] nulo = usar el color general. */
data class AppRule(
    val packageName: String,
    val enabled: Boolean = true,
    val color: Int? = null,
)

data class LedSettings(
    val enabled: Boolean = true,
    val color: Int = DEFAULT_COLOR,
    val sizeDp: Int = 14,
    /** Posición relativa del centro del LED (0..1). */
    val posX: Float = 0.5f,
    val posY: Float = 0.045f,
    val pattern: BlinkPattern = BlinkPattern.PULSE,
    /** Duración de un ciclo de parpadeo en milisegundos. */
    val periodMs: Int = 1800,
    val glow: Boolean = true,
    /** Brillo de pantalla mientras se muestra el LED (fracción 0.01..1). */
    val screenBrightness: Float = 0.05f,
    /** Minutos que el LED mantiene la pantalla activa. 0 = sin límite (mientras haya notificaciones pendientes). */
    val maxDurationMin: Int = 0,
    val burnInShift: Boolean = true,
    val respectDnd: Boolean = true,
    val ignoreSilent: Boolean = true,
    val skipInCall: Boolean = true,
    /** No mostrar el LED con batería por debajo de este porcentaje (0 = desactivado). */
    val lowBatteryCutoff: Int = 15,
    val quietHoursEnabled: Boolean = false,
    /** Minutos desde medianoche. */
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
    val cycleAppColors: Boolean = true,
    val filterMode: AppFilterMode = AppFilterMode.ALL_EXCEPT_EXCLUDED,
    val appRules: Map<String, AppRule> = emptyMap(),
    /** Paquetes que alguna vez publicaron notificaciones (sólo el nombre de paquete). */
    val seenPackages: Set<String> = emptySet(),
    val disclosureAccepted: Boolean = false,
    /** Servicio en primer plano para que la app no sea congelada en segundo plano. */
    val keepAlive: Boolean = true,
) {
    fun isAppAllowed(pkg: String): Boolean {
        val rule = appRules[pkg]
        return when (filterMode) {
            AppFilterMode.ALL_EXCEPT_EXCLUDED -> rule?.enabled != false
            AppFilterMode.ONLY_SELECTED -> rule?.enabled == true
        }
    }

    fun colorFor(pkg: String): Int = appRules[pkg]?.color ?: color

    fun isInQuietHours(minuteOfDay: Int): Boolean {
        if (!quietHoursEnabled || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) minuteOfDay in quietStart until quietEnd
        else minuteOfDay >= quietStart || minuteOfDay < quietEnd
    }

    companion object {
        const val DEFAULT_COLOR: Int = 0xFF00E676.toInt()
        const val MIN_SIZE_DP = 4
        const val MAX_SIZE_DP = 40
        const val MIN_PERIOD_MS = 400
        const val MAX_PERIOD_MS = 5000
    }
}
