package com.offlineassistant.ai

/**
 * Тонкий JNI-мост к нативному llama.cpp (см. app/src/main/cpp/native-lib.cpp).
 * Каждый метод — блокирующий вызов в native-потоке; вызывающая сторона
 * (LlamaEngine) обязана уводить эти вызовы в фоновый диспетчер корутин.
 *
 * Все указатели на контекст представлены как Long (адрес нативной структуры),
 * чтобы не тащить сложные JNI-объекты через границу.
 */
object LlamaBridge {

    init {
        System.loadLibrary("offline_assistant_llama")
    }

    /** Загружает GGUF-модель с диска. Возвращает handle (>0) или -1 при ошибке. */
    external fun loadModel(modelPath: String, nThreads: Int, contextSize: Int): Long

    /** Выгружает модель и освобождает память. Безопасно вызывать с handle=0. */
    external fun unloadModel(handle: Long)

    /**
     * Синхронная генерация. В реальной реализации стоит завести потоковый вариант
     * с колбэком по токенам (JNI callback в Kotlin) для отзывчивого UI — здесь
     * упрощённая версия для MVP, см. README "Дальнейшие шаги".
     */
    external fun generate(
        handle: Long,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): String

    /** Быстрая проверка: сколько токенов сейчас занято в KV-cache (для отладки памяти). */
    external fun kvCacheUsage(handle: Long): Int
}
