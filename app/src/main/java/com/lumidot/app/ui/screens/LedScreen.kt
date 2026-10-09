package com.lumidot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumidot.app.data.BlinkPattern
import com.lumidot.app.data.LedSettings
import com.lumidot.app.led.LedController
import com.lumidot.app.ui.components.ColorPickerDialog
import com.lumidot.app.ui.components.ColorSwatch
import com.lumidot.app.ui.components.Hint
import com.lumidot.app.ui.components.LedPalette
import com.lumidot.app.ui.components.LedPreview
import com.lumidot.app.ui.components.SectionCard
import com.lumidot.app.ui.components.SettingSlider
import com.lumidot.app.ui.components.SettingSwitch
import java.util.Locale

private data class PositionPreset(val label: String, val x: Float, val y: Float)

private val positionPresets = listOf(
    PositionPreset("Junto a la cámara", 0.5f, 0.045f),
    PositionPreset("Arriba izquierda", 0.1f, 0.045f),
    PositionPreset("Arriba derecha", 0.9f, 0.045f),
    PositionPreset("Centro", 0.5f, 0.5f),
    PositionPreset("Abajo", 0.5f, 0.93f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedScreen(settings: LedSettings, onUpdate: ((LedSettings) -> LedSettings) -> Unit) {
    val context = LocalContext.current
    // Ajustes "en vivo" mientras se mueve un slider, antes de guardarlos.
    var live by remember(settings) { mutableStateOf(settings) }
    var showPicker by remember { mutableStateOf(false) }
    val previewColors = remember(live) {
        (listOf(live.color) + live.appRules.values.mapNotNull { it.color }).distinct().take(4)
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "Vista previa") {
            LedPreview(
                settings = live,
                colors = previewColors,
                modifier = Modifier
                    .height(360.dp)
                    .align(Alignment.CenterHorizontally),
            ) { x, y -> onUpdate { it.copy(posX = x, posY = y) } }
            Button(
                onClick = { LedController.launch(context, preview = true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Probar en pantalla completa")
            }
        }

        SectionCard(title = "Color") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LedPalette.forEach { c ->
                    ColorSwatch(c, settings.color == c) { onUpdate { it.copy(color = c) } }
                }
                val custom = settings.color !in LedPalette
                if (custom) ColorSwatch(settings.color, true) { showPicker = true }
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Color personalizado")
                }
            }
            SettingSwitch(
                title = "Alternar colores por app",
                subtitle = "Si hay notificaciones de varias apps con colores propios, el LED alterna entre ellos.",
                checked = settings.cycleAppColors,
            ) { on -> onUpdate { it.copy(cycleAppColors = on) } }
        }

        SectionCard(title = "Tamaño y brillo del punto") {
            SettingSlider(
                title = "Tamaño",
                value = settings.sizeDp.toFloat(),
                range = LedSettings.MIN_SIZE_DP.toFloat()..LedSettings.MAX_SIZE_DP.toFloat(),
                steps = LedSettings.MAX_SIZE_DP - LedSettings.MIN_SIZE_DP - 1,
                label = { "${it.toInt()} dp" },
                onPreview = { v -> live = live.copy(sizeDp = v.toInt()) },
            ) { v -> onUpdate { it.copy(sizeDp = v.toInt()) } }
            SettingSwitch(
                title = "Halo luminoso",
                subtitle = "Agrega un resplandor suave alrededor del punto.",
                checked = settings.glow,
            ) { on -> onUpdate { it.copy(glow = on) } }
        }

        SectionCard(title = "Posición") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                positionPresets.forEach { p ->
                    FilterChip(
                        selected = settings.posX == p.x && settings.posY == p.y,
                        onClick = { onUpdate { it.copy(posX = p.x, posY = p.y) } },
                        label = { Text(p.label) },
                    )
                }
            }
            SettingSlider(
                title = "Horizontal",
                value = settings.posX,
                range = 0f..1f,
                label = { "${(it * 100).toInt()} %" },
                onPreview = { v -> live = live.copy(posX = v) },
            ) { v -> onUpdate { it.copy(posX = v) } }
            SettingSlider(
                title = "Vertical",
                value = settings.posY,
                range = 0f..1f,
                label = { "${(it * 100).toInt()} %" },
                onPreview = { v -> live = live.copy(posY = v) },
            ) { v -> onUpdate { it.copy(posY = v) } }
        }

        SectionCard(title = "Parpadeo") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BlinkPattern.entries.forEach { p ->
                    FilterChip(
                        selected = settings.pattern == p,
                        onClick = { onUpdate { it.copy(pattern = p) } },
                        label = { Text(p.label) },
                    )
                }
            }
            SettingSlider(
                title = "Velocidad (duración del ciclo)",
                value = settings.periodMs.toFloat(),
                range = LedSettings.MIN_PERIOD_MS.toFloat()..LedSettings.MAX_PERIOD_MS.toFloat(),
                label = { String.format(Locale.getDefault(), "%.1f s", it / 1000f) },
                onPreview = { v -> live = live.copy(periodMs = v.toInt()) },
            ) { v -> onUpdate { it.copy(periodMs = (v / 50).toInt() * 50) } }
            Hint("Valores bajos = parpadeo rápido. Valores altos = parpadeo lento y más descansado.")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showPicker) {
        ColorPickerDialog(
            title = "Color del LED",
            initial = settings.color,
            allowDefault = false,
            onDismiss = { showPicker = false },
        ) { c ->
            showPicker = false
            if (c != null) onUpdate { it.copy(color = c) }
        }
    }
}
