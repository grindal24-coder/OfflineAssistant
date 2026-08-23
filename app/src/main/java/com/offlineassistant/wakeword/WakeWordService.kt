package com.offlineassistant.wakeword

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offlineassistant.R
import com.offlineassistant.speech.AndroidSpeechToText
import com.offlineassistant.ai.LlamaEngine
import com.offlineassistant.commands.CommandRouter
import com.offlineassistant.commands.IntentJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Легковесный foreground-сервис (п.9 ТЗ: RAM 50-200MB, CPU <1% в ожидании).
 * Держит только wake-word детектор; Llama НЕ загружена, пока не сработало "Эй Ассистент".
 *
 * Полный цикл после активации (п.10 ТЗ):
 *   wake word -> SpeechToText -> LlamaEngine.ask() -> CommandRouter.execute()
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var llamaEngine: LlamaEngine
    private lateinit var commandRouter: CommandRouter

    override fun onCreate() {
        super.onCreate()
        wakeWordDetector = PorcupineWakeWordDetector(this) // TODO: заменить на выбранный движок
        speechToText = AndroidSpeechToText(this)
        llamaEngine = LlamaEngine(this)
        commandRouter = CommandRouter(this, llamaEngine)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeWordDetector.start(onWakeWordDetected = ::onWakeWordTriggered)
        return START_STICKY
    }

    private fun onWakeWordTriggered() {
        speechToText.recognizeOnce(
            onResult = { text -> handleUserUtterance(text) },
            onError = { err -> android.util.Log.e("WakeWordService", "STT error: $err") }
        )
    }

    private fun handleUserUtterance(text: String) {
        scope.launch {
            val result = llamaEngine.ask(text)
            val raw = result.rawJson ?: return@launch
            val assistantIntent = runCatching { IntentJson.parse(raw) }.getOrNull() ?: return@launch
            val commandResult = commandRouter.execute(assistantIntent)

            commandResult.needsContactRegistration?.let { alias ->
                val activityIntent = Intent(this@WakeWordService, com.offlineassistant.ui.ContactRegistrationActivity::class.java).apply {
                    putExtra(com.offlineassistant.ui.ContactRegistrationActivity.EXTRA_ALIAS, alias)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(activityIntent)
            }

            // TODO: озвучить commandResult.spokenReply через TextToSpeech
            android.util.Log.i("WakeWordService", "Ответ: ${commandResult.spokenReply}")
        }
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Ассистент", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wakeword_notification_title))
            .setContentText(getString(R.string.wakeword_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        wakeWordDetector.stop()
        scope.launch { llamaEngine.unloadAll() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "wakeword_service"
        private const val NOTIFICATION_ID = 1
    }
}
