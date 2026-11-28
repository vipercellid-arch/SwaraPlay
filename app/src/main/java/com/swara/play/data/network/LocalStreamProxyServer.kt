package com.swara.play.data.network

import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * SwaraPlay v11.8.0 — Embedded Localhost OkHttp + Cloudflare DoH HLS/MP4 Stream Proxy.
 * Routes all ExoPlayer manifest & segment requests through HttpClientProvider.client (OkHttp + DoH),
 * injects Referer/Origin/Desktop UA, auto-sniffs disguised #EXTM3U playlists, and rewrites all segment
 * & AES key URIs back through localhost so Hexa, Nexa, and MeowTV play 100% identically to Web!
 */
object LocalStreamProxyServer {
    @Volatile private var port: Int = 0
    @Volatile private var started: Boolean = false
    private val pool = Executors.newCachedThreadPool()

    @Synchronized
    fun ensureStarted(): Int {
        if (started && port > 0) return port
        return try {
            val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            port = serverSocket.localPort
            started = true
            pool.execute {
                while (!serverSocket.isClosed) {
                    try {
                        val client = serverSocket.accept()
                        pool.execute { handleClient(client) }
                    } catch (_: Exception) {
                        if (serverSocket.isClosed) break
                    }
                }
            }
            port
        } catch (_: Exception) {
            0
        }
    }

    fun buildProxyUrl(targetUrl: String, referer: String = "", origin: String = ""): String {
        val p = ensureStarted()
        if (p <= 0 || !targetUrl.startsWith("http")) return targetUrl
        val u = URLEncoder.encode(targetUrl, "UTF-8")
        val r = URLEncoder.encode(referer, "UTF-8")
        val o = URLEncoder.encode(origin, "UTF-8")
        return "http://127.0.0.1:$p/proxy?url=$u&ref=$r&org=$o"
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            runCatching {
                s.soTimeout = 25_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val rawPath = parts[1]

                var rangeHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                }

                val out = s.getOutputStream()
                val qIdx = rawPath.indexOf('?')
                if (qIdx < 0) {
                    writeSimpleError(out, 400, "Bad Request")
                    return
                }
                val params = parseQuery(rawPath.substring(qIdx + 1))
                val target = params["url"].orEmpty()
                val referer = params["ref"].orEmpty()
                val origin = params["org"].orEmpty()

                if (!target.startsWith("http")) {
                    writeSimpleError(out, 400, "Invalid URL")
                    return
                }

                val reqBuilder = Request.Builder()
                    .url(target)
                    .header("User-Agent", HttpClientProvider.USER_AGENT)
                    .header("Accept", "*/*")
                if (referer.isNotBlank()) reqBuilder.header("Referer", referer)
                if (origin.isNotBlank()) reqBuilder.header("Origin", origin)
                if (!rangeHeader.isNullOrBlank()) reqBuilder.header("Range", rangeHeader)

                HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                    val code = resp.code
                    if (!resp.isSuccessful && code != 206) {
                        writeSimpleError(out, code, "Upstream Error")
                        return
                    }
                    val ct = (resp.header("Content-Type") ?: "application/octet-stream").lowercase()
                    val finalUrl = resp.request.url.toString()
                    val looksM3u8 = target.contains(".m3u8", true) ||
                        finalUrl.contains(".m3u8", true) ||
                        ct.contains("mpegurl") ||
                        ct.contains("x-mpegurl")

                    val cl = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val canSniff = looksM3u8 || (cl in 1..(512 * 1024) && !ct.startsWith("video/") && !ct.startsWith("audio/"))

                    if (canSniff) {
                        val bytes = resp.body?.bytes() ?: ByteArray(0)
                        val headStr = String(bytes, 0, minOf(bytes.size, 32), Charsets.UTF_8).trimStart()
                        if (looksM3u8 || headStr.startsWith("#EXTM3U")) {
                            val text = String(bytes, Charsets.UTF_8)
                            val rewritten = if (text.trimStart().startsWith("#EXTM3U")) {
                                rewriteM3u8(text, finalUrl, referer, origin)
                            } else {
                                text
                            }
                            val outBytes = rewritten.toByteArray(Charsets.UTF_8)
                            val hdr = buildString {
                                append("HTTP/1.1 200 OK\r\n")
                                append("Content-Type: application/vnd.apple.mpegurl; charset=utf-8\r\n")
                                append("Content-Length: ${outBytes.size}\r\n")
                                append("Access-Control-Allow-Origin: *\r\n")
                                append("Connection: close\r\n\r\n")
                            }
                            out.write(hdr.toByteArray(Charsets.US_ASCII))
                            out.write(outBytes)
                            out.flush()
                            return
                        } else {
                            val statusText = if (code == 206) "Partial Content" else "OK"
                            val hdr = buildString {
                                append("HTTP/1.1 $code $statusText\r\n")
                                append("Content-Type: $ct\r\n")
                                append("Content-Length: ${bytes.size}\r\n")
                                resp.header("Content-Range")?.let { append("Content-Range: $it\r\n") }
                                append("Accept-Ranges: bytes\r\n")
                                append("Connection: close\r\n\r\n")
                            }
                            out.write(hdr.toByteArray(Charsets.US_ASCII))
                            out.write(bytes)
                            out.flush()
                            return
                        }
                    }

                    // Direct streaming pipe for TS / M4S / MP4 segments
                    val statusText = if (code == 206) "Partial Content" else "OK"
                    val hdr = buildString {
                        append("HTTP/1.1 $code $statusText\r\n")
                        append("Content-Type: $ct\r\n")
                        if (cl >= 0) append("Content-Length: $cl\r\n")
                        resp.header("Content-Range")?.let { append("Content-Range: $it\r\n") }
                        append("Accept-Ranges: bytes\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(hdr.toByteArray(Charsets.US_ASCII))
                    resp.body?.byteStream()?.use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                        }
                    }
                    out.flush()
                }
            }
        }
    }

    private fun rewriteM3u8(body: String, baseUrl: String, referer: String, origin: String): String {
        val baseUri = runCatching { URI(baseUrl) }.getOrNull()
        fun resolveAbs(raw: String): String {
            return runCatching {
                if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw
                else baseUri?.resolve(raw)?.toString() ?: raw
            }.getOrDefault(raw)
        }
        val uriRegex = Regex("URI=\"([^\"]+)\"")
        return body.lines().joinToString("\n") { line ->
            val t = line.trim()
            when {
                t.isEmpty() -> line
                t.startsWith("#") -> uriRegex.replace(line) { m ->
                    val abs = resolveAbs(m.groupValues[1])
                    "URI=\"${buildProxyUrl(abs, referer, origin)}\""
                }
                else -> buildProxyUrl(resolveAbs(t), referer, origin)
            }
        }
    }

    private fun parseQuery(q: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in q.split("&")) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = runCatching { URLDecoder.decode(pair.substring(0, eq), "UTF-8") }.getOrDefault("")
                val v = runCatching { URLDecoder.decode(pair.substring(eq + 1), "UTF-8") }.getOrDefault("")
                if (k.isNotEmpty()) map[k] = v
            }
        }
        return map
    }

    private fun writeSimpleError(out: OutputStream, code: Int, msg: String) {
        val body = msg.toByteArray(Charsets.UTF_8)
        val hdr = "HTTP/1.1 $code $msg\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(hdr.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }
}
