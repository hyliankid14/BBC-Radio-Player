package com.hyliankid14.bbcradioplayer

import android.app.AlertDialog
import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.CheckBox
import android.widget.TextView
import android.view.LayoutInflater

object AnalyticsOptInDialog {
    
    fun show(context: Context, analytics: PrivacyAnalytics) {
        try {
            val dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_analytics_opt_in, null)
            
            val messageText = dialogView.findViewById<TextView>(R.id.analytics_message)
            val checkbox = dialogView.findViewById<CheckBox>(R.id.analytics_checkbox)
            
            // Make links clickable
            messageText.movementMethod = LinkMovementMethod.getInstance()
            
            AlertDialog.Builder(context)
                .setTitle("Help Improve BBC Radio Player")
                .setView(dialogView)
                .setPositiveButton("Continue") { _, _ ->
                    analytics.setEnabled(checkbox.isChecked)
                    analytics.markOptInDialogShown()
                }
                .setNegativeButton("Maybe Later") { _, _ ->
                    analytics.setEnabled(false)
                    analytics.markOptInDialogShown()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsOptInDialog", "Error showing dialog", e)
        }
    }
}
