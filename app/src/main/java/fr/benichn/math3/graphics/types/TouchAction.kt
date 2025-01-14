package fr.benichn.math3.graphics.types

import android.graphics.PointF
import android.view.MotionEvent
import androidx.core.graphics.div
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.times
import fr.benichn.math3.graphics.FormulaViewer
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.Utils.div
import fr.benichn.math3.graphics.Utils.times
import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.ObservableProperty
import fr.benichn.math3.types.callback.VCC
import fr.benichn.math3.types.callback.invoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    fun getAbsPos(e: MotionEvent): PointF? {
        val i = e.findPointerIndex(id)
        return if (i == -1) null else PointF(e.getX(i), e.getY(i))
    }
}

abstract class TouchAction(val getPos: (PointF) -> PointF = { it }, val longPressTimeout: Long = DEFAULT_LONG_PRESS_TIMEOUT) {
    data class Replacement(val newAction: TouchAction, val repeatEvent: Boolean = false, val longPress: Boolean = false)

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
            return moves.filter { t - it.millis < MOVE_VELOCITY_DELAY }.let { mvs ->
                if (mvs.isEmpty()) PointF() else mvs
                    .drop(1)
                    .groupBy { it.millis }
                    .map { (_, ms) -> ms.last() }
                    .fold(Move(PointF(), mvs[0].millis)) { acc, m ->
                        Move(
                            acc.diff + m.diff / (m.millis - acc.millis).toFloat(),
                            m.millis
                        )
                    }.diff * 1000f
            }
        }

    private val notifyFinished = Callback<TouchAction, Unit>(this)
    val onFinished = notifyFinished.Listener()

    private var longPressJob: Job? = null
    private suspend fun waitForLongPress() {
        delay(longPressTimeout)
        isLongPressed = true
        onLongDown()
    }

    protected abstract fun onDown()
    protected abstract fun onLongDown()
    protected abstract fun onMove()
    protected abstract fun onUp()
    protected abstract fun onPinchDown()
    protected abstract fun onPinchMove()
    protected abstract fun onPinchUp()
    protected abstract fun beforeFinish()

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

    fun onTouchEvent(e: MotionEvent): Replacement? {
        run {
            if (!isFinished) {
                if (isLaunched) {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_CANCEL -> {
                            finish()
                            return@run
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val absPos = prim.getAbsPos(e)
                            if (absPos == null) {
                                finish()
                                return@run
                            }
                            updatePos(absPos, false)
                            if (!hasMoved && Utils.l2(prim.downAbsPosition - absPos) > MINIMAL_MOVE_DISTANCE_SQ) {
                                hasMoved = true
                                longPressJob?.cancel()
                            }
                            if (hasMoved) {
                                if (isPinched) {
                                    val pinchAbsPos = pinch.getAbsPos(e)
                                    if (pinchAbsPos == null) {
                                        finish()
                                        return@run
                                    }
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
                                    val absPos = prim.getAbsPos(e)
                                    if (absPos == null) {
                                        finish()
                                        return@run
                                    }
                                    updatePos(absPos, false)
                                    finish(true)
                                }
                            }
                        }
                    } else if (isPinched) {
                        if (pinch.isTargeted(e)) {
                            when (e.actionMasked) {
                                MotionEvent.ACTION_POINTER_UP -> {
                                    val absPos = pinch.getAbsPos(e)
                                    if (absPos == null) {
                                        finish()
                                        return@run
                                    }
                                    updatePos(absPos, true)
                                    onPinchUp()
                                    pinchData = null
                                }
                            }
                            return@run
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
        return replacement
    }

    private fun createPinch(e: MotionEvent) =
        createPinch(
            e.getPointerId(e.actionIndex),
            PointF(e.getX(e.actionIndex), e.getY(e.actionIndex))
        )

    private fun createPinch(id: Int, downAbsPos: PointF) {
        longPressJob?.cancel()
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
            longPressJob = MainScope().launch {
                waitForLongPress()
            }
            onDown()
        }
    }

    fun launch(downEvent: MotionEvent, longPress: Boolean = false) {
        assert(downEvent.actionMasked == MotionEvent.ACTION_DOWN)
        val id = downEvent.getPointerId(downEvent.actionIndex)
        launch(PointF(downEvent.getX(downEvent.actionIndex), downEvent.getY(downEvent.actionIndex)), id, longPress)
    }

    // fun forceLongDown() {
    //     if (!isLongPressed) {
    //         longPressJob?.cancel()
    //         downTimer.onFinish()
    //     }
    // }

    var replacement: Replacement? = null
        private set

    fun replace(a: TouchAction, repeatEvent: Boolean = false, longPress: Boolean = false) {
        longPressJob?.cancel()
        replacement = Replacement(a, repeatEvent, longPress)
        beforeFinish()
        isFinished = true
        if (isPinched) {
            a.launchWithPinch(prim.downAbsPosition, prim.id, pinch.downAbsPosition, pinch.id, longPress)
        } else {
            a.launch(prim.downAbsPosition, prim.id, longPress)
        }
    }

    fun finish() = finish(false)
    private fun finish(callOnUp: Boolean) {
        longPressJob?.cancel()
        if (callOnUp) onUp()
        beforeFinish()
        isFinished = true
        notifyFinished()
    }

    companion object {
        const val DEFAULT_LONG_PRESS_TIMEOUT = 300L
        const val MINIMAL_MOVE_DISTANCE_SQ = 100
        const val MOVE_VELOCITY_DELAY = 100L
    }
}

open class TouchActionHandler {
    var touchAction: TouchAction? = null
        private set(value) {
            field = value
            value?.apply {
                onFinished += { _, _ -> touchAction = null }
            }
        }

    private fun runTouchAction(e: MotionEvent) {
        touchAction?.let {
            it.onTouchEvent(e)?.let { repl ->
                touchAction = repl.newAction
                if (repl.repeatEvent) runTouchAction(e)
            }
        }
    }

    protected open fun createTouchAction(e: MotionEvent): TouchAction? = null

    fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchAction = createTouchAction(e)
            }
        }
        runTouchAction(e)
        return true
    }
}