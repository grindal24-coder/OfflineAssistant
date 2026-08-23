package com.offlineassistant.commands

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * п.13.6 ТЗ: "Следующий трек" / "Поставь музыку" -> MediaSession API.
 *
 * Упрощённая реализация MVP: шлём медиа-кнопки в систему (работает с любым
 * плеером, у которого активна MediaSession — Spotify, YouTube Music и т.д.).
 * Полноценная реализация — через MediaSessionManager.getActiveSessions()
 * и прямой MediaController, что требует NotificationListener-доступа
 * (уже запрашивается для п.13.4, можно переиспользовать).
 */
class MusicCommandHandler(private val context: Context) {

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val action = slots["action"]?.lowercase() ?: "play_pause"
        val keyCode = when (action) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            CommandResult("Готово.", true)
        }.getOrElse {
            CommandResult("Не получилось управлять музыкой: ${it.message}", false)
        }
    }
}
