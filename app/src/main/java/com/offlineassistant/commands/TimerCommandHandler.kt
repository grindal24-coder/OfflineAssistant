package com.offlineassistant.commands

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** п.13.2 ТЗ: "Поставь таймер на 15 минут" -> AlarmManager. */
class TimerCommandHandler(private val context: Context) {

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val minutes = slots["minutes"]?.toIntOrNull()
            ?: return CommandResult("Не понял, на сколько минут ставить таймер.", false)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = System.currentTimeMillis().toInt()

        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            putExtra(TimerAlarmReceiver.EXTRA_LABEL, slots["label"] ?: "Таймер")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)

        return CommandResult("Таймер на $minutes минут поставлен.", true)
    }
}

/** Срабатывает по истечении таймера — здесь можно проиграть звук/показать уведомление. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Таймер"
        // TODO: проиграть звук будильника + показать полноэкранное уведомление
        android.util.Log.i("TimerAlarmReceiver", "Таймер сработал: $label")
    }

    companion object {
        const val EXTRA_LABEL = "label"
    }
}
