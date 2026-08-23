package com.offlineassistant.commands

import android.content.Context
import android.content.pm.ApplicationInfo

/** п.13.5 ТЗ: "Открой Telegram" -> PackageManager. */
class OpenAppCommandHandler(private val context: Context) {

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val appName = slots["appName"] ?: return CommandResult("Не понял, какое приложение открыть.", false)
        val pm = context.packageManager

        // Простой поиск по видимому имени приложения среди установленных.
        // Для точности лучше держать в Room кэш "название -> packageName" (см. database/).
        val match = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.enabled }
            .firstOrNull { info ->
                pm.getApplicationLabel(info).toString().contains(appName, ignoreCase = true)
            }
            ?: return CommandResult("Не нашёл приложение «$appName».", false)

        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return CommandResult("У «$appName» нет экрана запуска.", false)

        return runCatching {
            context.startActivity(launchIntent)
            CommandResult("Открываю ${pm.getApplicationLabel(match)}.", true)
        }.getOrElse {
            CommandResult("Не получилось открыть приложение: ${it.message}", false)
        }
    }
}
