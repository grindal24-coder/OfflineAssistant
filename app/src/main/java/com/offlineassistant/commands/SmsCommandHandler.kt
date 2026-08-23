package com.offlineassistant.commands

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.offlineassistant.database.AppDatabase

/**
 * п.13.7 ТЗ: "Напиши маме буду через час" -> SmsManager.
 *
 * SMS — критичный intent (см. CRITICAL_INTENTS в Intent.kt), та же причина,
 * что и у CallCommandHandler: неверно выбранный контакт при неоднозначном
 * имени — это реальное сообщение не тому человеку.
 */
class SmsCommandHandler(private val context: Context) {

    private val resolver = ContactResolver(AppDatabase.getInstance(context))

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val contactName = slots["contact"] ?: return CommandResult("Не понял, кому написать.", false)
        val message = slots["message"] ?: return CommandResult("Не понял текст сообщения.", false)

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return CommandResult("Нет разрешения на отправку SMS.", false)
        }

        return when (val result = resolver.resolve(contactName)) {
            is ContactResolver.Result.NeedsRegistration -> CommandResult(
                spokenReply = "Не знаю, кто такая «$contactName». Покажу список контактов, выбери нужный.",
                success = false,
                needsContactRegistration = contactName
            )
            is ContactResolver.Result.Found -> runCatching {
                val smsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(result.contact.phoneNumber, null, message, null, null)
                CommandResult("Отправил ${result.contact.displayName}: «$message».", true)
            }.getOrElse {
                CommandResult("Не получилось отправить SMS: ${it.message}", false)
            }
        }
    }
}
