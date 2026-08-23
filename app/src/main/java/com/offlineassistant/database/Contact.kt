package com.offlineassistant.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * п.13.9 ТЗ: "Мама = +7xxxxxxxxxx" — ассистент запоминает алиасы контактов,
 * т.к. системная адресная книга может не совпадать с тем, как пользователь
 * называет людей голосом ("мама" вместо полного имени в контактах).
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,          // то, как пользователь называет контакт голосом ("мама")
    val displayName: String,    // отображаемое имя
    val phoneNumber: String
)

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity): Long

    @Query("SELECT * FROM contacts WHERE alias LIKE '%' || :name || '%' OR displayName LIKE '%' || :name || '%' LIMIT 1")
    suspend fun findByName(name: String): ContactEntity?

    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<ContactEntity>
}

/** Алиас для читаемости в обработчиках команд. */
typealias Contact = ContactEntity
