package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object SynopsisTranslator {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun translateToLanguage(text: String, targetLangIso: String): String = withContext(Dispatchers.IO) {
        val clean = text.trim()
        val lang = targetLangIso.lowercase().ifBlank { "id" }
        if (clean.isBlank() || lang == "en") return@withContext clean
        val cacheKey = "$lang:${clean.hashCode()}"
        cache[cacheKey]?.let { return@withContext it }

        try {
            val encoded = URLEncoder.encode(clean.take(1200), "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t&q=$encoded"
            val req = Request.Builder().url(url).header("User-Agent", HttpClientProvider.USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext clean
                val raw = resp.body?.string() ?: return@withContext clean
                val root = JSONArray(raw)
                val sentences = root.optJSONArray(0) ?: return@withContext clean
                val out = buildString {
                    for (i in 0 until sentences.length()) {
                        val part = sentences.optJSONArray(i)?.optString(0, "").orEmpty()
                        append(part)
                    }
                }.trim()
                val result = out.ifBlank { clean }
                cache[cacheKey] = result
                result
            }
        } catch (_: Exception) {
            clean
        }
    }
}
