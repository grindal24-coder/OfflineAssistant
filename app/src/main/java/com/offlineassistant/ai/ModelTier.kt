package com.offlineassistant.ai

/**
 * Двухуровневая схема моделей (см. обсуждение с пользователем поверх ТЗ):
 *
 * FAST  — Llama 3.2 1B Instruct Q4_K_M (~0.7-0.9 GB). Используется по умолчанию
 *         для простых команд с чёткими intent'ами: звонок, таймер, открыть
 *         приложение, музыка. Держится в памяти дольше (дешёво выгружать/грузить).
 *
 * SMART — Llama 3.2 3B Instruct Q4_K_M (~1.8-2.2 GB). Подключается только когда
 *         FAST-модель вернула low-confidence intent (UNKNOWN) или пользователь
 *         ведёт связный диалог/просит что-то сложное (поиск, рассуждение,
 *         многошаговые команды). Выгружается из RAM после завершения ответа.
 *
 * Пиковое потребление при одновременной работе (кратковременно, во время
 * переключения) — около 3 GB, что укладывается в 12 GB устройства из ТЗ (п.2).
 */
enum class ModelTier(val fileName: String, val approxRamMb: Int) {
    FAST(fileName = "llama-3.2-1b-instruct.Q4_K_M.gguf", approxRamMb = 900),
    SMART(fileName = "llama-3.2-3b-instruct.Q4_K_M.gguf", approxRamMb = 2200)
}
