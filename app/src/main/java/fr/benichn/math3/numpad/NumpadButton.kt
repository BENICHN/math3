package fr.benichn.math3.numpad

import android.annotation.SuppressLint
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import fr.benichn.math3.R
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.numpad.types.SwipeTouchListener
import fr.benichn.math3.types.callback.*

@SuppressLint("ClickableViewAccessibility")
class NumpadButton(context: Context, val id: String) : androidx.appcompat.widget.AppCompatImageButton(
    ContextThemeWrapper(
        context,
        R.style.numpad_btn
    ), null, R.style.numpad_btn
) {
    val onSwipe = Callback<NumpadButton, Direction>(this)
    init {
        setOnTouchListener(object : SwipeTouchListener() {
            override fun onSwipeBottom() {
                onSwipe(Direction.Down)
            }
            override fun onSwipeLeft() {
                onSwipe(Direction.Left)
            }
            override fun onSwipeRight() {
                onSwipe(Direction.Right)
            }
            override fun onSwipeTop() {
                onSwipe(Direction.Up)
            }
        })
        stateListAnimator = null
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> isPressed = true
        }
        return super.onTouchEvent(event)
    }
}