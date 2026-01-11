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
            textSize = 14f
            setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    android.R.color.black
                )
            )
            setPadding(24, 24, 24, 24)
            setLineSpacing(1.5f, 1.5f)
        }

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            addView(textView)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Episode Description")
            .setView(scrollView)
            .setPositiveButton("Close") { _, _ -> dismiss() }
            .create()
    }
}
