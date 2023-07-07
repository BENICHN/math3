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

abstract class TouchAction(val getPos: (PointF) -> PointF = { it }) {
    var downAbsPosition: PointF = PointF(Float.NaN, Float.NaN)
        private set
    var downPosition: PointF = PointF(Float.NaN, Float.NaN)
        private set
    var downIndex: Int = -1
        private set
    var isLaunched = false
        private set
    var lastAbsPos = downAbsPosition
        private set
    var lastPos = downPosition
        private set
    var lastAbsDiff = PointF()
        private set
    var lastDiff = PointF()
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
    }

    protected abstract fun onDown()
    protected abstract fun onLongDown()
    protected abstract fun onMove()
    protected abstract fun onUp()
    protected abstract fun beforeFinish(replacement: TouchAction?)

    fun onTouchEvent(e: MotionEvent) {
        if (!isFinished && (downIndex == -1 || e.actionIndex == downIndex)) {
            val absPos = PointF(e.x, e.y)
            val pos = getPos(absPos)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isLaunched) {
                        launch(e)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLaunched) {
                        lastAbsDiff = absPos - lastAbsPos
                        lastDiff = pos - lastPos
                        lastAbsPos = absPos
                        lastPos = pos
                        if (!hasMoved && Utils.l2(downAbsPosition - absPos) > MINIMAL_MOVE_DISTANCE_SQ) {
                            hasMoved = true
                            downTimer.cancel()
                        }
                        if (hasMoved) {
                            onMove()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isLaunched) {
                        lastAbsDiff = absPos - lastAbsPos
                        lastDiff = pos - lastPos
                        lastAbsPos = absPos
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
    }

    fun launch(downAbsPosition: PointF, downIndex: Int, longPress: Boolean = false) {
        assert(!isLaunched)
        this.downIndex = downIndex
        this.downAbsPosition = downAbsPosition
        downPosition = getPos(downAbsPosition)
        lastAbsPos = downAbsPosition
        lastPos = downPosition
        isLaunched = true
        if (longPress) {
            onDown()
            isLongPressed = true
            onLongDown()
        } else {
            downTimer.start()
            onDown()
        }
    }

    fun launch(downEvent: MotionEvent, longPress: Boolean = false) {
        assert(downEvent.actionMasked == MotionEvent.ACTION_DOWN)
        launch(PointF(downEvent.x, downEvent.y), downEvent.actionIndex, longPress)
    }

    fun forceLongDown() {
        if (!isLongPressed) {
            downTimer.cancel()
            downTimer.onFinish()
        }
    }

    protected fun replace(a: TouchAction) {
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
        const val LONG_PRESS_TIMEOUT = 300L
        const val MINIMAL_MOVE_DISTANCE_SQ = 100
    }
}