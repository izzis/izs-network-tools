package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP response headers viewer. Accepts a full URL or bare host
 * (https is assumed). Follows redirects and prints the chain,
 * status line, timing and all response headers.
 */
object HttpHeadersFetcher {

    fun fetch(rawTarget: String, timeoutMs: Int): Flow<String> = flow {
        var url = rawTarget.trim()
        if (url.isEmpty()) {
            emit("ERROR: enter a URL or host first")
            return@flow
        }
        if (!url.contains("://")) url = "https://$url"
        emit(";; HEADERS $url\n")
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "IZS-Network-Tools/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val ms = System.currentTimeMillis() - start
                var prior = resp.priorResponse
                val chain = mutableListOf<String>()
                while (prior != null) {
                    chain.add(0, "${prior.code} ${prior.message} <- ${prior.request.url}")
                    prior = prior.priorResponse
                }
                chain.forEach { emit(it) }
                emit("${resp.code} ${resp.message} (${ms} ms)")
                emit("Final URL: ${resp.request.url}\n")
                emit("== Response headers ==")
                for (i in 0 until resp.headers.size) {
                    emit("${resp.headers.name(i)}: ${resp.headers.value(i)}")
                }
            }
        } catch (e: Exception) {
            emit("ERROR: request failed: ${e.message}")
            emit("Hint: check the scheme (http/https), port and path.")
        }
    }.flowOn(Dispatchers.IO)
}
