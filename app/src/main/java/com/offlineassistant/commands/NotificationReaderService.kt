package com.offlineassistant.commands

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * п.13.4 ТЗ: чтение/пересказ уведомлений.
 * Доступ выдаётся вручную пользователем: Настройки -> Специальные возможности ->
 * Доступ к уведомлениям. Программно запросить нельзя — только открыть нужный экран
 * настроек через ACTION_NOTIFICATION_LISTENER_SETTINGS.
 */
class NotificationReaderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getString("android.title") ?: return
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: return

        // TODO: положить в буфер последних уведомлений (Room) для команды
        // "прочитай последнее сообщение" и/или прокинуть в LlamaEngine для пересказа.
        android.util.Log.d("NotificationReader", "$title: $text")
    }
}
