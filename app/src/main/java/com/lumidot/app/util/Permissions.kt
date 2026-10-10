package com.lumidot.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.lumidot.app.service.LumiNotificationListener

/** Estado de los accesos especiales. Ninguno es obligatorio para abrir la app o probar el LED. */
object Permissions {

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun canShowOverLockscreen(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    fun openNotificationAccess(context: Context) {
        val component = LumiNotificationListener.componentName(context)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else null
        if (detail == null || !context.tryStart(detail)) {
            context.tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        if (!context.tryStart(intent)) context.tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    /** Lista de optimización de batería (no usa REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). */
    fun openBatterySettings(context: Context) {
        // En MIUI/HyperOS el ajuste real ("Ahorro de batería → Sin restricciones") está en la
        // información de la app; la lista genérica de Android no siempre lo refleja.
        if (isXiaomi()) {
            openAppDetails(context)
            return
        }
        if (!context.tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppDetails(context)
    }

    /** Información de la app: desde aquí se habilitan los "ajustes restringidos" en Android 13+. */
    fun openAppDetails(context: Context) {
        context.tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    /** Xiaomi, Redmi y POCO (MIUI/HyperOS) tienen permisos propios además de los de Android. */
    fun isXiaomi(): Boolean =
        Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco") ||
            Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    /**
     * Abre "Otros permisos" de MIUI/HyperOS, donde están "Mostrar en pantalla de
     * bloqueo" y "Abrir ventanas nuevas en segundo plano". Si no existe, abre la
     * información de la app.
     */
    fun openXiaomiPermissions(context: Context) {
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", context.packageName)
        val withClass = Intent(editor).setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        )
        if (!context.tryStart(withClass) && !context.tryStart(editor)) openAppDetails(context)
    }

    fun requestRebind(context: Context) {
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                LumiNotificationListener.componentName(context)
            )
        } catch (_: RuntimeException) {
        }
    }

    private fun Context.tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
