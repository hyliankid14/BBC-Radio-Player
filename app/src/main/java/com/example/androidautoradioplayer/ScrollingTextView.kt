package com.example.androidautoradioplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView

class ScrollingTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var animator: ValueAnimator? = null
    
    private val scrollRunnable = Runnable {
        if (text.isNullOrEmpty()) return@Runnable
        
        val textWidth = paint.measureText(text.toString()).toInt()
        val contentWidth = width - paddingLeft - paddingRight
        
        if (contentWidth > 0 && textWidth > contentWidth) {
            // Calculate start position based on gravity
            // If centered, the text is drawn starting at a negative coordinate relative to 0
            // We need to scroll starting from that negative coordinate to show the beginning
            val absoluteGravity = android.view.Gravity.getAbsoluteGravity(gravity, layoutDirection)
            val horizontalGravity = absoluteGravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK
            
            val startX = if (horizontalGravity == android.view.Gravity.CENTER_HORIZONTAL) {
                (contentWidth - textWidth) / 2
            } else {
                0
            }
            
            val scrollDistance = textWidth - contentWidth + 100 // Scroll a bit past the end
            val endX = startX + scrollDistance
            
            // Calculate duration based on distance to ensure consistent speed (approx 50px/sec)
            val scrollDuration = (scrollDistance * 20).toLong()
            
            animator = ValueAnimator.ofInt(startX, endX).apply {
                duration = scrollDuration
                interpolator = LinearInterpolator()
                startDelay = 2000 // 2 seconds pause at start
                
                addUpdateListener { animation ->
                    scrollTo(animation.animatedValue as Int, 0)
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    var cancelled = false
                    
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        if (cancelled) return
                        
                        // Reset to start
                        scrollTo(startX, 0)
                        // Restart (will wait startDelay again)
                        if (isAttachedToWindow) {
                            start()
                        }
                    }
                })
                start()
            }
        } else {
            // Ensure we are reset if text fits
            scrollTo(0, 0)
        }
    }

    init {
        setSingleLine()
        ellipsize = null // Disable native ellipsize
        setHorizontallyScrolling(true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        startScrolling()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        startScrolling()
    }

    fun startScrolling() {
        removeCallbacks(scrollRunnable)
        animator?.removeAllListeners()
        animator?.cancel()
        scrollTo(0, 0)

        // Use post to ensure we have correct measurements and layout is complete
        post(scrollRunnable)
    }
    
    override fun onDetachedFromWindow() {
        removeCallbacks(scrollRunnable)
        animator?.removeAllListeners()
        animator?.cancel()
        super.onDetachedFromWindow()
    }
    
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            startScrolling()
        } else {
            removeCallbacks(scrollRunnable)
            animator?.removeAllListeners()
            animator?.cancel()
            scrollTo(0, 0)
        }
    }
}
