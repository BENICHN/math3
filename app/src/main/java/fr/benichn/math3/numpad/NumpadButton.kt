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
    private val notifySwipe = Callback<NumpadButton, Direction>(this)
    val onSwipe = notifySwipe.Listener()
    init {
        setOnTouchListener(object : SwipeTouchListener() {
            override fun onSwipeBottom() {
                notifySwipe(Direction.Down)
            }
            override fun onSwipeLeft() {
                notifySwipe(Direction.Left)
            }
            override fun onSwipeRight() {
                notifySwipe(Direction.Right)
            }
            override fun onSwipeTop() {
                notifySwipe(Direction.Up)
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