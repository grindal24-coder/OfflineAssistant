// JNI-реализация com.offlineassistant.ai.LlamaBridge поверх llama.cpp.
//
// СТАТУС: полная реализация generate() (токенизация -> decode-цикл ->
// семплинг -> детокенизация -> остановка по EOG/maxTokens), написана под
// API llama.cpp образца 2024-2025 (после разделения llama_vocab от
// llama_model и перехода на sampler-chain API). ЭТО НЕ ГАРАНТИРОВАННО
// СКОМПИЛИРУЕТСЯ без сверки с той версией submodule'а, которую ты реально
// подключишь — llama.cpp довольно активно меняет сигнатуры между релизами.
//
// Если сборка упадёт с ошибками "no member named ..." или "too few/many
// arguments" — почти наверняка дело в версии API. Порядок действий:
//   1. Открой ai/llama.cpp/include/llama.h после `git submodule update`.
//   2. Найди актуальные сигнатуры функций, перечисленных в разделе
//      "ФУНКЦИИ, ЧУВСТВИТЕЛЬНЫЕ К ВЕРСИИ" ниже.
//   3. Поправь конкретно эти места — остальная логика (цикл генерации,
//      остановка, детокенизация) меняться не должна.
// Альтернатива — закрепить submodule на конкретном теге релиза, под который
// этот код написан (см. README.md, раздел "Подключить llama.cpp" — там
// стоит добавить `git checkout <tag>` внутри ai/llama.cpp на стабильный тег
// вместо `main`, чтобы API не уезжал из-под ног при случайном update).
//
// ФУНКЦИИ, ЧУВСТВИТЕЛЬНЫЕ К ВЕРСИИ:
//   llama_model_get_vocab, llama_vocab_n_tokens, llama_tokenize,
//   llama_batch_get_one, llama_sampler_chain_init/_add, llama_sampler_sample,
//   llama_vocab_is_eog, llama_token_to_piece.
//
// TODO, оставшиеся сознательно (не блокируют базовую работу):
//   - потоковая генерация с колбэком по токенам вместо накопления всей
//     строки перед возвратом в Kotlin (сейчас Kotlin ждёт весь ответ разом —
//     для intent-JSON это нормально, для длинных ASK-ответов будет заметная пауза)
//   - GBNF grammar-constrained decoding для гарантированно валидного JSON
//     (см. LlamaEngine.SYSTEM_PROMPT_INTENT в Kotlin-коде) — сейчас модель
//     теоретически может выдать невалидный JSON, парсер на Kotlin-стороне
//     это только частично прощает
//   - обработка переполнения контекста (n_ctx) на длинных диалогах

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
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
    // На старых версиях llama.cpp функция называется llama_load_model_from_file,
    // на новых — llama_model_load_from_file (старое имя оставлено как
    // deprecated-обёртка в части релизов). Если компилятор ругается на
    // отсутствие символа — попробуй заменить на llama_model_load_from_file.
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
    llama_model* model = session->model;
    llama_context* ctx = session->ctx;

    const char* sys = env->GetStringUTFChars(systemPrompt, nullptr);
    const char* usr = env->GetStringUTFChars(userPrompt, nullptr);

    // Llama 3.2 instruct chat template. Захардкожено под конкретный формат
    // Llama 3.x, а не через llama_chat_apply_template — потому что нам нужен
    // строго этот шаблон под конкретную модель, а не универсальный
    // автоопределяемый (меньше сюрпризов при отладке).
    std::string prompt = std::string("<|start_header_id|>system<|end_header_id|>\n\n")
        + sys + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
        + usr + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";

    env->ReleaseStringUTFChars(systemPrompt, sys);
    env->ReleaseStringUTFChars(userPrompt, usr);

    const llama_vocab* vocab = llama_model_get_vocab(model);

    // --- Токенизация промпта ---
    const int n_prompt_max = static_cast<int>(prompt.size()) + 32; // с запасом
    std::vector<llama_token> prompt_tokens(n_prompt_max);
    int n_prompt_tokens = llama_tokenize(
        vocab,
        prompt.c_str(), static_cast<int32_t>(prompt.size()),
        prompt_tokens.data(), n_prompt_max,
        /*add_special=*/true, /*parse_special=*/true
    );
    if (n_prompt_tokens < 0) {
        // llama_tokenize возвращает отрицательное число = требуемый размер буфера
        prompt_tokens.resize(-n_prompt_tokens);
        n_prompt_tokens = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
            true, true
        );
    }
    prompt_tokens.resize(n_prompt_tokens);

    // --- Прогон промпта через decode (заполняем KV-cache) ---
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt_tokens);
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode() провалился на промпте");
        return env->NewStringUTF("");
    }

    // --- Настройка семплера ---
    // Температура берётся из аргумента (LlamaEngine.kt передаёт 0.1 для
    // FAST/intent-классификации и 0.2-0.3 для SMART/синтеза ответа —
    // низкая температура держит JSON стабильным).
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // --- Цикл генерации ---
    std::string result;
    result.reserve(maxTokens * 4); // грубая оценка байт на токен для кириллицы/латиницы

    llama_token new_token = 0;
    int n_generated = 0;
    int n_past = n_prompt_tokens;

    char piece_buf[256];

    while (n_generated < maxTokens) {
        new_token = llama_sampler_sample(sampler, ctx, -1);
        llama_sampler_accept(sampler, new_token);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break; // модель сама решила закончить (EOS/EOT токен)
        }

        int piece_len = llama_token_to_piece(
            vocab, new_token, piece_buf, sizeof(piece_buf),
            /*lstrip=*/0, /*special=*/false
        );
        if (piece_len > 0) {
            result.append(piece_buf, piece_len);
        }

        // Ранняя остановка: как только в накопленном тексте появилась
        // закрывающая фигурная скобка JSON-объекта — дальше можно не
        // генерировать (для intent-классификации модель иногда продолжает
        // "болтать" после JSON, хотя промпт просит этого не делать).
        if (!result.empty() && result.find('}') != std::string::npos &&
            result.find('{') != std::string::npos) {
            size_t open = result.find('{');
            size_t close = result.rfind('}');
            if (close > open) {
                // JSON похож на завершённый — прекращаем досрочно
                break;
            }
        }

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx, next_batch) != 0) {
            LOGE("llama_decode() провалился в цикле генерации");
            break;
        }
        n_past++;
        n_generated++;
    }

    llama_sampler_free(sampler);

    LOGI("generate() сгенерировал %d токенов", n_generated);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_offlineassistant_ai_LlamaBridge_kvCacheUsage(
        JNIEnv* env, jobject /* this */, jlong handle) {
    if (handle == 0) return 0;
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    return llama_get_kv_cache_used_cells(session->ctx);
}
