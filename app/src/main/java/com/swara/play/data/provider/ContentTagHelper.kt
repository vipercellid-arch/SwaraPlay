package com.swara.play.data.provider

object ContentTagHelper {
    private val adultKeywords = listOf(
        "erotic", "ecchi", "hentai", "adult", "18+", "nc-17", "sex", "sensual",
        "forbidden love", "affair", "seduction", "explicit", "nude", "smut"
    )
    private val lgbtKeywords = listOf(
        "lgbt", "lgbtq", "boys love", "girls love", "yaoi", "yuri", "queer",
        "gay", "lesbian", "transgender", "same-sex", "bl drama", "gl drama"
    )

    fun detectAdult18(title: String, overview: String, genres: List<String>, isAdultFlag: Boolean = false): Boolean {
        if (isAdultFlag) return true
        val combined = (title + " " + overview + " " + genres.joinToString(" ")).lowercase()
        return adultKeywords.any { combined.contains(it) }
    }

    fun detectLgbt(title: String, overview: String, genres: List<String>): Boolean {
        val combined = (title + " " + overview + " " + genres.joinToString(" ")).lowercase()
        return lgbtKeywords.any { combined.contains(it) }
    }
}
