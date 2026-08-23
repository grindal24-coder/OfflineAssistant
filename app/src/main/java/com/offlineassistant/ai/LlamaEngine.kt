package com.offlineassistant.ai

import android.content.Context
import com.offlineassistant.commands.CRITICAL_INTENTS
import com.offlineassistant.commands.IntentJson
import com.offlineassistant.commands.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Оркестрирует две модели (FAST/SMART, см. ModelTier) поверх LlamaBridge.
 *
 * Стратегия роутинга (обновлено по итогам ревью ТЗ — раньше была грубая
 * проверка "intent == UNKNOWN", теперь используется confidence + список
 * критичных intent'ов):
 *
 *  1. Любая команда сначала идёт в FAST (1B) с function-calling промптом,
 *     который просит модель вернуть JSON с полями intent, slots, confidence.
 *  2. Результат FAST принимается как окончательный, только если ОДНОВРЕМЕННО:
 *       - JSON успешно распарсился;
 *       - intent != UNKNOWN;
 *       - confidence >= CONFIDENCE_THRESHOLD;
 *       - intent НЕ входит в CRITICAL_INTENTS (звонок/SMS — см. Intent.kt).
 *  3. Иначе эскалируем в SMART (3B) — включая случай "1B уверена, но команда
 *     критичная": уверенность 1B в том, ЧТО это звонок, не гарантирует, что
 *     она верно выбрала контакта при неоднозначном имени.
 *  4. SMART выгружается сразу после ответа, если не идёт активный диалог
 *     (см. keepSmartWarm) — экономия RAM и батареи (п.11 ТЗ).
 *
 * Сознательно НЕ реализовано (по итогам ревью, см. README "Что было
 * рассмотрено и отклонено"): принудительная выгрузка FAST на время работы
 * SMART. На целевом устройстве (12GB RAM, п.2 ТЗ) пиковые ~3GB от обеих
 * моделей не являются проблемой, а выгрузка/повторная загрузка FAST добавила
 * бы задержку на каждую эскалацию без реальной выгоды. Актуально
 * пересмотреть только если целевое устройство сменится на модель с 4-6GB RAM.
 */
class LlamaEngine(private val context: Context) {

    private val mutex = Mutex()
    private var fastHandle: Long = 0L
    private var smartHandle: Long = 0L

    /** Держать ли SMART-модель прогретой между репликами активного диалога. */
    var keepSmartWarm: Boolean = false

    private fun modelFile(tier: ModelTier): File =
        File(context.getExternalFilesDir(null), "models/${tier.fileName}")

    suspend fun ensureFastLoaded(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (fastHandle > 0L) return@withLock true
            val file = modelFile(ModelTier.FAST)
            if (!file.exists()) return@withLock false
            fastHandle = LlamaBridge.loadModel(file.absolutePath, nThreads = 4, contextSize = 2048)
            fastHandle > 0L
        }
    }

    private suspend fun ensureSmartLoaded(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (smartHandle > 0L) return@withLock true
            val file = modelFile(ModelTier.SMART)
            if (!file.exists()) return@withLock false
            smartHandle = LlamaBridge.loadModel(file.absolutePath, nThreads = 4, contextSize = 4096)
            smartHandle > 0L
        }
    }

    suspend fun unloadSmart() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (smartHandle > 0L) {
                LlamaBridge.unloadModel(smartHandle)
                smartHandle = 0L
            }
        }
    }

    suspend fun unloadAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (fastHandle > 0L) { LlamaBridge.unloadModel(fastHandle); fastHandle = 0L }
            if (smartHandle > 0L) { LlamaBridge.unloadModel(smartHandle); smartHandle = 0L }
        }
    }

    /**
     * Основная точка входа: возвращает сырой текст ответа модели (обычно JSON,
     * см. commands/CommandRouter.kt для парсинга в Intent) плюс метаданные о
     * том, какая модель отвечала и почему (для отладки/логов).
     */
    suspend fun ask(userText: String, forceSmart: Boolean = false): EngineResult = withContext(Dispatchers.IO) {
        if (!forceSmart) {
            if (ensureFastLoaded()) {
                val raw = LlamaBridge.generate(
                    handle = fastHandle,
                    systemPrompt = SYSTEM_PROMPT_INTENT,
                    userPrompt = userText,
                    maxTokens = 160,
                    temperature = 0.1f
                )
                val parsed = runCatching { IntentJson.parse(raw) }.getOrNull()

                val goodEnough = parsed != null &&
                    parsed.type != IntentType.UNKNOWN &&
                    parsed.confidence >= CONFIDENCE_THRESHOLD &&
                    parsed.type !in CRITICAL_INTENTS

                if (goodEnough) {
                    return@withContext EngineResult(raw, tier = ModelTier.FAST)
                }
                // Низкая уверенность, невалидный JSON, или критичный intent
                // (звонок/SMS) — в любом из этих случаев эскалируем в SMART.
            }
        }

        if (ensureSmartLoaded()) {
            val raw = LlamaBridge.generate(
                handle = smartHandle,
                systemPrompt = SYSTEM_PROMPT_INTENT,
                userPrompt = userText,
                maxTokens = 256,
                temperature = 0.2f
            )
            if (!keepSmartWarm) unloadSmart()
            return@withContext EngineResult(raw, tier = ModelTier.SMART)
        }

        EngineResult(rawJson = null, tier = null, error = "Ни одна модель не найдена в ${modelFile(ModelTier.FAST).parent}")
    }

    /**
     * Синтезирует голосовой ответ на вопрос, используя найденные веб-сниппеты
     * как контекст (RAG-подход). Используется WebAnswerHandler для ASK-intent'ов
     * (см. IntentType.ASK). Всегда идёт через SMART (3B) — синтез связного
     * текста с опорой на источники требует больше "рассуждения", чем короткая
     * intent-классификация, для которой хватает 1B.
     *
     * Промпт намеренно требует честно сказать "не нашёл информации", если
     * сниппетов недостаточно — важно не превращать локальную LLM в источник
     * галлюцинаций поверх скудных данных поиска.
     */
    suspend fun answerWithContext(question: String, snippets: List<SearchSnippet>): String =
        withContext(Dispatchers.IO) {
            if (!ensureSmartLoaded()) {
                return@withContext "Не могу ответить — модель для сложных вопросов не найдена."
            }

            val contextBlock = if (snippets.isEmpty()) {
                "Результатов поиска нет."
            } else {
                snippets.joinToString("\n\n") { "Источник: ${it.title}\n${it.snippet}" }
            }

            val userPrompt = """
Вопрос пользователя: "$question"

Данные из поиска:
$contextBlock

Ответь на вопрос кратко (2-4 предложения), опираясь ТОЛЬКО на данные выше.
Если данных недостаточно для ответа — честно скажи, что не нашёл информацию,
не выдумывай факты. Отвечай на русском, разговорным языком для голосового ответа.
""".trim()

            val answer = LlamaBridge.generate(
                handle = smartHandle,
                systemPrompt = "Ты — голосовой ассистент. Отвечай кратко и по делу, без markdown.",
                userPrompt = userPrompt,
                maxTokens = 220,
                temperature = 0.3f
            )
            if (!keepSmartWarm) unloadSmart()
            answer
        }

    companion object {
        /** confidence ниже этого порога — считаем 1B недостаточно уверенной, эскалируем. */
        const val CONFIDENCE_THRESHOLD = 0.85

        /**
         * System-промпт для function calling. Держим намеренно жёстким и коротким —
         * 1B модель нестабильна на свободных инструкциях (см. предупреждение в README).
         * Поле confidence — самооценка модели, НЕ откалиброванная вероятность,
         * см. предупреждение в AssistantIntent.confidence (commands/Intent.kt).
         * Для продакшена рекомендуется дополнительно добавить GBNF-грамматику через
         * llama.cpp grammar sampling, чтобы гарантировать валидный JSON —
         * особенно для CALL/SMS, где парсер не должен молча проглатывать ошибку.
         */
        const val SYSTEM_PROMPT_INTENT = """
Ты — голосовой ассистент телефона. Отвечай ТОЛЬКО валидным JSON, без пояснений, без markdown, без текста до/после JSON.

Формат: {"intent": "<CALL|TIMER|REMINDER|SMS|OPEN_APP|MUSIC|SEARCH|ASK|UNKNOWN>", "confidence": <0.0-1.0>, "slots": {...}}

Поле confidence — твоя уверенность в том, что ты правильно понял(а) намерение
И все параметры (slots). Если сомневаешься хоть в чём-то (например, не уверен,
о каком контакте речь) — ставь confidence ниже 0.85.

ВАЖНО: различай SEARCH и ASK.
- SEARCH — пользователь хочет сам посмотреть в браузере ("найди сайт про..",
  "открой поиск по..").
- ASK — пользователь задал вопрос и ждёт от ТЕБЯ голосовой ответ ("какая
  погода", "кто выиграл вчера", "что такое..", "сколько будет 25 умножить на 4").

Примеры:

Запрос: "Позвони маме"
Ответ: {"intent": "CALL", "confidence": 0.97, "slots": {"contact": "мама"}}

Запрос: "Набери Диме"
Ответ: {"intent": "CALL", "confidence": 0.95, "slots": {"contact": "Дима"}}

Запрос: "Позвони Сергею" (если в запросе неясно, какому именно Сергею)
Ответ: {"intent": "CALL", "confidence": 0.6, "slots": {"contact": "Сергей"}}

Запрос: "Поставь таймер на 15 минут"
Ответ: {"intent": "TIMER", "confidence": 0.98, "slots": {"minutes": "15", "label": "Таймер"}}

Запрос: "Заведи будильник на 5 минут, чтобы проверить пиццу"
Ответ: {"intent": "TIMER", "confidence": 0.95, "slots": {"minutes": "5", "label": "Пицца"}}

Запрос: "Напомни купить хлеб завтра"
Ответ: {"intent": "REMINDER", "confidence": 0.9, "slots": {"text": "купить хлеб", "timestampMillis": ""}}

Запрос: "Напомни позвонить врачу через час"
Ответ: {"intent": "REMINDER", "confidence": 0.9, "slots": {"text": "позвонить врачу", "timestampMillis": ""}}

Запрос: "Напиши маме буду через час"
Ответ: {"intent": "SMS", "confidence": 0.96, "slots": {"contact": "мама", "message": "буду через час"}}

Запрос: "Отправь Диме сообщение выезжаю"
Ответ: {"intent": "SMS", "confidence": 0.95, "slots": {"contact": "Дима", "message": "выезжаю"}}

Запрос: "Открой Телеграм"
Ответ: {"intent": "OPEN_APP", "confidence": 0.97, "slots": {"appName": "Telegram"}}

Запрос: "Запусти Ютуб"
Ответ: {"intent": "OPEN_APP", "confidence": 0.97, "slots": {"appName": "YouTube"}}

Запрос: "Следующий трек"
Ответ: {"intent": "MUSIC", "confidence": 0.95, "slots": {"action": "next"}}

Запрос: "Поставь музыку"
Ответ: {"intent": "MUSIC", "confidence": 0.9, "slots": {"action": "play"}}

Запрос: "Останови музыку"
Ответ: {"intent": "MUSIC", "confidence": 0.95, "slots": {"action": "pause"}}

Запрос: "Найди информацию о погоде"
Ответ: {"intent": "SEARCH", "confidence": 0.92, "slots": {"query": "погода"}}

Запрос: "Какая сегодня погода в Москве"
Ответ: {"intent": "ASK", "confidence": 0.9, "slots": {"query": "погода сегодня Москва"}}

Запрос: "Кто выиграл вчерашний матч"
Ответ: {"intent": "ASK", "confidence": 0.85, "slots": {"query": "результат вчерашнего матча"}}

Запрос: "Сколько будет 25 умножить на 4"
Ответ: {"intent": "ASK", "confidence": 0.95, "slots": {"query": "25 умножить на 4"}}

Запрос: "Сколько стоит биткоин"
Ответ: {"intent": "ASK", "confidence": 0.9, "slots": {"query": "курс биткоина"}}

Запрос: "Расскажи анекдот"
Ответ: {"intent": "UNKNOWN", "confidence": 0.3, "slots": {}}

Если не уверен в намерении — верни {"intent": "UNKNOWN", "confidence": 0.0, "slots": {}}.
Отвечай только JSON, без единого лишнего слова.
""".trim()
    }
}

data class EngineResult(
    val rawJson: String?,
    val tier: ModelTier?,
    val error: String? = null
)
