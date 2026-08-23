package com.offlineassistant.commands

import com.offlineassistant.database.AppDatabase
import com.offlineassistant.database.ContactEntity

/**
 * Резолвит голосовой алиас ("мама") в реальный контакт.
 *
 * По итогам ревью ТЗ: раньше CallCommandHandler/SmsCommandHandler при
 * ненайденном контакте просто возвращали ошибку "не нашёл контакт" и на этом
 * всё заканчивалось — пользователю приходилось открывать приложение и
 * вручную звонить. Теперь при промахе резолвер возвращает NeedsRegistration,
 * и вызывающая сторона (CallCommandHandler/SmsCommandHandler) может показать
 * пользователю экран привязки (см. ui/ContactRegistrationActivity.kt),
 * где он выбирает контакт из системной адресной книги и сохраняет алиас.
 * После этого команда "Позвони маме" будет резолвиться мгновенно из Room.
 */
class ContactResolver(private val db: AppDatabase) {

    sealed class Result {
        data class Found(val contact: ContactEntity) : Result()
        data class NeedsRegistration(val alias: String) : Result()
    }

    suspend fun resolve(alias: String): Result {
        val contact = db.contactDao().findByName(alias)
        return if (contact != null) Result.Found(contact) else Result.NeedsRegistration(alias)
    }
}
