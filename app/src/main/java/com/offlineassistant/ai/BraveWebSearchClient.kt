package com.offlineassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Клиент Brave Search API (https://brave.com/search/api/).
 * Бесплатный тир на момент написания даёт ограниченное число запросов/месяц —
 * этого достаточно для личного голосового ассистента, не для продакшн-нагрузки.
 *
 * Ключ НЕ хардкодится в код — берётся из BuildConfig.BRAVE_SEARCH_API_KEY,
 * который генерируется из local.properties (файл не в git, см. .gitignore и
 * README, раздел "Настройка поиска с синтезом ответа").
 */
class BraveWebSearchClient(private val apiKey: String) : WebSearchClient {

    override suspend fun search(query: String, maxResults: Int): List<SearchSnippet> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext emptyList()

            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=$maxResults")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Subscription-Token", apiKey)
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) return@runCatching emptyList()

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val results = json.optJSONObject("web")?.optJSONArray("results") ?: return@runCatching emptyList()

                (0 until minOf(results.length(), maxResults)).map { i ->
                    val item = results.getJSONObject(i)
                    SearchSnippet(
                        title = item.optString("title"),
                        snippet = item.optString("description"),
                        url = item.optString("url")
                    )
                }
            }.getOrElse {
                android.util.Log.e("BraveWebSearchClient", "Ошибка поиска: ${it.message}")
                emptyList()
            }
        }
}
