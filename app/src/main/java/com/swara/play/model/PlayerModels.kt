package com.swara.play.model

enum class StreamKind {
    DIRECT_EXO,
    EMBED_WEBVIEW
}

data class StreamServer(
    val id: String,
    val name: String,
    val providerTag: String,
    val url: String,
    val kind: StreamKind,
    val quality: String = "HD",
    val referer: String = ""
)

data class SubtitleTrack(
    val id: String,
    val languageCode: String,
    val languageLabel: String,
    val url: String,
    val format: String = "srt",
    val source: String = "Wyzie"
)
