package com.offlineassistant.commands

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.offlineassistant.database.AppDatabase
import com.offlineassistant.database.ReminderEntity

/**
 * п.13.3 ТЗ: "Напомни купить хлеб завтра" -> AlarmManager + Room.
 * Разбор времени ("завтра", "через час" и т.п.) — намеренно упрощён, слот
 * timestampMillis ожидается уже нормализованным моделью/промптом; для MVP
 * можно временно fallback'ать на "через 24 часа", если слот пуст (см. TODO).
 */
class ReminderCommandHandler(private val context: Context) {

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val text = slots["text"] ?: return CommandResult("Не понял, о чём напомнить.", false)
        val whenMillis = slots["timestampMillis"]?.toLongOrNull()
            ?: (System.currentTimeMillis() + 24 * 60 * 60_000L) // TODO: нормальный парсинг дат

        val dao = AppDatabase.getInstance(context).reminderDao()
        val id = dao.insert(ReminderEntity(text = text, triggerAtMillis = whenMillis))

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
            putExtra(ReminderAlarmReceiver.EXTRA_TEXT, text)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pendingIntent)

        return CommandResult("Напомню: $text.", true)
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        // TODO: показать уведомление с текстом напоминания
        android.util.Log.i("ReminderAlarmReceiver", "Напоминание: $text")
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TEXT = "text"
    }
}
