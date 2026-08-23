package com.offlineassistant.commands

import android.content.Context
import com.offlineassistant.BuildConfig
import com.offlineassistant.ai.BraveWebSearchClient
import com.offlineassistant.ai.DuckDuckGoInstantAnswerClient
import com.offlineassistant.ai.LlamaEngine
import com.offlineassistant.ai.WebSearchClient

/**
 * Обработчик ASK-intent (см. IntentType.ASK в Intent.kt) — отвечает на
 * произвольный вопрос голосом, а не просто открывает браузер (в отличие от
 * SearchCommandHandler). Механизм — простой RAG: поиск -> сниппеты -> локальная
 * SMART-модель синтезирует ответ на их основе (LlamaEngine.answerWithContext).
 *
 * Деградация без сети/ключа: если поиск не настроен или недоступен —
 * честно сообщает об этом и предлагает SEARCH-фоллбэк (открыть браузер)
 * вместо того, чтобы позволить модели "угадывать" ответ без данных.
 */
class WebAnswerHandler(private val context: Context, private val llamaEngine: LlamaEngine) {

    private val webSearchClient: WebSearchClient = createDefaultClient()

    suspend fun handle(slots: Map<String, String>): CommandResult {
        val query = slots["query"] ?: return CommandResult("Не понял вопрос.", false)

        val snippets = webSearchClient.search(query)
        if (snippets.isEmpty()) {
            return CommandResult(
                spokenReply = "Не нашёл информации по запросу «$query». Могу открыть поиск в браузере, если нужно.",
                success = false
            )
        }

        val answer = llamaEngine.answerWithContext(query, snippets)
        return CommandResult(spokenReply = answer, success = true)
    }

    companion object {
        /**
         * Фабрика клиента: Brave, если настроен ключ (BuildConfig, см.
         * README "Настройка поиска с синтезом ответа"), иначе бесключевой
         * DuckDuckGo Instant Answer как более слабый fallback.
         */
        private fun createDefaultClient(): WebSearchClient =
            if (BuildConfig.BRAVE_SEARCH_API_KEY.isNotBlank()) {
                BraveWebSearchClient(BuildConfig.BRAVE_SEARCH_API_KEY)
            } else {
                DuckDuckGoInstantAnswerClient()
            }
    }
}
