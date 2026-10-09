package com.lumidot.app.led

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/**
 * Dibuja el punto LED (con halo opcional). El parpadeo se hace cambiando
 * [View.setAlpha], que sólo actualiza una propiedad del RenderNode y no vuelve
 * a dibujar la vista: es la forma más barata de animar en cada cuadro.
 */
class LedDotView(context: Context) : View(context) {

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var dotColor: Int = Color.GREEN
        set(value) {
            if (field != value) {
                field = value
                updateShaders()
                invalidate()
            }
        }

    var dotSizePx: Float = 40f
        private set
    var glow: Boolean = true
        private set

    /** Tamaño total de la vista (el halo ocupa más que el punto). */
    val viewSizePx: Int get() = (if (glow) dotSizePx * GLOW_FACTOR else dotSizePx + 2f).toInt().coerceAtLeast(1)

    fun configure(sizePx: Float, glow: Boolean) {
        if (sizePx == dotSizePx && glow == this.glow) return
        dotSizePx = sizePx
        this.glow = glow
        updateShaders()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSizePx, viewSizePx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateShaders()

    private fun updateShaders() {
        val c = viewSizePx / 2f
        corePaint.color = dotColor
        if (glow) {
            val transparent = dotColor and 0x00FFFFFF
            val mid = (dotColor and 0x00FFFFFF) or 0x55000000
            glowPaint.shader = RadialGradient(
                c, c, c,
                intArrayOf(mid, mid, transparent),
                floatArrayOf(0f, 0.25f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            glowPaint.shader = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        val c = viewSizePx / 2f
        if (glow) canvas.drawCircle(c, c, c, glowPaint)
        canvas.drawCircle(c, c, dotSizePx / 2f, corePaint)
    }

    private companion object {
        const val GLOW_FACTOR = 2.8f
    }
}
