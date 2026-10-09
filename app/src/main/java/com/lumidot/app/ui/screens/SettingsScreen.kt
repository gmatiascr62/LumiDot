package com.lumidot.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumidot.app.BuildConfig
import com.lumidot.app.data.LedSettings
import com.lumidot.app.ui.components.Hint
import com.lumidot.app.ui.components.SectionCard
import com.lumidot.app.ui.components.SettingSlider
import com.lumidot.app.ui.components.SettingSwitch
import java.util.Locale

private fun formatMinutes(m: Int) = String.format(Locale.getDefault(), "%02d:%02d", m / 60, m % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: LedSettings, onUpdate: ((LedSettings) -> LedSettings) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "Batería y pantalla") {
            Text("Tiempo máximo del LED", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 15, 30, 60, 0).forEach { m ->
                    FilterChip(
                        selected = settings.maxDurationMin == m,
                        onClick = { onUpdate { it.copy(maxDurationMin = m) } },
                        label = { Text(if (m == 0) "Sin límite" else "$m min") },
                    )
                }
            }
            Hint("Pasado este tiempo la pantalla se apaga sola. El LED vuelve con la próxima notificación.")
            SettingSlider(
                title = "Brillo de pantalla",
                value = settings.screenBrightness * 100f,
                range = 1f..40f,
                label = { "${it.toInt()} %" },
            ) { v -> onUpdate { it.copy(screenBrightness = v / 100f) } }
            Hint("Más bajo = menos consumo. En algunos equipos un brillo muy bajo puede verse apagado al sol.")
            SettingSwitch(
                title = "Protección contra quemado",
                subtitle = "Desplaza el punto unos píxeles cada minuto (recomendado en AMOLED).",
                checked = settings.burnInShift,
            ) { on -> onUpdate { it.copy(burnInShift = on) } }
            Text("No mostrar con batería baja", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 10, 15, 20, 30).forEach { p ->
                    FilterChip(
                        selected = settings.lowBatteryCutoff == p,
                        onClick = { onUpdate { it.copy(lowBatteryCutoff = p) } },
                        label = { Text(if (p == 0) "Siempre" else "< $p %") },
                    )
                }
            }
        }

        SectionCard(title = "Filtros") {
            SettingSwitch(
                title = "Respetar No molestar",
                subtitle = "No encender el LED para notificaciones que Android silencia.",
                checked = settings.respectDnd,
            ) { on -> onUpdate { it.copy(respectDnd = on) } }
            SettingSwitch(
                title = "Ignorar notificaciones silenciosas",
                subtitle = "Sólo las notificaciones que suenan o vibran encienden el LED.",
                checked = settings.ignoreSilent,
            ) { on -> onUpdate { it.copy(ignoreSilent = on) } }
            SettingSwitch(
                title = "No mostrar durante llamadas",
                subtitle = "Evita encender la pantalla con el teléfono en la oreja.",
                checked = settings.skipInCall,
            ) { on -> onUpdate { it.copy(skipInCall = on) } }
        }

        SectionCard(title = "Horario silencioso") {
            SettingSwitch(
                title = "Activar horario silencioso",
                subtitle = "El LED no se enciende en este rango horario.",
                checked = settings.quietHoursEnabled,
            ) { on -> onUpdate { it.copy(quietHoursEnabled = on) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = settings.quietHoursEnabled,
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            onUpdate { it.copy(quietStart = h * 60 + m) }
                        }, settings.quietStart / 60, settings.quietStart % 60, true).show()
                    },
                ) { Text("Desde ${formatMinutes(settings.quietStart)}") }
                OutlinedButton(
                    enabled = settings.quietHoursEnabled,
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            onUpdate { it.copy(quietEnd = h * 60 + m) }
                        }, settings.quietEnd / 60, settings.quietEnd % 60, true).show()
                    },
                ) { Text("Hasta ${formatMinutes(settings.quietEnd)}") }
            }
        }

        SectionCard(title = "Privacidad") {
            Hint("• LumiDot no tiene permiso de Internet: no puede enviar datos a ningún servidor.")
            Hint("• No lee ni guarda el texto de tus notificaciones; sólo sabe qué app la envió y si sigue pendiente.")
            Hint("• La configuración se guarda únicamente en este teléfono y se excluye de las copias de seguridad.")
            Hint("• Sin publicidad, sin analíticas y sin cuentas.")
        }

        SectionCard(title = "Limitaciones de Android") {
            Hint("• El LED necesita encender la pantalla (en negro y al mínimo): Android no permite dibujar con la pantalla totalmente apagada.")
            Hint("• Para aparecer solo con la pantalla apagada, Android exige el acceso \"Mostrar sobre otras apps\".")
            Hint("• Algunos fabricantes cierran apps en segundo plano; si el LED deja de funcionar, quitá la restricción de batería.")
        }

        Hint("LumiDot ${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(8.dp))
    }
}
