package com.offlineassistant.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** п.13.9 ТЗ: "прошлые команды" — история для будущего контекста/аналитики. */
@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userText: String,
    val intentType: String,
    val success: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Dao
interface CommandHistoryDao {
    @Insert
    suspend fun insert(entry: CommandHistoryEntity): Long

    @Query("SELECT * FROM command_history ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int = 20): List<CommandHistoryEntity>
}
