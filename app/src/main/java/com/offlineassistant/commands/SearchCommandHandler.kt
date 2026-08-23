package com.offlineassistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * п.13.8 ТЗ: "Найди информацию о погоде" -> локальный intent SEARCH,
 * дальше передаём запрос в браузер (единственный внешний, не-локальный шаг
 * во всём проекте — согласно п.17 сама генерация и распознавание остаются офлайн).
 */
class SearchCommandHandler(private val context: Context) {

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val query = slots["query"] ?: return CommandResult("Не понял, что искать.", false)

        return runCatching {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(browserIntent)
            }
            CommandResult("Ищу «$query».", true)
        }.getOrElse {
            CommandResult("Не получилось выполнить поиск: ${it.message}", false)
        }
    }
}
