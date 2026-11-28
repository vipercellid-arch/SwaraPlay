package com.swara.play.model

data class StreamSource(
    val id: String,
    val url: String,
    val referer: String = "",
    val origin: String = "",
    val label: String = "",
    val subtitleUrl: String = ""
)

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val isPrerelease: Boolean
)
