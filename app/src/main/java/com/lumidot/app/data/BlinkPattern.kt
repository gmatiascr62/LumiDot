package com.lumidot.app.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Patrones de parpadeo. [intensity] recibe la fase del ciclo (0..1) y devuelve
 * la intensidad del LED (0..1). Se usa tanto en el LED real como en la vista previa.
 */
enum class BlinkPattern(val label: String) {
    PULSE("Pulso"),
    BREATHE("Respiración"),
    BLINK("Parpadeo"),
    DOUBLE("Doble destello"),
    HEARTBEAT("Latido"),
    SOLID("Fijo");

    fun intensity(phase: Float): Float {
        val p = phase - phase.toInt().toFloat()
        return when (this) {
            PULSE -> (0.12f + 0.88f * (0.5f - 0.5f * cos(2.0 * PI * p)).toFloat())
            BREATHE -> if (p < 0.65f) sin(PI * p / 0.65f).toFloat().let { it * it } else 0f
            BLINK -> flash(p, 0f, 0.4f)
            DOUBLE -> maxOf(flash(p, 0f, 0.12f), flash(p, 0.22f, 0.12f))
            HEARTBEAT -> maxOf(beat(p, 0f), 0.7f * beat(p, 0.2f))
            SOLID -> 1f
        }
    }

    /** Destello con flancos suaves para que no sea agresivo a la vista. */
    private fun flash(p: Float, start: Float, length: Float): Float {
        if (p < start || p > start + length) return 0f
        val local = (p - start) / length
        val edge = 0.15f
        return when {
            local < edge -> local / edge
            local > 1f - edge -> (1f - local) / edge
            else -> 1f
        }
    }

    private fun beat(p: Float, start: Float): Float {
        if (p < start) return 0f
        val t = (p - start) * 12f
        return (t * exp(1.0 - t).toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        fun fromName(name: String?): BlinkPattern =
            entries.firstOrNull { it.name == name } ?: PULSE
    }
}
