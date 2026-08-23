package com.offlineassistant.wakeword

/**
 * Абстракция над движком поиска ключевой фразы "Эй Ассистент" (п.8 ТЗ,
 * фраза изменена с исходной короткой "Эй" — см. PorcupineWakeWordDetector.kt).
 * Позволяет безболезненно менять реализацию: Porcupine / TFLite / Vosk-KWS
 * (см. варианты в п.9 ТЗ) без переписывания WakeWordService.
 *
 * Реализация ДОЛЖНА держать RAM в пределах 50-200 MB и не грузить Llama.
 */
interface WakeWordDetector {
    /** Инициализация модели детектора. Возвращает false, если модель недоступна. */
    fun start(onWakeWordDetected: () -> Unit): Boolean

    fun stop()
}
