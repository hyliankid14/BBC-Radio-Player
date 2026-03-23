package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkQualityDetector {
    fun getRecommendedAudioQuality(context: Context): ThemePreference.AudioQuality {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return ThemePreference.AudioQuality.DATA_SAVER_48
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return ThemePreference.AudioQuality.DATA_SAVER_48

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return ThemePreference.AudioQuality.HIGH_320
            }

            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return ThemePreference.AudioQuality.DATA_SAVER_48
            }

            if (connectivityManager.isActiveNetworkMetered) {
                val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
                return when {
                    downstreamKbps >= 12_000 -> ThemePreference.AudioQuality.STANDARD_128
                    downstreamKbps >= 2_500 -> ThemePreference.AudioQuality.DATA_SAVER_96
                    else -> ThemePreference.AudioQuality.DATA_SAVER_48
                }
            }

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
                return when {
                    downstreamKbps >= 30_000 -> ThemePreference.AudioQuality.HIGH_320
                    downstreamKbps >= 10_000 -> ThemePreference.AudioQuality.STANDARD_128
                    downstreamKbps >= 2_500 -> ThemePreference.AudioQuality.DATA_SAVER_96
                    else -> ThemePreference.AudioQuality.DATA_SAVER_48
                }
            }

            ThemePreference.AudioQuality.STANDARD_128
        } else {
            @Suppress("DEPRECATION")
            when (connectivityManager.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> ThemePreference.AudioQuality.HIGH_320
                ConnectivityManager.TYPE_MOBILE -> ThemePreference.AudioQuality.DATA_SAVER_96
                else -> ThemePreference.AudioQuality.DATA_SAVER_48
            }
        }
    }
}
