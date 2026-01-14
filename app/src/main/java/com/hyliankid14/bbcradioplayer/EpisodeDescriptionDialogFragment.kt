package com.hyliankid14.bbcradioplayer

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide

import android.view.ViewGroup
import android.view.WindowManager

class EpisodeDescriptionDialogFragment : DialogFragment() {
    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_TITLE = "title"
        private const val ARG_IMAGE_URL = "image_url"
        
        fun newInstance(description: String, title: String = "Episode Description", imageUrl: String? = null): EpisodeDescriptionDialogFragment {
            return EpisodeDescriptionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_TITLE, title)
                    putString(ARG_IMAGE_URL, imageUrl)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val description = arguments?.getString(ARG_DESCRIPTION) ?: ""
        val title = arguments?.getString(ARG_TITLE) ?: "Episode Description"
        val imageUrl = arguments?.getString(ARG_IMAGE_URL)
        
        val textView = TextView(requireContext()).apply {
            // Render HTML in the dialog so full formatting and whitespace is preserved
            text = androidx.core.text.HtmlCompat.fromHtml(description, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            textSize = 16f
            // Use themed on-surface color so text is legible in light and dark modes
            setTextColor(resources.getColor(R.color.md_theme_onSurface, requireContext().theme))
            setLineSpacing(1.4f, 1.1f)
            // Allow links to be clickable and text to be selectable
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setTextIsSelectable(true)
            maxLines = Int.MAX_VALUE
        }

        val pad = (16 * resources.displayMetrics.density + 0.5f).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        if (!imageUrl.isNullOrEmpty()) {
            val imageView = ImageView(requireContext()).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
                contentDescription = title
            }
            val imageHeight = (200 * resources.displayMetrics.density + 0.5f).toInt()
            container.addView(imageView, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                imageHeight
            ))
            Glide.with(requireContext()).load(imageUrl).into(imageView)
        }

        container.addView(textView, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            addView(container, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Close") { _, _ -> dismiss() }
            .create()
    }

    override fun onStart() {
        super.onStart()
        // Expand dialog to full screen so long descriptions are fully scrollable and not visually truncated
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Ensure soft input mode doesn't resize the dialog unexpectedly
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
} 
