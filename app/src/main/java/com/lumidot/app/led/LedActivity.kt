package com.lumidot.app.led

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.Display
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lumidot.app.data.LedSettings
import com.lumidot.app.lumiSettings
import com.lumidot.app.service.AlertTracker
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Pantalla completamente negra con un punto que parpadea. En pantallas AMOLED
 * los píxeles negros están apagados, así que sólo consume el punto y el panel
 * a brillo mínimo. Se muestra sobre la pantalla de bloqueo sin desbloquearla.
 *
 * Se cierra sola cuando ya no quedan notificaciones pendientes, al tocarla,
 * al apagar la pantalla con el botón de encendido o al cumplirse el tiempo máximo.
 */
class LedActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var dot: LedDotView
    private var hint: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    private var settings: LedSettings? = null
    private var appliedPattern: Pair<Any, Int>? = null

    private var preview = false
    private var colors: List<Int> = emptyList()
    private var colorIndex = 0
    private var shiftX = 0f
    private var shiftY = 0f
    private var expired = false
    private var startedAt = 0L

    private val expireRunnable = Runnable { expire() }
    private val shiftRunnable = object : Runnable {
        override fun run() {
            val s = settings ?: return
            if (s.burnInShift) {
                val max = dp(BURN_IN_SHIFT_DP)
                shiftX = Random.nextFloat() * 2 * max - max
                shiftY = Random.nextFloat() * 2 * max - max
                placeDot()
            }
            handler.postDelayed(this, BURN_IN_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        setupWindow()

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }
        dot = LedDotView(this)
        root.addView(dot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        if (preview) addPreviewHint()
        setContentView(root)
        root.doOnLayout { placeDot() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { lumiSettings.settings.collect { applySettings(it) } }
                if (!preview) launch { AlertTracker.alerts.collect { onAlertsChanged() } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_NEW_ALERT, false)) {
            // Llegó una notificación nueva: reactivar el LED y reiniciar el tiempo máximo.
            expired = false
            dot.visibility = android.view.View.VISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            restartAnimation(force = true)
            scheduleExpiry()
        }
    }

    override fun onStart() {
        super.onStart()
        startedAt = SystemClock.elapsedRealtime()
        LedController.ledVisible = true
        handler.postDelayed(shiftRunnable, BURN_IN_INTERVAL_MS)
        scheduleExpiry()
        restartAnimation(force = true)
    }

    override fun onStop() {
        super.onStop()
        LedController.ledVisible = false
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        // El usuario apagó la pantalla o salió: el LED se apaga hasta la próxima notificación.
        // (Se ignora un onStop inmediato que algunos equipos emiten al encender la pantalla.)
        if (!isChangingConfigurations && SystemClock.elapsedRealtime() - startedAt > 800) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        animator = null
        super.onDestroy()
    }

    private fun setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val attrs = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lowestRefreshRateMode()?.let { attrs.preferredDisplayModeId = it }
        window.attributes = attrs
    }

    /** Pide al panel la menor frecuencia de refresco disponible (ahorra batería en pantallas 90/120 Hz). */
    private fun lowestRefreshRateMode(): Int? {
        val display: Display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else null)
            ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
            ?: return null
        val current = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { it.refreshRate }
            ?.modeId
    }

    private fun applySettings(s: LedSettings) {
        val previous = settings
        settings = s
        window.attributes = window.attributes.apply { screenBrightness = s.screenBrightness }
        dot.configure(dp(s.sizeDp.toFloat()), s.glow)
        updateColors()
        placeDot()
        if (!s.burnInShift) {
            shiftX = 0f
            shiftY = 0f
        }
        restartAnimation(force = false)
        if (previous?.maxDurationMin != s.maxDurationMin) scheduleExpiry()
    }

    private fun onAlertsChanged() {
        if (AlertTracker.alerts.value.isEmpty()) {
            // Se leyeron o descartaron todas las notificaciones: apagar el LED.
            finish()
            return
        }
        updateColors()
    }

    private fun updateColors() {
        val s = settings ?: return
        colors = if (preview) {
            (listOf(s.color) + s.appRules.values.mapNotNull { it.color }).distinct().take(if (s.cycleAppColors) 4 else 1)
        } else {
            AlertTracker.pendingColors().ifEmpty { listOf(s.color) }.let { if (s.cycleAppColors) it else it.take(1) }
        }
        if (colorIndex >= colors.size) colorIndex = 0
        dot.dotColor = colors[colorIndex]
    }

    private fun restartAnimation(force: Boolean) {
        val s = settings ?: return
        if (expired || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val key = s.pattern to s.periodMs
        if (!force && key == appliedPattern && animator?.isRunning == true) return
        appliedPattern = key
        animator?.cancel()
        val pattern = s.pattern
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = s.periodMs.toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { dot.alpha = pattern.intensity(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    if (colors.size > 1) {
                        colorIndex = (colorIndex + 1) % colors.size
                        dot.dotColor = colors[colorIndex]
                    }
                }
            })
            start()
        }
    }

    private fun scheduleExpiry() {
        handler.removeCallbacks(expireRunnable)
        val minutes = settings?.maxDurationMin ?: return
        val limitMs = if (preview) PREVIEW_MAX_MS else minutes * 60_000L
        if (limitMs > 0) handler.postDelayed(expireRunnable, limitMs)
    }

    /**
     * Tiempo máximo cumplido: se deja de mantener la pantalla encendida y el
     * sistema la apaga con su tiempo de espera habitual. El LED vuelve con la
     * próxima notificación nueva.
     */
    private fun expire() {
        if (preview) {
            finish()
            return
        }
        expired = true
        animator?.cancel()
        dot.visibility = android.view.View.INVISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun placeDot() {
        val s = settings ?: return
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val half = dot.dotSizePx / 2f
        val cx = (s.posX * w + shiftX).coerceIn(half, w - half)
        val cy = (s.posY * h + shiftY).coerceIn(half, h - half)
        val viewHalf = dot.viewSizePx / 2f
        dot.translationX = cx - viewHalf
        dot.translationY = cy - viewHalf
    }

    private fun addPreviewHint() {
        hint = TextView(this).apply {
            text = "Vista previa · tocá la pantalla para cerrar"
            setTextColor(0xFF6B7280.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        root.addView(hint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = dp(48f).toInt() })
        handler.postDelayed({ hint?.animate()?.alpha(0f)?.setDuration(600)?.start() }, 3000)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    companion object {
        const val EXTRA_PREVIEW = "com.lumidot.app.extra.PREVIEW"
        const val EXTRA_NEW_ALERT = "com.lumidot.app.extra.NEW_ALERT"
        private const val BURN_IN_SHIFT_DP = 6f
        private const val BURN_IN_INTERVAL_MS = 60_000L
        private const val PREVIEW_MAX_MS = 60_000L
    }
}
