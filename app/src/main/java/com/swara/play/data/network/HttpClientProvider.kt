package com.swara.play.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    // Desktop Chrome 137 UA matching EncDecEndpoints (Hexa, Nexa, MeowTV) so CDNs never reject APK playback
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    @Volatile
    var dohEnabled: Boolean = true

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    private val cloudflareDoh: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("8.8.8.8")
            )
            .build()
    }

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(cloudflareDoh)
            .connectTimeout(18, TimeUnit.SECONDS)
            .readTimeout(28, TimeUnit.SECONDS)
            .writeTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(18, TimeUnit.SECONDS)
            .readTimeout(28, TimeUnit.SECONDS)
            .writeTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val client: OkHttpClient
        get() = if (dohEnabled) dohClient else standardClient
}
