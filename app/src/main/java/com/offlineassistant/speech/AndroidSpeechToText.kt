package com.offlineassistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * п.12 ТЗ: реализация через встроенный Android SpeechRecognizer API.
 *
 * ВНИМАНИЕ: на многих устройствах системный SpeechRecognizer уходит в облако
 * Google (не полностью офлайн), что противоречит п.17 "не отправляет голос
 * в облако". Для полностью офлайн-режима нужен whisper.cpp (см.
 * WhisperCppRecognizer.kt) — используй его в релизной сборке, а этот класс
 * оставь как быстрый fallback для разработки/эмулятора.
 */
class AndroidSpeechToText(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun recognizeOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("SpeechRecognizer недоступен на устройстве")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text != null) onResult(text) else onError("Пустой результат распознавания")
                }
                override fun onError(error: Int) = onError("Код ошибки STT: $error")

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
        }
        recognizer?.startListening(intent)
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}
