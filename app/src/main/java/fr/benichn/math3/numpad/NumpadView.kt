package fr.benichn.math3.numpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.NumpadFormulaBox
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.numpad.types.Pt
import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.ObservableProperty
import org.json.JSONObject

class NumpadView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = NumpadFormulaBox(
        JSONObject(context.assets.open("numpad_pages.json").reader().use { it.readText() }),
        SizeF(width.toFloat(), height.toFloat())
    ).apply {
        onPictureChanged += { _, _ ->
            invalidate()
        }
    }

    val notifyButtonClicked = Callback<NumpadView, String>(this)
    val onButtonClicked = notifyButtonClicked.Listener()

    var touchAction: TouchAction? by ObservableProperty<NumpadView, TouchAction?>(this, null) { _, e ->
        e.new?.apply {
            onFinished += { _, _ -> touchAction = null }
            onReplaced += { _, ev -> touchAction = ev.new }
        }
    }

    private inner class NumpadTouchAction : TouchAction() {
        private lateinit var downButton: FormulaBox
        override fun onDown() {
            downButton = box.findBox(prim.lastPosition)
            box.currentPageBox.let { it.buttonPressed = it.coordsOf(downButton) }
        }

        override fun onLongDown() {
        }

        override fun onMove() {
            val d = prim.totalDiff
            when {
                d.x > SWIPE_DISTANCE -> {
                    box.currentPageBox.buttonPressed = null
                    box.swipe(Direction.Right)
                    finish()
                }
                d.y > SWIPE_DISTANCE -> {
                    box.currentPageBox.buttonPressed = null
                    box.swipe(Direction.Down)
                    finish()
                }
                d.x < -SWIPE_DISTANCE -> {
                    box.currentPageBox.buttonPressed = null
                    box.swipe(Direction.Left)
                    finish()
                }
                d.y < -SWIPE_DISTANCE -> {
                    box.currentPageBox.buttonPressed = null
                    box.swipe(Direction.Up)
                    finish()
                }
            }
        }

        override fun onUp() {
            val id = box.currentPageBox.getButtonId(downButton)
            notifyButtonClicked(id)
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            box.currentPageBox.buttonPressed = null
        }

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        box.size = SizeF(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        box.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchAction = NumpadTouchAction()
            }
        }
        runTouchAction(e)
        return true
    }

    private fun runTouchAction(e: MotionEvent) {
        touchAction?.also {
            it.onTouchEvent(e)
            if (touchAction != it) { // en cas de remplacement
                runTouchAction(e)
            }
        }
    }

    companion object {
        const val SWIPE_DISTANCE = 100f
    }
}