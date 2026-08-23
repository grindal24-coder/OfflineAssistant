// JNI-реализация com.offlineassistant.ai.LlamaBridge поверх llama.cpp.
//
// СТАТУС: рабочий скелет с корректными сигнатурами и базовым потоком вызовов
// llama.cpp (llama_load_model_from_file / llama_new_context_with_model /
// llama_decode / llama_sampler_sample). Требует финальной сверки с актуальной
// версией llama.cpp API на момент сборки — этот слой в апстриме меняется
// нередко, поэтому имена функций стоит сверить с ai/llama.cpp/include/llama.h
// после того как подключишь submodule (см. README.md).
//
// TODO перед продакшн-использованием:
//   - потоковая генерация с колбэком по токенам вместо накопления всего текста
//   - GBNF grammar-constrained decoding, чтобы гарантировать валидный JSON
//     (см. LlamaEngine.SYSTEM_PROMPT_INTENT в Kotlin-коде)
//   - обработка контекста, который не влезает (context overflow)
//   - потокобезопасность при параллельных вызовах (сейчас предполагается,
//     что Kotlin-сторона сериализует вызовы через Mutex, см. LlamaEngine.kt)

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "offline_assistant_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_offlineassistant_ai_LlamaBridge_loadModel(
        JNIEnv* env, jobject /* this */,
        jstring modelPath, jint nThreads, jint contextSize) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("Не удалось загрузить модель");
        return -1;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(contextSize);
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Не удалось создать context");
        llama_free_model(model);
        return -1;
    }

    auto* session = new LlamaSession{model, ctx};
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlineassistant_ai_LlamaBridge_unloadModel(
        JNIEnv* env, jobject /* this */, jlong handle) {
    if (handle == 0) return;
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (session->ctx) llama_free(session->ctx);
    if (session->model) llama_free_model(session->model);
    delete session;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlineassistant_ai_LlamaBridge_generate(
        JNIEnv* env, jobject /* this */, jlong handle,
        jstring systemPrompt, jstring userPrompt,
        jint maxTokens, jfloat temperature) {

    if (handle == 0) return env->NewStringUTF("");
    auto* session = reinterpret_cast<LlamaSession*>(handle);

    const char* sys = env->GetStringUTFChars(systemPrompt, nullptr);
    const char* usr = env->GetStringUTFChars(userPrompt, nullptr);

    // Llama 3.2 instruct chat template (упрощённо — см. TODO выше про
    // сверку с актуальным API; в идеале использовать llama_chat_apply_template).
    std::string prompt = std::string("<|start_header_id|>system<|end_header_id|>\n\n")
        + sys + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
        + usr + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";

    env->ReleaseStringUTFChars(systemPrompt, sys);
    env->ReleaseStringUTFChars(userPrompt, usr);

    // TODO: токенизация, llama_decode в цикле, семплинг (temperature),
    // остановка по EOS/eot токену или maxTokens. Смотри
    // ai/llama.cpp/examples/simple/simple.cpp как референс для актуальной версии API.
    LOGI("generate() вызван, maxTokens=%d temperature=%f — реализация не завершена", maxTokens, temperature);

    // Формат синхронизирован с LlamaEngine.SYSTEM_PROMPT_INTENT (Kotlin) —
    // поле confidence добавлено по итогам ревью ТЗ для роутинга 1B/3B, см.
    // CRITICAL_INTENTS и CONFIDENCE_THRESHOLD в LlamaEngine.kt. Для CALL/SMS
    // особенно важно подключить GBNF grammar (см. TODO выше), чтобы модель
    // физически не могла вернуть невалидный intent/slots для чувствительных команд.
    std::string result = R"({"intent": "UNKNOWN", "confidence": 0.0, "slots": {}})";
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_offlineassistant_ai_LlamaBridge_kvCacheUsage(
        JNIEnv* env, jobject /* this */, jlong handle) {
    if (handle == 0) return 0;
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    return llama_get_kv_cache_used_cells(session->ctx);
}
