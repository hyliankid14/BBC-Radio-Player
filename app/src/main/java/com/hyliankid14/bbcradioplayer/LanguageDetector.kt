package com.hyliankid14.bbcradioplayer

object LanguageDetector {
    // A small set of common English stopwords used to help detect English language text.
    private val englishStopwords = setOf(
        "the", "and", "to", "of", "a", "in", "is", "for", "on", "with", "that", "this", "it", "as", "are", "was", "be", "by", "or"
    )

    fun isLikelyEnglish(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        // Normalize and tokenise
        val norm = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^\\p{L}0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        if (norm.length < 20) {
            // For very short strings fall back to measuring Latin character ratio
            val letters = norm.replace(Regex("[^\\p{L}]+"), "")
            if (letters.isEmpty()) return false
            val latinCount = letters.count { it in 'a'..'z' || it in 'A'..'Z' }
            return latinCount.toDouble() / letters.length.toDouble() >= 0.8
        }

        val tokens = norm.split(' ').filter { it.isNotBlank() }
        val tokenCount = tokens.size
        if (tokenCount == 0) return false

        val stopwordMatches = tokens.count { englishStopwords.contains(it) }
        val stopwordRatio = stopwordMatches.toDouble() / tokenCount.toDouble()

        // Latin script ratio (counts letters in Latin Unicode block)
        val lettersOnly = norm.replace(Regex("[^\\p{L}]+"), "")
        val latinLetters = lettersOnly.count { it.toInt() in 0x0041..0x007A } // rough Latin range
        val latinRatio = if (lettersOnly.isEmpty()) 0.0 else latinLetters.toDouble() / lettersOnly.length.toDouble()

        // Heuristic: require a reasonable latin script presence and some English stopwords
        return (latinRatio >= 0.7 && stopwordRatio >= 0.06) || latinRatio >= 0.9 || stopwordRatio >= 0.12
    }

    fun isPodcastEnglish(podcast: Podcast): Boolean {
        // Use title + description as the signal (RSS may not carry explicit language tag reliably)
        val combined = listOfNotNull(podcast.title, podcast.description).joinToString(" ")
        return isLikelyEnglish(combined)
    }
}
