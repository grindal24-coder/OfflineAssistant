package com.offlineassistant.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.offlineassistant.ai.LlamaEngine
import com.offlineassistant.commands.CommandRouter
import com.offlineassistant.commands.IntentJson
import com.offlineassistant.speech.AndroidSpeechToText
import com.offlineassistant.wakeword.WakeWordService
import kotlinx.coroutines.launch

/**
 * Экран запуска: запрашивает необходимые рантайм-разрешения (п.13 ТЗ) и
 * запускает WakeWordService (п.9 ТЗ) в качестве foreground-сервиса.
 *
 * Кнопка "Проверить голосом" — временный путь тестирования В ОБХОД
 * wake-word детектора, который сейчас является заглушкой (см.
 * PorcupineWakeWordDetector.kt). Без этой кнопки у тебя нет вообще никакого
 * способа проверить цепочку "распознал речь -> LLM -> команда", пока
 * Picovoice/TFLite не настроен. Использует тот же LlamaEngine/CommandRouter,
 * что и WakeWordService — просто дёргает их напрямую по нажатию, а не по
 * срабатыванию ключевой фразы.
 *
 * TODO: убрать эту кнопку (или спрятать за debug-флагом) после того как
 * wake-word детектор заработает по-настоящему.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var llamaEngine: LlamaEngine
    private lateinit var commandRouter: CommandRouter

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startWakeWordService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.offlineassistant.R.layout.activity_main)

        statusText = findViewById(com.offlineassistant.R.id.statusText)
        speechToText = AndroidSpeechToText(this)
        llamaEngine = LlamaEngine(this)
        commandRouter = CommandRouter(this, llamaEngine)

        findViewById<Button>(com.offlineassistant.R.id.testButton).setOnClickListener {
            runVoiceTest()
        }

        permissionLauncher.launch(requiredPermissions)
    }

    private fun runVoiceTest() {
        statusText.text = "Слушаю..."
        speechToText.recognizeOnce(
            onResult = { recognizedText ->
                statusText.text = "Распознано: «$recognizedText»\nДумаю..."
                lifecycleScope.launch {
                    val result = llamaEngine.ask(recognizedText)
                    val raw = result.rawJson
                    if (raw == null) {
                        statusText.text = "Ошибка: ${result.error ?: "нет ответа от модели"}"
                        return@launch
                    }
                    val assistantIntent = runCatching { IntentJson.parse(raw) }.getOrNull()
                    if (assistantIntent == null) {
                        statusText.text = "Не смог распарсить ответ модели:\n$raw"
                        return@launch
                    }
                    val commandResult = commandRouter.execute(assistantIntent)
                    statusText.text = buildString {
                        append("Модель: ${result.tier}\n")
                        append("Intent: ${assistantIntent.type} (confidence ${assistantIntent.confidence})\n")
                        append("Ответ: ${commandResult.spokenReply}")
                    }

                    commandResult.needsContactRegistration?.let { alias ->
                        val activityIntent = Intent(this@MainActivity, ContactRegistrationActivity::class.java).apply {
                            putExtra(ContactRegistrationActivity.EXTRA_ALIAS, alias)
                        }
                        startActivity(activityIntent)
                    }
                }
            },
            onError = { err -> statusText.text = "Ошибка распознавания речи: $err" }
        )
    }

    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
