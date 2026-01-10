package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkQualityDetector {
    /**
     * Determines if high quality audio should be used based on current network conditions.
     * Returns true for high quality if on WiFi or strong cellular connection (4G/5G).
     * Returns false for low quality if on weak cellular (2G/3G) or metered connection.
     */
    fun shouldUseHighQuality(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            // Check if on WiFi - always use high quality
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true
            }
            
            // Check for metered connection (limited data plan)
            if (connectivityManager.isActiveNetworkMetered) {
                return false
            }
            
            // Check for strong cellular connections (4G/5G)
            val hasLte = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            if (hasLte) {
                // Check for 5G
                val has5G = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    capabilities.linkDownstreamBandwidthKbps >= 100000 // Estimate for 5G
                } else {
                    false
                }
                return has5G || capabilities.linkDownstreamBandwidthKbps >= 10000 // 4G threshold
            }
            
            false
        } else {
            @Suppress("DEPRECATION")
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.type == ConnectivityManager.TYPE_WIFI
        }
    }
}
