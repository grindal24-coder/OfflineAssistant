package com.offlineassistant.wakeword

import android.content.Context

/**
 * Реализация на движке Picovoice Porcupine (см. п.9 ТЗ, вариант 1).
 *
 * ВАЖНО (лицензия): Porcupine бесплатен только для личного/некоммерческого
 * использования и лимитированного числа активаций в бесплатном тире —
 * проверь актуальные условия на picovoice.ai перед релизом.
 *
 * Для активации:
 *  1. Раскомментировать зависимость в app/build.gradle.kts
 *  2. Получить AccessKey на console.picovoice.ai
 *  3. Обучить кастомное ключевое слово "Эй Ассистент" через Porcupine Console
 *     (готовых русскоязычных wake-word моделей у Porcupine нет "из коробки").
 *     ВАЖНО: используй именно двухсловную фразу "Эй Ассистент", а не короткое
 *     "Эй" — короткие слова дают много ложных срабатываний (телевизор,
 *     обычная речь с похожим звучанием).
 *  4. Положить .ppn файл в assets/ и подставить путь ниже
 *
 * Пока это TODO-заглушка, чтобы проект компилировался без внешних SDK-ключей.
 */
class PorcupineWakeWordDetector(private val context: Context) : WakeWordDetector {

    override fun start(onWakeWordDetected: () -> Unit): Boolean {
        // TODO: инициализировать PorcupineManager с кастомной .ppn моделью "Эй Ассистент"
        android.util.Log.w("WakeWord", "PorcupineWakeWordDetector: не реализовано, см. TODO в файле")
        return false
    }

    override fun stop() {
        // TODO: porcupineManager?.delete()
    }
}
