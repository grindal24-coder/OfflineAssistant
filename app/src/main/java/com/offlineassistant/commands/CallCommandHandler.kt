package com.offlineassistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.offlineassistant.database.AppDatabase

/**
 * п.13.1 ТЗ: "Позвони маме" -> Telephony API.
 * Требует CALL_PHONE + READ_CONTACTS (задекларированы в манифесте, запрашиваются в рантайме).
 *
 * CALL — критичный intent (см. CRITICAL_INTENTS в Intent.kt): LlamaEngine
 * всегда проверяет его через 3B, даже если 1B была уверена, потому что
 * неверный выбор контакта при неоднозначном имени — это реальный звонок не
 * тому человеку, а не просто неверный текстовый ответ.
 */
class CallCommandHandler(private val context: Context) {

    private val resolver = ContactResolver(AppDatabase.getInstance(context))

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val contactName = slots["contact"] ?: return CommandResult("Не расслышал, кому звонить.", false)

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return CommandResult("Нет разрешения на звонки.", false)
        }

        return when (val result = resolver.resolve(contactName)) {
            is ContactResolver.Result.NeedsRegistration -> CommandResult(
                spokenReply = "Не знаю, кто такая «$contactName». Покажу список контактов, выбери нужный.",
                success = false,
                needsContactRegistration = contactName
            )
            is ContactResolver.Result.Found -> runCatching {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${result.contact.phoneNumber}"))
                callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(callIntent)
                CommandResult("Звоню ${result.contact.displayName}.", true)
            }.getOrElse {
                CommandResult("Не получилось начать звонок: ${it.message}", false)
            }
        }
    }
}
