package com.lumidot.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lumidot.app.data.AppFilterMode
import com.lumidot.app.data.AppRule
import com.lumidot.app.data.LedSettings
import com.lumidot.app.ui.components.ColorPickerDialog
import com.lumidot.app.ui.components.Hint
import com.lumidot.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

private data class AppEntry(val packageName: String, val label: String)

/** Carga apps con ícono en el launcher + apps que ya enviaron notificaciones. */
private suspend fun loadApps(context: Context, extraPackages: Set<String>): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val packages = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toMutableSet()
    packages += extraPackages
    packages -= context.packageName
    val collator = Collator.getInstance()
    packages.mapNotNull { pkg ->
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            AppEntry(pkg, pm.getApplicationLabel(info).toString())
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }.sortedWith { a, b -> collator.compare(a.label, b.label) }
}

private val iconCache = LruCache<String, ImageBitmap>(120)

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(sizePx, sizePx).asImageBitmap()
                        .also { iconCache.put(packageName, it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(settings: LedSettings, onUpdate: ((LedSettings) -> LedSettings) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var pickerFor by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(settings.seenPackages) {
        apps = loadApps(context, settings.seenPackages)
    }

    val filtered = remember(apps, query, settings.seenPackages) {
        val q = query.trim()
        apps.orEmpty()
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            // Primero las apps que ya enviaron notificaciones.
            .sortedByDescending { it.packageName in settings.seenPackages }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            SectionCard(title = "¿Qué apps encienden el LED?") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AppFilterMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.filterMode == mode,
                            onClick = { onUpdate { it.copy(filterMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, AppFilterMode.entries.size),
                        ) { Text(if (mode == AppFilterMode.ALL_EXCEPT_EXCLUDED) "Todas" else "Sólo elegidas") }
                    }
                }
                Hint(
                    if (settings.filterMode == AppFilterMode.ALL_EXCEPT_EXCLUDED)
                        "Todas las apps encienden el LED, salvo las que desactives abajo."
                    else
                        "Sólo las apps que actives abajo encienden el LED."
                )
                Hint("Tocá el círculo de color para asignarle un color propio a cada app.")
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("Buscar app") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
        if (apps == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        items(filtered, key = { it.packageName }) { app ->
            val rule = settings.appRules[app.packageName]
            val allowed = settings.isAppAllowed(app.packageName)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.packageName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.packageName in settings.seenPackages) Hint("Envía notificaciones")
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(rule?.color ?: settings.color).copy(alpha = if (rule?.color != null) 1f else 0.35f))
                        .clickable { pickerFor = app },
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = allowed,
                    onCheckedChange = { on ->
                        onUpdate { s ->
                            val r = s.appRules[app.packageName] ?: AppRule(app.packageName)
                            s.copy(appRules = s.appRules + (app.packageName to r.copy(enabled = on)))
                        }
                    },
                )
            }
        }
    }

    pickerFor?.let { app ->
        ColorPickerDialog(
            title = "Color para ${app.label}",
            initial = settings.appRules[app.packageName]?.color ?: settings.color,
            allowDefault = true,
            onDismiss = { pickerFor = null },
        ) { c ->
            pickerFor = null
            onUpdate { s ->
                // En modo "todas", una app sin regla está habilitada; en "sólo elegidas", asignar color la habilita.
                val r = s.appRules[app.packageName]
                    ?: AppRule(app.packageName, enabled = true)
                s.copy(appRules = s.appRules + (app.packageName to r.copy(color = c)))
            }
        }
    }
}
