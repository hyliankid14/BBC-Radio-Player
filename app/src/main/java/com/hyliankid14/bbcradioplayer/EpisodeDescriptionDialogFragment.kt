package com.hyliankid14.bbcradioplayer

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class EpisodeDescriptionDialogFragment : DialogFragment() {
    companion object {
        private const val ARG_DESCRIPTION = "description"
        
        fun newInstance(description: String): EpisodeDescriptionDialogFragment {
            return EpisodeDescriptionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val description = arguments?.getString(ARG_DESCRIPTION) ?: ""
        
        val textView = TextView(requireContext()).apply {
            text = description
            textSize = 16f
            // Use themed on-surface color so text is legible in light and dark modes
            setTextColor(resources.getColor(R.color.md_theme_onSurface, requireContext().theme))
            // Convert 16dp padding to pixels
            val pad = (16 * resources.displayMetrics.density + 0.5f).toInt()
            setPadding(pad, pad, pad, pad)
            setLineSpacing(1.4f, 1.1f)
        }

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            addView(textView, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Episode Description")
            .setView(scrollView)
            .setPositiveButton("Close") { _, _ -> dismiss() }
            .create()
    }
}
