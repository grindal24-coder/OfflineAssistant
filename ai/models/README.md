# Модели

Сюда на устройстве (не в git, см. .gitignore) кладутся GGUF-файлы:

- `llama-3.2-1b-instruct.Q4_K_M.gguf` — быстрая модель для простых команд
- `llama-3.2-3b-instruct.Q4_K_M.gguf` — модель для сложных запросов

На самом устройстве приложение ожидает их в:
`/sdcard/Android/data/com.offlineassistant/files/models/`
(т.е. `context.getExternalFilesDir(null)/models/`, см. `LlamaEngine.kt`).

## Где скачать (проверенные ссылки)

- **1B**: https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
  — файл `Llama-3.2-1B-Instruct-Q4_K_M.gguf`
- **3B**: https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF
  — файл `Llama-3.2-3B-Instruct-Q4_K_M.gguf` (~2.02 GB)

Бери файл с суффиксом `Q4_K_M` — баланс размера/качества, соответствует п.5 ТЗ.

Через huggingface-cli:
```bash
pip install huggingface_hub
huggingface-cli download bartowski/Llama-3.2-1B-Instruct-GGUF --include "Llama-3.2-1B-Instruct-Q4_K_M.gguf" --local-dir ./
huggingface-cli download bartowski/Llama-3.2-3B-Instruct-GGUF --include "Llama-3.2-3B-Instruct-Q4_K_M.gguf" --local-dir ./
```

## Дообучение не обязательно

Модели используются "как есть" (без fine-tuning), точность распознавания команд
регулируется few-shot примерами прямо в системном промпте — см.
`few_shot_prompt_example.txt` в этой папке (уже встроен в
`LlamaEngine.SYSTEM_PROMPT_INTENT`). Если после тестов точности не хватит —
следующий шаг не fine-tuning, а GBNF grammar-constrained decoding в llama.cpp
(гарантирует валидный JSON), и только потом, если совсем не хватает, LoRA
fine-tuning на своём датасете команд (отдельный процесс на ПК с GPU,
не на телефоне).

## Как закинуть на устройство

```bash
adb push llama-3.2-1b-instruct.Q4_K_M.gguf /sdcard/Android/data/com.offlineassistant/files/models/
adb push llama-3.2-3b-instruct.Q4_K_M.gguf /sdcard/Android/data/com.offlineassistant/files/models/
```

Или через файловый менеджер на телефоне — путь идентичный.
