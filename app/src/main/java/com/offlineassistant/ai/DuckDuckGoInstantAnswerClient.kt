package com.offlineassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fallback без API-ключа — DuckDuckGo Instant Answer API.
 *
 * ЧЕСТНОЕ ОГРАНИЧЕНИЕ: это НЕ полноценный поиск по вебу. Он отвечает только
 * на "справочные" запросы, покрытые их внутренними источниками (в основном
 * Википедия и родственные базы) — определения, факты, известные сущности.
 * На "что было в новостях вчера", "результат вчерашнего матча" и подобные
 * актуальные вопросы почти всегда вернёт пустой AbstractText — тогда
 * WebAnswerHandler должен честно сказать пользователю, что не нашёл
 * информацию, а не выдумывать ответ.
 *
 * Используется автоматически, если BuildConfig.BRAVE_SEARCH_API_KEY пуст
 * (см. LlamaEngine/WebAnswerHandler — фабрика клиента).
 */
class DuckDuckGoInstantAnswerClient : WebSearchClient {

    override suspend fun search(query: String, maxResults: Int): List<SearchSnippet> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                if (connection.responseCode != 200) return@runCatching emptyList()

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText")
                val abstractUrl = json.optString("AbstractURL")
                val heading = json.optString("Heading")

                if (abstractText.isNullOrBlank()) emptyList()
                else listOf(SearchSnippet(title = heading, snippet = abstractText, url = abstractUrl))
            }.getOrElse {
                android.util.Log.e("DuckDuckGoClient", "Ошибка поиска: ${it.message}")
                emptyList()
            }
        }
}
