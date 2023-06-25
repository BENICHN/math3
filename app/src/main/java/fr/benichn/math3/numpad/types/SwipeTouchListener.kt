package fr.benichn.math3.numpad.types

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

open class SwipeTouchListener : View.OnTouchListener {
    private var x0: Float = -1f
    private var y0: Float = -1f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (x0 == -1f) {
                    x0 = e.x
                    y0 = e.y
                }
            }
            MotionEvent.ACTION_UP -> {
                 x0 = -1f
                 y0 = -1f
            }
            MotionEvent.ACTION_MOVE -> {
                if (x0 != -1f) {
                    val dx = e.x - x0
                    val dy = e.y - y0
                    if (abs(dx) > abs(dy)) {
                        if (dx > SWIPE_DISTANCE) {
                            onSwipeRight()
                            x0 = -1f
                            y0 = -1f
                        }
                        if (dx < -SWIPE_DISTANCE) {
                            onSwipeLeft()
                            x0 = -1f
                            y0 = -1f
                        }
                    } else {
                        if (dy > SWIPE_DISTANCE) {
                            onSwipeBottom()
                            x0 = -1f
                            y0 = -1f
                        }
                        if (dy < -SWIPE_DISTANCE) {
                            onSwipeTop()
                            x0 = -1f
                            y0 = -1f
                        }
                    }
                }
            }
            else -> {
            }
        }
        return false
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeTop() {}
    open fun onSwipeBottom() {}

    companion object {
        const val SWIPE_DISTANCE = 100f
    }
}