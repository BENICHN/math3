package fr.benichn.math3.graphics.types

import android.graphics.PointF
import android.os.CountDownTimer
import android.view.MotionEvent
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.Utils.Companion.div
import fr.benichn.math3.graphics.Utils.Companion.times
import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.VCC
import fr.benichn.math3.types.callback.invoke

data class TouchData(
    val id: Int,
    val downAbsPosition: PointF = PointF(Float.NaN, Float.NaN),
    val downPosition: PointF = PointF(Float.NaN, Float.NaN),
    val lastAbsPosition: PointF = downAbsPosition,
    val lastPosition: PointF = downPosition,
    val lastAbsDiff: PointF = PointF(),
    val lastDiff: PointF = PointF(),
    ) {
    val totalAbsDiff
        get() = lastAbsPosition - downAbsPosition
    val totalDiff
        get() = lastPosition - downPosition
    fun isTargeted(e: MotionEvent) = e.actionIndex == e.findPointerIndex(id)
    fun getAbsPos(e: MotionEvent): PointF {
        val i = e.findPointerIndex(id)
        return PointF(e.getX(i), e.getY(i))
    }
}

abstract class TouchAction(val getPos: (PointF) -> PointF = { it }) {
    val isLaunched
        get() = primaryData != null
    val isPinched
        get() = pinchData != null
    var isLongPressed = false
        private set
    var isFinished = false
        private set
    var hasMoved = false
        private set
    var primaryData: TouchData? = null
        private set
    var pinchData: TouchData? = null
        private set

    val prim
        get() = primaryData!!
    val pinch
        get() = pinchData!!

    data class Move(val diff: PointF, val millis: Long = System.currentTimeMillis())

    private val moves = mutableListOf<Move>()
    val velocity: PointF // en px/s
        get() {
            val t = System.currentTimeMillis()
            return if (moves.isEmpty()) PointF() else moves
                .filter { t - it.millis < MOVE_VELOCITY_DELAY }
                .drop(1)
                .fold(Move(PointF(), moves[0].millis)) { acc, m ->
                    Move(
                        acc.diff + m.diff / (m.millis - acc.millis).toFloat(),
                        m.millis
                    )
                }
                .diff * 1000f
        }

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
    protected abstract fun onPinchDown()
    protected abstract fun onPinchMove()
    protected abstract fun onPinchUp()
    protected abstract fun beforeFinish(replacement: TouchAction?)

    private fun createData(id: Int, downAbsPos: PointF) {
        val td = TouchData(
            id,
            downAbsPos,
            getPos(downAbsPos)
        )
        if (isLaunched) {
            pinchData = td
        } else {
            primaryData = td
        }
    }

    private fun updatePos(absPos: PointF, isPinch: Boolean) {
        val pos = getPos(absPos)
        val td = (if (isPinch) pinchData else primaryData)?.let { TouchData(
            it.id,
            it.downAbsPosition,
            it.downPosition,
            absPos,
            pos,
            absPos - it.lastAbsPosition,
            pos - it.lastPosition
        ) }
        if (isPinch) {
            pinchData = td
        } else {
            primaryData = td
        }
    }

    fun onTouchEvent(e: MotionEvent) {
        if (!isFinished) {
            if (isLaunched) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        val absPos = prim.getAbsPos(e)
                        updatePos(absPos, false)
                        if (!hasMoved && Utils.l2(prim.downAbsPosition - absPos) > MINIMAL_MOVE_DISTANCE_SQ) {
                            hasMoved = true
                            downTimer.cancel()
                        }
                        if (hasMoved) {
                            if (isPinched) {
                                val pinchAbsPos = pinch.getAbsPos(e)
                                updatePos(pinchAbsPos, true)
                                onPinchMove()
                            } else {
                                moves += Move(prim.lastAbsDiff)
                                onMove()
                            }
                        }
                    }
                }
                if (prim.isTargeted(e)) {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                            if (isPinched) {
                                Pair(prim, pinch).run {
                                    primaryData = second
                                    pinchData = first
                                }
                                onPinchUp()
                                pinchData = null
                            } else {
                                updatePos(prim.getAbsPos(e), false)
                                finish(true)
                            }
                        }
                    }
                } else if (isPinched) {
                    if (pinch.isTargeted(e)) {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_POINTER_UP -> {
                                updatePos(pinch.getAbsPos(e), true)
                                onPinchUp()
                                pinchData = null
                            }
                        }
                        return
                    }
                } else {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            createPinch(e)
                        }
                    }
                }
            } else {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        launch(e)
                    }
                }
            }
        }
    }

    private fun createPinch(e: MotionEvent) =
        createPinch(
            e.getPointerId(e.actionIndex),
            PointF(e.getX(e.actionIndex), e.getY(e.actionIndex))
        )

    private fun createPinch(id: Int, downAbsPos: PointF) {
        downTimer.cancel()
        hasMoved = true
        createData(
            id,
            downAbsPos
        )
        onPinchDown()
    }

    fun launchWithPinch(downAbsPosition: PointF, id: Int, pinchDownAbsPosition: PointF, pinchId: Int, longPress: Boolean = false) {
        launch(downAbsPosition, id, longPress)
        createPinch(pinchId, pinchDownAbsPosition)
    }

    fun launch(downAbsPosition: PointF, id: Int, longPress: Boolean = false) {
        assert(!isLaunched)
        createData(
            id,
            downAbsPosition
        )
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
        val id = downEvent.getPointerId(downEvent.actionIndex)
        launch(PointF(downEvent.getX(downEvent.actionIndex), downEvent.getY(downEvent.actionIndex)), id, longPress)
    }

    fun forceLongDown() {
        if (!isLongPressed) {
            downTimer.cancel()
            downTimer.onFinish()
        }
    }

    fun replace(a: TouchAction, longPress: Boolean = false) {
        downTimer.cancel()
        beforeFinish(a)
        isFinished = true
        if (isPinched) {
            a.launchWithPinch(prim.downAbsPosition, prim.id, pinch.downAbsPosition, pinch.id, longPress)
        } else {
            a.launch(prim.downAbsPosition, prim.id, longPress)
        }
        notifyReplaced(this, a)
    }

    fun finish() = finish(false)
    private fun finish(callOnUp: Boolean) {
        downTimer.cancel()
        if (callOnUp) onUp()
        beforeFinish(null)
        isFinished = true
        notifyFinished()
    }

    companion object {
        const val LONG_PRESS_TIMEOUT = 300L
        const val MINIMAL_MOVE_DISTANCE_SQ = 100
        const val MOVE_VELOCITY_DELAY = 100L
    }
}