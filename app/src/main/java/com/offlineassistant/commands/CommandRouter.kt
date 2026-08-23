package com.offlineassistant.commands

import android.content.Context
import com.offlineassistant.ai.LlamaEngine

/**
 * Результат выполнения команды — то, что ассистент должен озвучить/показать.
 *
 * needsContactRegistration заполняется, когда CALL/SMS упёрлись в неизвестный
 * алиас контакта (см. ContactResolver.Result.NeedsRegistration) — вызывающая
 * сторона (WakeWordService/UI) должна в этом случае открыть
 * ui/ContactRegistrationActivity.kt с этим алиасом, чтобы пользователь привязал
 * контакт один раз, а не при каждом вызове.
 */
data class CommandResult(
    val spokenReply: String,
    val success: Boolean,
    val needsContactRegistration: String? = null
)

/**
 * Единая точка входа: получает распарсенный AssistantIntent (см. Intent.kt)
 * и делегирует конкретному обработчику из п.13 ТЗ.
 *
 * Принимает готовый LlamaEngine извне (а не создаёт свой) — WebAnswerHandler
 * (ASK-intent) переиспользует ту же SMART-модель для синтеза ответа, что и
 * основной цикл intent-классификации, чтобы не плодить несколько загруженных
 * инстансов модели одновременно.
 */
class CommandRouter(private val context: Context, llamaEngine: LlamaEngine) {

    private val callHandler = CallCommandHandler(context)
    private val timerHandler = TimerCommandHandler(context)
    private val reminderHandler = ReminderCommandHandler(context)
    private val smsHandler = SmsCommandHandler(context)
    private val openAppHandler = OpenAppCommandHandler(context)
    private val musicHandler = MusicCommandHandler(context)
    private val searchHandler = SearchCommandHandler(context)
    private val webAnswerHandler = WebAnswerHandler(context, llamaEngine)

    suspend fun execute(intent: AssistantIntent): CommandResult = when (intent.type) {
        IntentType.CALL -> callHandler.handle(intent.slots)
        IntentType.TIMER -> timerHandler.handle(intent.slots)
        IntentType.REMINDER -> reminderHandler.handle(intent.slots)
        IntentType.SMS -> smsHandler.handle(intent.slots)
        IntentType.OPEN_APP -> openAppHandler.handle(intent.slots)
        IntentType.MUSIC -> musicHandler.handle(intent.slots)
        IntentType.SEARCH -> searchHandler.handle(intent.slots)
        IntentType.ASK -> webAnswerHandler.handle(intent.slots)
        IntentType.UNKNOWN -> CommandResult("Не понял команду, попробуй переформулировать.", success = false)
    }
}
