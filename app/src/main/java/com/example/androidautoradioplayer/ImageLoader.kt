package com.example.androidautoradioplayer

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

object ImageLoader {
    private const val USER_AGENT = "AndroidAutoRadioPlayer/1.0 (https://github.com/example/repo)"

    fun getGlideUrl(url: String?): Any? {
        if (url.isNullOrEmpty()) return url
        
        // Add User-Agent for Wikimedia and BBC
        if (url.contains("wikimedia.org") || url.contains("bbci.co.uk")) {
            return GlideUrl(
                url, LazyHeaders.Builder()
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
            )
        }
        return url
    }
}
