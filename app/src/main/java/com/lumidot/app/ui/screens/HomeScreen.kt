package com.lumidot.app.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumidot.app.data.LedSettings
import com.lumidot.app.led.LedController
import com.lumidot.app.service.AlertTracker
import com.lumidot.app.ui.components.Hint
import com.lumidot.app.ui.components.LedPreview
import com.lumidot.app.ui.components.SectionCard
import com.lumidot.app.ui.components.SettingSwitch
import com.lumidot.app.ui.theme.LumiGreen
import com.lumidot.app.ui.theme.LumiWarning
import com.lumidot.app.util.Permissions

private data class PermState(
    val notifications: Boolean,
    val overlay: Boolean,
    val battery: Boolean,
) {
    companion object {
        fun read(context: Context) = PermState(
            notifications = Permissions.hasNotificationAccess(context),
            overlay = Permissions.canShowOverLockscreen(context),
            battery = Permissions.isIgnoringBatteryOptimizations(context),
        )
    }
}

@Composable
fun HomeScreen(settings: LedSettings, onUpdate: ((LedSettings) -> LedSettings) -> Unit) {
    val context = LocalContext.current
    var perms by remember { mutableStateOf(PermState.read(context)) }
    LifecycleResumeEffect(Unit) {
        perms = PermState.read(context)
        onPauseOrDispose { }
    }
    val connected by AlertTracker.listenerConnected.collectAsStateWithLifecycle()
    val alerts by AlertTracker.alerts.collectAsStateWithLifecycle()
    val skipReason by AlertTracker.lastSkipReason.collectAsStateWithLifecycle()
    var showDisclosure by remember { mutableStateOf(false) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Encabezado con estado general
        SectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedPreview(
                    settings = settings,
                    interactive = false,
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("LumiDot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    val (statusText, statusColor) = statusOf(settings, perms, connected)
                    Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                    if (perms.notifications && connected) {
                        Spacer(Modifier.height(4.dp))
                        Hint(
                            when (alerts.size) {
                                0 -> "Sin notificaciones pendientes"
                                1 -> "1 notificación pendiente"
                                else -> "${alerts.size} notificaciones pendientes"
                            }
                        )
                    }
                }
            }
            SettingSwitch(
                title = "LED de notificaciones",
                subtitle = if (settings.enabled) "Activado" else "Desactivado",
                checked = settings.enabled,
            ) { on -> onUpdate { it.copy(enabled = on) } }
            Button(
                onClick = { LedController.launch(context, preview = true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Probar LED en pantalla completa")
            }
            Hint("La prueba funciona sin conceder ningún permiso.")
            OutlinedButton(
                onClick = {
                    LedController.scheduleLockedTest(context)
                    Toast.makeText(context, "Bloqueá el teléfono ahora: el LED aparece en 5 segundos", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Probar con pantalla apagada (5 s)") }
            Hint("Comprueba que Android te deje mostrar el LED con el teléfono bloqueado, como con una notificación real.")
        }

        // Accesos
        SectionCard(title = "Accesos") {
            PermissionRow(
                title = "Acceso a notificaciones",
                granted = perms.notifications,
                description = "Necesario para detectar notificaciones nuevas y apagar el LED cuando las leés o descartás.",
                actionLabel = if (perms.notifications) "Revisar" else "Conceder",
            ) {
                if (settings.disclosureAccepted || perms.notifications) Permissions.openNotificationAccess(context)
                else showDisclosure = true
            }
            if (!perms.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Hint(
                    "Si el interruptor aparece bloqueado como \"Ajuste restringido\" (APK instalado fuera de Play), " +
                        "abrí Información de la app → menú ⋮ → \"Permitir ajustes restringidos\" y volvé a intentar."
                )
                TextButton(onClick = { Permissions.openAppDetails(context) }) { Text("Abrir información de la app") }
            }
            if (perms.notifications && !connected) {
                Hint("El servicio todavía no está conectado. Si no se conecta en unos segundos, reconectalo.")
                TextButton(onClick = { Permissions.requestRebind(context) }) { Text("Reconectar servicio") }
            }
            PermissionRow(
                title = "Mostrar sobre otras apps",
                granted = perms.overlay,
                description = "Android sólo permite que una app abra una pantalla desde segundo plano si tiene este acceso. " +
                    "Sin él, el LED no puede aparecer solo con la pantalla apagada.",
                actionLabel = if (perms.overlay) "Revisar" else "Conceder",
            ) { Permissions.openOverlaySettings(context) }
            if (Permissions.isXiaomi()) {
                PermissionRow(
                    title = "Permisos de Xiaomi (HyperOS)",
                    granted = false,
                    description = "En \"Otros permisos\" activá \"Mostrar en pantalla de bloqueo\" y " +
                        "\"Abrir ventanas nuevas mientras se ejecuta en segundo plano\". Sin ellos HyperOS " +
                        "bloquea el LED aunque los demás accesos estén concedidos.",
                    actionLabel = "Abrir",
                    optional = true,
                ) { Permissions.openXiaomiPermissions(context) }
            }
            PermissionRow(
                title = "Sin restricción de batería" + if (Permissions.isXiaomi()) "" else " (opcional)",
                granted = perms.battery,
                description = "Recomendado en marcas que cierran apps agresivamente (Xiaomi, Samsung, Huawei, etc.).",
                actionLabel = "Abrir",
                optional = true,
            ) { Permissions.openBatterySettings(context) }
        }

        // "Estabas usando el teléfono" es el comportamiento normal: no se muestra como aviso.
        if (perms.notifications && skipReason != null && skipReason != LedController.REASON_IN_USE) {
            SectionCard(title = "Último aviso") {
                Hint("El LED no se mostró en la última notificación: $skipReason.")
                if (Permissions.isXiaomi()) {
                    TextButton(onClick = { Permissions.openXiaomiPermissions(context) }) { Text("Abrir permisos de Xiaomi") }
                }
            }
        }

        SectionCard(title = "Cómo funciona") {
            InfoLine("Cuando llega una notificación con la pantalla apagada, LumiDot enciende la pantalla en negro absoluto con brillo mínimo y muestra un punto que parpadea.")
            InfoLine("En pantallas AMOLED los píxeles negros quedan apagados: sólo se ilumina el punto.")
            InfoLine("El LED se apaga cuando leés o descartás las notificaciones, al tocar la pantalla, al presionar el botón de encendido o al cumplirse el tiempo máximo.")
            InfoLine("Android no permite encender luces sin usar la pantalla ni mostrar nada mientras está totalmente apagada; por eso LumiDot usa la pantalla al mínimo.")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Acceso a notificaciones") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LumiDot necesita saber cuándo llega o se elimina una notificación para encender y apagar el LED.")
                    Text("• Sólo usa el nombre de la app y el estado de la notificación.")
                    Text("• No lee, guarda ni muestra el contenido de tus mensajes.")
                    Text("• La app no tiene permiso de Internet: ningún dato sale de tu teléfono.")
                    Text("Podés revocar el acceso en cualquier momento desde los ajustes de Android.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onUpdate { it.copy(disclosureAccepted = true) }
                    Permissions.openNotificationAccess(context)
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Ahora no") } },
        )
    }
}

private fun statusOf(settings: LedSettings, perms: PermState, connected: Boolean): Pair<String, Color> = when {
    !settings.enabled -> "Desactivado" to Color(0xFF9AA5B4)
    !perms.notifications -> "Modo prueba: falta el acceso a notificaciones" to LumiWarning
    !connected -> "Conectando con el sistema…" to LumiWarning
    !perms.overlay -> "Detectando notificaciones. Falta \"Mostrar sobre otras apps\" para encender el LED." to LumiWarning
    else -> "Activo y esperando notificaciones" to LumiGreen
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    description: String,
    actionLabel: String,
    optional: Boolean = false,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else if (optional) Icons.Filled.Info else Icons.Filled.Warning,
            contentDescription = if (granted) "Concedido" else "Pendiente",
            tint = if (granted) LumiGreen else if (optional) MaterialTheme.colorScheme.onSurfaceVariant else LumiWarning,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(description)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row {
        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
