package com.offlineassistant.commands

import org.json.JSONObject

/**
 * SEARCH vs ASK — разные сценарии (добавлено по вопросу "может ли ассистент
 * отвечать как Gemini/Perplexity с поиском"):
 *
 *  - SEARCH: пользователь хочет сам посмотреть — "найди сайт..", "открой
 *    поиск про.." -> просто открываем браузер с запросом (SearchCommandHandler).
 *  - ASK: пользователь задал вопрос и ждёт ГОЛОСОВОЙ ОТВЕТ — "какая погода",
 *    "кто выиграл вчера", "что такое..", "сколько будет.." -> WebAnswerHandler
 *    достаёт сниппеты через поисковое API и просит SMART-модель синтезировать
 *    короткий ответ на их основе. См. commands/WebAnswerHandler.kt.
 *    Требует INTERNET и настроенного ключа поискового API (опционально,
 *    см. ai/WebSearchClient.kt) — без него ASK деградирует до SEARCH.
 */
enum class IntentType {
    CALL, TIMER, REMINDER, SMS, OPEN_APP, MUSIC, SEARCH, ASK, UNKNOWN
}

/**
 * Intent'ы, для которых цена ошибки высока (реальное действие с необратимыми
 * или чувствительными последствиями — звонок незнакомцу, отправка SMS не тому
 * человеку и т.п.). Для них LlamaEngine ВСЕГДА проверяет через 3B-модель,
 * даже если 1B вернула высокий confidence — см. LlamaEngine.ask().
 *
 * По итогам ревью ТЗ: "если команда звонок/SMS/деньги/удаление — всегда
 * проверять через 3B", т.к. 1B может уверенно ошибиться (например, неверно
 * выбрать контакта при неоднозначном имени).
 */
val CRITICAL_INTENTS = setOf(IntentType.CALL, IntentType.SMS)

data class AssistantIntent(
    val type: IntentType,
    val slots: Map<String, String>,
    /**
     * Самооценка модели в диапазоне 0.0-1.0. НЕ является откалиброванной
     * вероятностью в строгом смысле — это просто число, которое модель
     * генерирует по инструкции в промпте (см. SYSTEM_PROMPT_INTENT). Полезно
     * как эвристика для роутинга 1B/3B, но не стоит доверять ему как точной
     * метрике. Дефолт 0.0, если модель не вернула поле — это осознанно
     * трактуется как "низкая уверенность" (безопаснее эскалировать лишний раз,
     * чем пропустить ошибку).
     */
    val confidence: Double = 0.0
)

/**
 * Парсер строгого JSON-контракта, который LLM обязана вернуть
 * (см. LlamaEngine.SYSTEM_PROMPT_INTENT).
 *
 * ВАЖНО: модели 1B/3B без grammar-constrained decoding иногда добавляют
 * лишний текст вокруг JSON или ломают экранирование — парсер намеренно
 * терпимый (ищет первую { и последнюю }), но это временное решение.
 * Рекомендуется заменить на llama.cpp GBNF grammar, гарантирующую валидный JSON
 * — особенно важно для CRITICAL_INTENTS, где сломанный парсинг не должен
 * тихо привести к неверному действию.
 */
object IntentJson {
    fun parse(raw: String): AssistantIntent {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start != -1 && end != -1 && end > start) { "JSON не найден в ответе модели: $raw" }

        val json = JSONObject(raw.substring(start, end + 1))
        val intentStr = json.optString("intent", "UNKNOWN").uppercase()
        val type = runCatching { IntentType.valueOf(intentStr) }.getOrDefault(IntentType.UNKNOWN)
        val confidence = json.optDouble("confidence", 0.0).let { if (it.isNaN()) 0.0 else it }

        val slots = mutableMapOf<String, String>()
        json.optJSONObject("slots")?.let { slotsJson ->
            slotsJson.keys().forEach { key -> slots[key] = slotsJson.optString(key) }
        }
        return AssistantIntent(type, slots, confidence)
    }
}
