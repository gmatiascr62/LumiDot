package com.lumidot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LedPalette: List<Int> = listOf(
    0xFF00E676, 0xFF76FF03, 0xFFFFEA00, 0xFFFFAB00, 0xFFFF6D00, 0xFFFF1744,
    0xFFF50057, 0xFFD500F9, 0xFF651FFF, 0xFF2979FF, 0xFF00B0FF, 0xFF00E5FF,
    0xFF1DE9B6, 0xFFFFFFFF,
).map { it.toInt() }

@Composable
fun ColorSwatch(
    color: Int,
    selected: Boolean,
    size: Dp = 36.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.15f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(size / 2))
        }
    }
}

/**
 * Selector de color: paleta + tono personalizado.
 * Si [allowDefault] es true, ofrece "Usar color general" (devuelve null).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int?,
    allowDefault: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var hue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial ?: LedPalette.first(), hsv)
        mutableFloatStateOf(hsv[0])
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LedPalette.forEach { c ->
                        ColorSwatch(c, selected == c) { selected = c }
                    }
                }
                Text("Tono personalizado", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            selected = Color.hsv(it, 1f, 1f).toArgb()
                        },
                        valueRange = 0f..359f,
                        modifier = Modifier.weight(1f),
                    )
                    val c = selected
                    if (c != null) ColorSwatch(c, false, size = 28.dp) {}
                }
                if (allowDefault) {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Usar el color general")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected) }, enabled = selected != null) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
