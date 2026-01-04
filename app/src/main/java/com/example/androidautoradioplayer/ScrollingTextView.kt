package com.example.androidautoradioplayer

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView

class ScrollingTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var animator: ValueAnimator? = null
    private var textWidth = 0
    private var viewWidth = 0

    init {
        setSingleLine()
        ellipsize = null // Disable native ellipsize
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewWidth = width
        measureTextWidth()
        startScrolling()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        measureTextWidth()
        startScrolling()
    }

    private fun measureTextWidth() {
        textWidth = paint.measureText(text.toString()).toInt()
    }

    private fun startScrolling() {
        animator?.cancel()
        scrollTo(0, 0)

        val contentWidth = width - paddingLeft - paddingRight
        if (textWidth > contentWidth) {
            val scrollDistance = textWidth - contentWidth + 100 // Scroll a bit past the end
            val scrollDuration = (textWidth.toFloat() / 100f * 2000).toLong() // Slower speed
            
            animator = ValueAnimator.ofInt(0, scrollDistance).apply {
                duration = scrollDuration
                interpolator = LinearInterpolator()
                startDelay = 2000 // 2 seconds pause at start
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                
                addUpdateListener { animation ->
                    scrollTo(animation.animatedValue as Int, 0)
                    invalidate()
                }
                start()
            }
        }
    }
    
    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
