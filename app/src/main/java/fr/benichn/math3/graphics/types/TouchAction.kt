package fr.benichn.math3.graphics.types

import android.graphics.PointF
import android.os.CountDownTimer
import android.util.Log
import android.view.MotionEvent
import androidx.core.graphics.minus
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.VCC
import fr.benichn.math3.types.callback.invoke

abstract class TouchAction(val downPosition: PointF, val downIndex: Int, val getPos: (MotionEvent) -> PointF = { e -> PointF(e.x, e.y) }) {
    constructor(downEvent: MotionEvent, getPos: (MotionEvent) -> PointF = { e -> PointF(e.x, e.y) }) : this(
        getPos(downEvent),
        downEvent.actionIndex,
        getPos
    ) {
        assert(downEvent.actionMasked == MotionEvent.ACTION_DOWN)
    }

    var lastPos = downPosition
        private set
    var isLongPressed = false
        private set
    var isFinished = false
        private set
    var hasMoved = false
        private set

    private val notifyFinished = Callback<TouchAction, Unit>(this)
    val onFinished = notifyFinished.Listener()

    private val notifyReplaced = VCC<TouchAction, TouchAction>(this)
    val onReplaced = notifyReplaced.Listener()

    private val downTimer = object : CountDownTimer(LONG_PRESS_TIMEOUT, LONG_PRESS_TIMEOUT) {
        override fun onTick(p0: Long) {}
        override fun onFinish() {
            isLongPressed = true
            onLongDown()
        }
    }.start()

    protected abstract fun onLongDown()
    protected abstract fun onMove()
    protected abstract fun onUp()
    protected abstract fun beforeFinish(replacement: TouchAction?)

    fun onTouchEvent(e: MotionEvent) {
        if (!isFinished && e.actionIndex == downIndex) {
            val pos = getPos(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    lastPos = pos
                    if (!hasMoved && Utils.l2(downPosition - pos) > MINIMAL_MOVE_DISTANCE_SQ) {
                        hasMoved = true
                        downTimer.cancel()
                    }
                    if (hasMoved) {
                        onMove()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    lastPos = pos
                    downTimer.cancel()
                    onUp()
                    beforeFinish(null)
                    isFinished = true
                    notifyFinished()
                }
            }
        }
    }

    fun forceLongDown() {
        if (!isLongPressed) {
            downTimer.cancel()
            downTimer.onFinish()
        }
    }

    fun replace(a: TouchAction) {
        downTimer.cancel()
        beforeFinish(a)
        isFinished = true
        notifyReplaced(this, a)
    }

    fun finish() {
        downTimer.cancel()
        beforeFinish(null)
        isFinished = true
        notifyFinished()
    }

    companion object {
        const val LONG_PRESS_TIMEOUT = 500L
        const val MINIMAL_MOVE_DISTANCE_SQ = 100
    }
}