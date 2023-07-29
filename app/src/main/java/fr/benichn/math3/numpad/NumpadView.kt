package fr.benichn.math3.numpad

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.numpad.types.Direction
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
    private val pageBox
        get() = box.currentPageBox

    val notifyButtonClicked = Callback<NumpadView, String>(this)
    val onButtonClicked = notifyButtonClicked.Listener()

    var touchAction: TouchAction? by ObservableProperty<NumpadView, TouchAction?>(this, null) { _, e ->
        e.new?.apply {
            onFinished += { _, _ -> touchAction = null }
            onReplaced += { _, ev -> touchAction = ev.new }
        }
    }

    private inner class NumpadTouchAction : TouchAction(longPressTimeout = 200L) {
        private lateinit var downButton: NumpadButtonInfo
        override fun onDown() {
            downButton = pageBox.findButton(prim.lastPosition)
            pageBox.buttonPressed = downButton.pt
        }

        override fun onLongDown() {
            if (downButton.hasAux) {
                pageBox.buttonExpanded = downButton.pt
            }
        }

        override fun onMove() {
            if (!isLongPressed || !downButton.hasAux) {
                val d = prim.totalDiff
                when {
                    d.x > SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        box.swipe(Direction.Right)
                        finish()
                    }
                    d.y > SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        box.swipe(Direction.Down)
                        finish()
                    }
                    d.x < -SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        box.swipe(Direction.Left)
                        finish()
                    }
                    d.y < -SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        box.swipe(Direction.Up)
                        finish()
                    }
                }
            } else {
                pageBox.buttonPressed = pageBox.findCoords(prim.lastPosition)
            }
        }

        override fun onUp() {
            if (pageBox.buttonExpanded == null) {
                notifyButtonClicked(downButton.id)
            } else {
                val btn = pageBox.findAuxButton(prim.lastPosition)
                btn?.let { notifyButtonClicked(it.id) }
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            pageBox.buttonExpanded = null
            pageBox.buttonPressed = null
        }

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        box.size = SizeF(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        box.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        Log.d("nump", e.toString())
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