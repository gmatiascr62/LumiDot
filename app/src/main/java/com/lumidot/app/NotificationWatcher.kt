package com.lumidot.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationWatcher : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!getSharedPreferences("lumidot", MODE_PRIVATE).getBoolean("enabled", true)) return
        // Android restricts launching activities from background notification listeners.
        // This service receives events, but does not launch the LED automatically yet.
        // The initial release offers a manual LED preview from the main screen.
    }
}
