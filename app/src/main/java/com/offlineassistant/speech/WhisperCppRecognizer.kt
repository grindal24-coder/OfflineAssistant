package com.offlineassistant.speech

/**
 * п.12 ТЗ: полностью офлайн вариант через whisper.cpp (small/base модель).
 *
 * Требует отдельного JNI-моста (по аналогии с ai/LlamaBridge.kt) и своей
 * native-либы — см. https://github.com/ggerganov/whisper.cpp, папка examples/android.
 *
 * Рекомендуемый план подключения:
 *  1. Добавить whisper.cpp как git submodule в ai/whisper.cpp/
 *  2. Собрать libwhisper.so через отдельный CMakeLists.txt (или расширить
 *     существующий app/src/main/cpp/CMakeLists.txt новой целью)
 *  3. Реализовать WhisperBridge.kt с методами loadModel/transcribePcm16
 *  4. Захватывать аудио через AudioRecord (16kHz mono PCM) и скармливать в transcribePcm16
 *
 * Пока не реализовано — WakeWordService по умолчанию использует
 * AndroidSpeechToText как временный вариант для разработки.
 */
class WhisperCppRecognizer {
    fun transcribe(pcm16Audio: ShortArray): String {
        throw NotImplementedError("Whisper.cpp JNI-мост ещё не подключён, см. TODO в файле")
    }
}
