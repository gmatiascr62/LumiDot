package com.lumidot.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.lumidot.app.data.LedSettings

/**
 * Vista previa interactiva: simula la pantalla negra del teléfono a escala.
 * Arrastrá o tocá para mover el LED; [onPositionChange] se llama al soltar.
 */
@Composable
fun LedPreview(
    settings: LedSettings,
    modifier: Modifier = Modifier,
    colors: List<Int> = listOf(settings.color),
    interactive: Boolean = true,
    onPositionChange: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { time = it }
    }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    val currentSettings by rememberUpdatedState(settings)
    val onPos by rememberUpdatedState(onPositionChange)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)

    Box(
        modifier
            .aspectRatio(9f / 19.5f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
            .then(
                if (interactive) Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            val x = (o.x / size.width).coerceIn(0f, 1f)
                            val y = (o.y / size.height).coerceIn(0f, 1f)
                            onPos(x, y)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o ->
                                dragPos = Offset(o.x / size.width, o.y / size.height)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragPos = Offset(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f),
                                )
                            },
                            onDragEnd = {
                                dragPos?.let { onPos(it.x, it.y) }
                                dragPos = null
                            },
                            onDragCancel = { dragPos = null },
                        )
                    }
                else Modifier
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = currentSettings
            val period = s.periodMs.coerceAtLeast(1)
            val phase = (time % period).toFloat() / period
            val cycle = (time / period).toInt()
            val palette = if (s.cycleAppColors) colors.ifEmpty { listOf(s.color) } else listOf(colors.firstOrNull() ?: s.color)
            val color = Color(palette[cycle % palette.size])
            val alpha = s.pattern.intensity(phase)

            // Escala: el ancho de la vista previa representa el ancho real de la pantalla.
            val scale = size.width / (screenWidthDp.dp.toPx())
            val radius = (s.sizeDp.dp.toPx() * scale / 2f).coerceAtLeast(1.5f)
            val pos = dragPos ?: Offset(s.posX, s.posY)
            val center = Offset(
                (pos.x * size.width).coerceIn(radius, size.width - radius),
                (pos.y * size.height).coerceIn(radius, size.height - radius),
            )
            if (s.glow) {
                val glowRadius = radius * 2.8f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = 0.33f * alpha),
                        0.25f to color.copy(alpha = 0.33f * alpha),
                        1f to Color.Transparent,
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
            }
            drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center)
        }
        if (interactive) {
            Text(
                "Arrastrá el punto para moverlo",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }
}
