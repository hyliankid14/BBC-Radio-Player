package com.hyliankid14.bbcradioplayer

import java.text.SimpleDateFormat
import java.util.Locale

object EpisodeDateParser {
    fun parsePubDateToEpoch(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss",
            "EEE, dd MMM yyyy HH",
            "dd MMM yyyy HH",
            "EEE, dd MMM yyyy",
            "dd MMM yyyy"
        )
        for (pattern in patterns) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next pattern.
            }
        }
        return 0L
    }
}
