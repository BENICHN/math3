package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import fr.benichn.math3.Utils.Companion.neg
import fr.benichn.math3.Utils.Companion.pos
import fr.benichn.math3.graphics.Utils.Companion.l2
import fr.benichn.math3.graphics.Utils.Companion.moveToEnd
import fr.benichn.math3.graphics.Utils.Companion.times
import fr.benichn.math3.graphics.Utils.Companion.with
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sign

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    val input = InputFormulaBox()
    var box = TransformerFormulaBox(input, BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
        private set
    var caret: BoxCaret
        private set
    private val origin
        get() = PointF(width * 0.5f, height - FormulaBox.DEFAULT_TEXT_RADIUS)
    var offset = PointF()
        set(value) {
            field = adjustOffset(value)
            invalidate()
        }

    private fun findSingle(absPos: PointF) = box.findSingle(absPos)!!
    private fun doubleFromSingles(p1: CaretPosition.Single, p2: CaretPosition.Single) = CaretPosition.Double.fromSingles(p1, p2)!!

    private var accVelocity = PointF()
    private val velocityTimer = fixedRateTimer(period = VELOCITY_REDUCTION_PERIOD) {
        // Log.d("fix", accVelocity.toString())
        val diff = accVelocity * VELOCITY_REDUCTION_MULT * 0.25f
        val newVel = accVelocity.run { PointF(
            x - VELOCITY_REDUCTION * VELOCITY_REDUCTION_MULT * x.sign,
            y - VELOCITY_REDUCTION * VELOCITY_REDUCTION_MULT * y.sign
        ) }
        accVelocity = newVel.run { PointF(
            if (x.sign == accVelocity.x.sign) x else 0f,
            if (y.sign == accVelocity.y.sign) y else 0f
        ) }
        if (diff != PointF(0f, 0f)) {
            offset += diff
        }
    }

    private fun moveToCaret() {
        val p = caret.positions.lastOrNull()
        val pos = p?.absPos ?: (p as? CaretPosition.Single)?.getAbsPosition()
        pos?.let { it + origin + offset }?.run {
            // val radius = (p as? CaretPosition.Single)?.radius ?: FormulaBox.DEFAULT_TEXT_RADIUS
            val o = DEFAULT_PADDING // + radius
            val oxr = pos(x + o - width)
            val oxl = neg(x - o)
            val ox =
                if (oxr > 0f) {
                    if (oxl > 0f) {
                        0.5f * (oxl - oxr)
                    } else {
                        -oxr
                    }
                } else {
                    oxl
                }
            val oyb = pos(y + o - height)
            val oyt = neg(y - o)
            val oy =
                if (oyb > 0f) {
                    if (oyt > 0f) {
                        0.5f * (oyt - oyb)
                    } else {
                        -oyb
                    }
                } else {
                    oyt
                }
            offset += PointF(ox, oy)
        }
    }

    fun adjustOffset() {
        offset = offset
    }

    private fun adjustOffset(offset: PointF): PointF {
        val r = defaultPadding.applyOnRect(box.realBounds + origin + offset)
        val x = if (r.width() <= width) 0f else {
            val ol = r.left
            val or = r.right - width
            if (ol > 0) { // marge à gauche
                offset.x - ol
            } else { // depassement gauche
                if (or > 0) { // depassement droite
                    offset.x
                } else {
                    offset.x - or
                }
            }
        }
        val y = if (r.height() <= height) 0f else {
            val ot = r.top
            val ob = r.bottom - height
            if (ot > 0) { // marge en haut
                offset.y - ot
            } else { // depassement haut
                if (ob > 0) { // depassement bas
                    offset.y
                } else {
                    offset.y - ob
                }
            }
        }
        accVelocity = PointF(
            if (x == offset.x) accVelocity.x else 0f,
            if (y == offset.y) accVelocity.y else 0f
        )
        return PointF(x, y)
    }

    var isReadOnly by ObservableProperty(this, false).apply {
        onChanged += { _, e ->
            clearCaretPositions()
            input.isVisible = !e.new
        }
    }

    fun clearCaretPositions() {
        contextMenu = null
        caret.positions = listOf()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // touchAction?.finish()
        contextMenu = null
        adjustOffset()
        invalidate()
    }

    var contextMenu by ObservableProperty<FormulaView, ContextMenu?>(this, null) { _, e ->
        e.new?.run {
            onPictureChanged += { _, _ -> invalidate() }
        }
        invalidate()
    }

    var touchAction: TouchAction? by ObservableProperty<FormulaView, TouchAction?>(this, null) { _, e ->
        e.new?.apply {
            onFinished += { _, _ -> touchAction = null }
            onReplaced += { _, ev -> touchAction = ev.new }
        }
    }

    private data class PositionUp(
        val absPos: PointF,
        val millis: Long,
        val index: Int?
    )

    private var lastPlaceUp: PositionUp? = null
    private var isAdding = false

    var canMove = false
        private set

    private abstract inner class FormulaViewAction : TouchAction({ it - (origin + offset) })

    private inner class MoveViewAction : FormulaViewAction() {
        var baseGap = 0f
        var baseScale = 0f
        var baseOffset = PointF()

        override fun onDown() {
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            accVelocity += velocity
        }

        override fun onPinchDown() {
            baseScale = box.transformer(RectF()).scale
            baseGap = (prim.lastAbsPosition - pinch.lastAbsPosition).length()
            baseOffset = offset
        }

        override fun onPinchMove() {
            canMove = true
            val newGap = (prim.lastAbsPosition - pinch.lastAbsPosition).length()
            val ratio = (newGap / baseGap).let { if (abs(baseScale * it - 1f) < ZOOM_TOLERENCE) 1f/baseScale else it }
            val totalDiff = (prim.totalAbsDiff + pinch.totalAbsDiff) * 0.5f
            fun getPos(p: PointF) = p - (origin + baseOffset + totalDiff)
            val c = (getPos(prim.lastAbsPosition) + getPos(pinch.lastAbsPosition)) * 0.5f
            box.modifyTransformers { it.with(1, BoundsTransformer.Constant(BoxTransform.scale(baseScale * ratio))) }
            offset = baseOffset + totalDiff + c * (1 - ratio)
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            canMove = false
        }

        override fun onMove() {
            canMove = true
            offset += prim.lastAbsDiff
        }

    }

    private val notifyEnter = Callback<FormulaView, Unit>(this)
    val onEnter = notifyEnter.Listener()

    private inner class PlaceCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            if (caret.positions.isEmpty()) {
                notifyEnter()
            }
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            if (!isReadOnly) {
                if (caret.positions.isEmpty()) {
                    notifyEnter()
                }
                val b = box.findBox(prim.downPosition)
                b.contextMenu?.also { cm ->
                    if (cm.trigger(cm.source!!.accTransform.invert.applyOnPoint(prim.downPosition))) {
                        val p = CaretPosition.DiscreteSelection.fromBox(cm.source!!)!!
                        caret.positions = if (isAdding) {
                            getFiltered(caret.positions + p)
                        } else {
                            listOf(p)
                        }
                        contextMenu = cm.also {
                            it.origin = RectPoint.TOP_CENTER.get(it.source!!.accRealBounds) - PointF(0f, CONTEXT_MENU_OFFSET)
                            it.index = caret.positions.size-1
                        }
                    }
                }
                if (contextMenu == null) {
                    val p = findSingle(prim.downPosition)
                    caret.positions = if (isAdding) {
                        getFiltered(caret.positions + p)
                    } else {
                        listOf(p)
                    }
                }
            }
            lastPlaceUp = PositionUp(prim.downAbsPosition, System.currentTimeMillis(), caret.positions.size-1)
        }

        override fun onPinchDown() {
            replace(MoveViewAction())
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            replace(MoveViewAction())
        }

    }

    private inner class MoveCaretAction(val index: Int) : FormulaViewAction() {
        private lateinit var basePositions: List<CaretPosition>
        private lateinit var downSingle: CaretPosition.Single
        private var newSingle: CaretPosition.Single? = null

        override fun onDown() {
            basePositions = caret.positions.filterIndexed { i, _ -> i != index }
            downSingle = caret.positions[index] as CaretPosition.Single
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            newSingle = findSingle(prim.lastPosition)
            if (!hasMoved) {
                if (newSingle == downSingle) (displayContextMenu(index))
                lastPlaceUp = PositionUp(prim.downAbsPosition, System.currentTimeMillis(), if (isAdding) null else index)
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (!hasMoved && isAdding) {
                caret.positions = basePositions
            } else {
                caret.positions = getFiltered(basePositions + (newSingle ?: findSingle(prim.lastPosition)))
            }
        }

        override fun onMove() {
            caret.positions = getFiltered(basePositions + findSingle(prim.lastPosition).withModif(prim.lastPosition))
        }

    }

    private inner class CreateSelectionAction : FormulaViewAction() {
        private lateinit var downSingle: CaretPosition.Single
        private lateinit var basePositions: List<CaretPosition>

        override fun onDown() {
            downSingle = findSingle(prim.lastPosition)
            basePositions = if (isAdding) caret.positions else listOf()
            caret.positions = getFiltered(basePositions + doubleFromSingles(downSingle, downSingle).withModif(prim.lastPosition, downSingle.getAbsPosition()))
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            val s = findSingle(prim.lastPosition)
            val d = doubleFromSingles(downSingle, s)
            if (d.indexRange.start == d.indexRange.end) {
                caret.positions = getFiltered(
                    if (isReadOnly) basePositions
                    else basePositions + CaretPosition.Single(d.box, d.indexRange.start)
                )
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            caret.positions = caret.positions.map { it.withoutModif() }
        }

        override fun onMove() {
            val s = findSingle(prim.lastPosition)
            val p = CaretPosition.GridSelection.fromBoxes(downSingle.box, s.box)?.withModif(prim.lastPosition, downSingle.getAbsPosition()) ?:
                    doubleFromSingles(downSingle, s).withModif(prim.lastPosition, downSingle.getAbsPosition())
            caret.positions = getFiltered(basePositions + p)
        }
    }

    private inner class ModifySelectionAction(val index: Int, val downRectPoint: RectPoint) : FormulaViewAction() {
        private lateinit var fixedSingle: CaretPosition.Single
        private lateinit var basePositions: List<CaretPosition>

        override fun onDown() {
            val p = caret.positions[index]
            fixedSingle = when (p) {
                is CaretPosition.Double -> when (downRectPoint) {
                    RectPoint.CENTER_LEFT -> p.rightSingle
                    RectPoint.CENTER_RIGHT -> p.leftSingle
                    else -> throw IllegalArgumentException()
                }
                is CaretPosition.GridSelection -> when (downRectPoint) {
                    RectPoint.TOP_LEFT -> p.bottomRightSingle
                    RectPoint.TOP_RIGHT -> p.bottomLeftSingle
                    RectPoint.BOTTOM_LEFT -> p.topRightSingle
                    RectPoint.BOTTOM_RIGHT -> p.topLeftSingle
                    else -> throw IllegalArgumentException()
                }
                else -> throw UnsupportedOperationException()
            }
            basePositions = caret.positions.filterIndexed { i, _ -> i != index }
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            if (hasMoved) {
                val s = findSingle(prim.lastPosition)
                val d = doubleFromSingles(fixedSingle, s)
                if (d.indexRange.start == d.indexRange.end) {
                    caret.positions = getFiltered(
                        if (isReadOnly) basePositions
                        else basePositions + CaretPosition.Single(d.box, d.indexRange.start)
                    )
                }
            } else {
                lastPlaceUp = PositionUp(prim.downAbsPosition, System.currentTimeMillis(), index)
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (hasMoved) {
                caret.positions = caret.positions.map { it.withoutModif() }
            }
        }

        override fun onMove() {
            val s = findSingle(prim.lastPosition)
            val p =
                CaretPosition.GridSelection.fromBoxes(fixedSingle.box, s.box)?.withModif(prim.lastPosition, fixedSingle.getAbsPosition()) ?:
                    doubleFromSingles(fixedSingle, s).withModif(prim.lastPosition, fixedSingle.getAbsPosition())
            caret.positions = getFiltered(basePositions + p)
        }

    }

    private fun displayContextMenu(index: Int) {
        val p = caret.positions[index]
        val o = when (p) {
            is CaretPosition.Double -> RectPoint.TOP_CENTER.get(p.bounds)
            is CaretPosition.GridSelection -> RectPoint.TOP_CENTER.get(p.bounds)
            is CaretPosition.Single -> p.getAbsPosition() - PointF(0f, p.radius)
            else -> throw UnsupportedOperationException()
        }
        contextMenu = (if (p is CaretPosition.Single) getSingleContextMenu() else getSelectionContextMenu()).also {
            it.origin = o - PointF(0f, CONTEXT_MENU_OFFSET)
            it.index = index
        }
    }

     private inner class DisplayContextMenuAction(val index: Int) : FormulaViewAction() {
         override fun onDown() {
         }

         override fun onLongDown() {
             replace(CreateSelectionAction())
         }

         override fun onMove() {
             replace(MoveViewAction())
         }

         override fun onUp() {
             displayContextMenu(index)
             lastPlaceUp = PositionUp(prim.downAbsPosition, System.currentTimeMillis(), index)
         }

         override fun onPinchDown() {
             replace(MoveViewAction())
         }

         override fun onPinchMove() {
         }

         override fun onPinchUp() {
         }

         override fun beforeFinish(replacement: TouchAction?) {
         }

     }

     private inner class ContextMenuAction : FormulaViewAction() {
         var downEntry: ContextMenuEntry? = null
         val downEntryIndex
             get() = contextMenu!!.ent.indexOf(downEntry)

         override fun onDown() {
             downEntry = contextMenu!!.findEntry(prim.lastPosition)
             downEntry?.let {
                 contextMenu!!.fb.pressedItem = downEntryIndex
             }
         }

         override fun onLongDown() {
         }

         override fun onMove() {
             val entry = contextMenu!!.findEntry(prim.lastPosition)
             downEntry?.let {
                 contextMenu!!.fb.pressedItem = if (it == entry) downEntryIndex else null
             }
         }

         override fun onUp() {
             contextMenu?.let { cm ->
                 val entry = cm.findEntry(prim.lastPosition)
                 if (downEntry == entry) {
                     cm.source?.also {
                        entry?.action?.invoke(it)
                         caret.positions = getFiltered(caret.positions.with(cm.index, it.getInitialSingle() ?: CaretPosition.Single.fromBox(it, Side.R)!!))
                     } ?: run {
                        entry?.action?.invoke(caret.positions[cm.index])
                     }
                     contextMenu = null
                 }
             }
         }

         override fun onPinchDown() {
         }

         override fun onPinchMove() {
         }

         override fun onPinchUp() {
         }

         override fun beforeFinish(replacement: TouchAction?) {
         }

     }

    private inner class SelectionToDoubleAction(val index: Int) : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onMove() {
            replace(MoveViewAction())
        }

        override fun onUp() {
            val p = caret.positions[index] as CaretPosition.DiscreteSelection
            caret.positions = getFiltered(caret.positions.filterIndexed { i, _ -> i != index } + CaretPosition.Double.fromBoxes(p.selectedBoxes)!!)
            displayContextMenu(caret.positions.size-1)
        }

        override fun onPinchDown() {
            replace(MoveViewAction())
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

    }

    private inner class SelectWordAction(val index: Int?): FormulaViewAction() {
        override fun onDown() {
        }
        override fun onLongDown() {
            isAdding = !isAdding
            if (!isAdding) replace(PlaceCaretAction())
        }
        override fun onMove() {
        }
        override fun onUp() {
            if (!isLongPressed) {
                caret.positions = getFiltered(caret.positions.filterIndexed { i, _ -> i != index } + findSingle(prim.downPosition).let { s ->
                    CaretPosition.Double(s.box, Range(0, s.box.ch.size))
                })
            }
        }
        override fun onPinchDown() {
        }
        override fun onPinchMove() {
        }
        override fun onPinchUp() {
        }
        override fun beforeFinish(replacement: TouchAction?) {
        }
    }

    var syncHeight by ObservableProperty(this, true).apply {
        onChanged += { _, _ -> requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            widthMeasureSpec,
            if (syncHeight) {
                max(MIN_HEIGHT, ceil(box.realBounds.height() + 2 * DEFAULT_PADDING).toInt())
            } else {
                heightMeasureSpec
            }
        )
    }

    init {
        isClickable = true
        isLongClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            invalidate() }
        box.onBoundsChanged += { _, _ ->
            requestLayout()
            adjustOffset()
        }
        caret = box.createCaret()
        caret.onPositionsChanged += { _, _ ->
            moveToCaret()
        }
    }

    private fun updatePosition(old: CaretPosition, s: CaretPosition.Single, addedAfter: Int): CaretPosition {
        fun updateIndex(i: Int) =
            if (i > s.index) i + addedAfter else i
        return when (old) {
            is CaretPosition.DiscreteSelection -> old
            is CaretPosition.Double -> {
                if (old.box == s.box) {
                    CaretPosition.Double(
                        old.box,
                        Range(updateIndex(old.indexRange.start), updateIndex(old.indexRange.end))
                    )
                } else old
            }
            is CaretPosition.GridSelection -> old
            is CaretPosition.Single -> {
                if (old.box == s.box) {
                    CaretPosition.Single(
                        old.box,
                        updateIndex(old.index)
                    )
                } else old
            }
        }
    }

    private fun updatePositions(ps: MutableList<CaretPosition?>, s: CaretPosition.Single, addedAfter: Int) =
        ps.indices.forEach { i -> ps[i] = ps[i]?.let { p -> updatePosition(p, s, addedAfter) } }

    private fun verifyPositions(ps: MutableList<CaretPosition?>) {
        ps.indices.forEach { i -> ps[i] = ps[i]?.let { p ->
            if (p.box.root == box && p.selectedBoxes.all { it.root == box }) p else null
        } }
        ps.indices.forEach { i ->
            ps[i] = ps[i]?.let { p ->
                val ck = ps.withIndex().any { (j, pos) -> j != i && pos != null && (
                        pos.contains(p.box) ||
                        p.selectedBoxes.any { sb -> pos.contains(sb) } ||
                        p is CaretPosition.Single && p.index.let { k -> k in 1 until p.box.ch.size && pos.contains(p.box.ch[k]) && pos.contains(p.box.ch[k-1]) }
                        )
                }
                if (ck) null else p
            }
        }
    }

    private fun getVerified(ps: List<CaretPosition>): List<CaretPosition?> {
        val psm = ps.toMutableList<CaretPosition?>()
        verifyPositions(psm)
        return psm
    }

    private fun getFiltered(ps: List<CaretPosition>) = getVerified(ps).filterNotNull().distinct()

    fun sendAdd(newBox: () -> FormulaBox) {
        touchAction?.finish()
        contextMenu = null

        val ps = caret.positions.toMutableList<CaretPosition?>()

        fun addInInput(box: InputFormulaBox, selectionRange: Range): CaretPosition.Single {
            val nb = newBox()
            val i = selectionRange.start

            val noEnv = ps.count { pos -> pos != null && pos.box == box } >= 2
            val ib = InitialBoxes(
                if (noEnv) listOf() else box.ch.subList(0, selectionRange.start).toList(),
                box.ch.subList(selectionRange.start, selectionRange.end).toList(),
                if (noEnv) listOf() else box.ch.subList(selectionRange.end, box.ch.size).toList()
            )
            for (b in ib.selectedBoxes) {
                b.delete()
            }
            box.addBox(i, nb)
            val fb = nb.addInitialBoxes(ib)
            box.addBoxes(i+1, fb.boxesAfter)
            box.addBoxes(i, fb.boxesBefore)
            if (noEnv) {
                updatePositions(
                    ps,
                    CaretPosition.Single(box, i),
                    fb.run { boxesBefore.size + boxesAfter.size } + 1 - ib.selectedBoxes.size
                )
            }
            return nb.getInitialSingle() ?: CaretPosition.Single(box, i+1+fb.boxesBefore.size)
        }

        for (j in ps.indices) {
            val p = ps[j] ?: continue
            ps[j] = when (p) {
                is CaretPosition.DiscreteSelection -> p
                is CaretPosition.Single -> {
                    addInInput(p.box, Range(p.index, p.index))
                }
                is CaretPosition.Double -> {
                    addInInput(p.box, p.indexRange)
                }
                is CaretPosition.GridSelection -> {
                    ps.addAll(p.selectedInputs.map { box ->
                        addInInput(box, Range(0, box.ch.size))
                    })
                    null
                }
            }
            verifyPositions(ps)
        }

        caret.positions = ps.filterNotNull().distinct()
    }

    fun sendDelete() {
        touchAction?.finish()
        contextMenu = null

        val ps = caret.positions.toMutableList<CaretPosition?>()
        for (j in ps.indices) {
            val p = ps[j] ?: continue
            ps[j] = if (p is CaretPosition.GridSelection) {
                p.selectedInputs.map {
                    it.removeAllBoxes() // il ne peut pas y avoir de singles dedans
                    ps.add(it.lastSingle)
                }
                null
            } else {
                val dr = when (p) {
                    is CaretPosition.Single -> {
                        if (p.index == 0) {
                            fun isInputRoot(b: FormulaBox): Boolean =
                                b.parent?.let { if (it is InputFormulaBox) false else isInputRoot(it) } ?: true
                            if (!isInputRoot(p.box)) {
                                p.box.delete()
                            } else {
                                DeletionResult(p)
                            }
                        } else {
                            val b = p.box.ch[p.index - 1]
                            if (b.isFilled) {
                                DeletionResult.fromSelection(b)
                            } else {
                                b.delete().withFinalBoxes(b)
                            }
                        }
                    }

                    is CaretPosition.Double -> {
                        p.selectedBoxes.forEach { it.delete() }
                        DeletionResult(p.leftSingle)
                    }

                    is CaretPosition.DiscreteSelection -> {
                        p.callDelete()
                    }

                    else -> DeletionResult() // inatteignable
                }

                when(dr.newPos) {
                    is CaretPosition.Single -> {
                        updatePositions(
                            ps,
                            dr.newPos,
                            dr.finalBoxes.run { boxesBefore.size + boxesAfter.size } - if (dr.deleted) 1 else 0
                        )
                        if (!dr.finalBoxes.isEmpty) {
                            dr.newPos.box.addFinalBoxes(dr.newPos.index, dr.finalBoxes)
                        } else {
                            dr.newPos
                        }
                    }
                    else -> dr.newPos
                }
            }
            verifyPositions(ps)
        }

        caret.positions = ps.filterNotNull().distinct()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundPaint.color)
        val o = origin + offset
        val y = box.child.transform.origin.y + o.y
        canvas.drawLine(0f, y, o.x + box.realBounds.left - FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        canvas.drawLine(width.toFloat(), y, o.x + box.realBounds.right + FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        (origin + offset).let { canvas.translate(it.x, it.y) }
        box.drawOnCanvas(canvas)
        contextMenu?.box?.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Log.d("touch", "${e.actionIndex}, ${e.getPointerId(e.actionIndex)} ~ $e")
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                accVelocity = PointF()
                if (touchAction == null) {
                    val ap = PointF(e.x, e.y)
                    val pos = ap - (offset + origin)
                    contextMenu?.also { cm ->
                        when (cm.findElement(pos)) {
                            ContextMenu.Element.INTERIOR -> {
                                touchAction = ContextMenuAction()
                            }
                            ContextMenu.Element.NONE -> {
                                contextMenu = null
                                val p = caret.positions[cm.index]
                                if (p is CaretPosition.Double && p.getElement(pos) == CaretPosition.Double.Element.INTERIOR
                                    || p is CaretPosition.GridSelection && p.getElement(pos) == CaretPosition.GridSelection.Element.INTERIOR
                                    || p is CaretPosition.DiscreteSelection && p.getElement(pos) == CaretPosition.DiscreteSelection.Element.INTERIOR) {
                                    touchAction = PlaceCaretAction()
                                }
                            }
                        }
                    }
                    lastPlaceUp?.also { (absPos, t, i) ->
                        if ((System.currentTimeMillis() - t < DOUBLE_TAP_DELAY) && l2(absPos - ap) <= BoxCaret.SINGLE_MAX_TOUCH_DIST_SQ) {
                            touchAction = SelectWordAction(i)
                        } else {
                            lastPlaceUp = null
                        }
                    }
                    if (touchAction == null) {
                        touchAction = caret.positions.withIndex().firstNotNullOfOrNull { (j, p) ->
                            when (p) {
                                is CaretPosition.Single -> {
                                    when (p.getElement(pos)) {
                                        CaretPosition.Single.Element.BAR ->
                                            MoveCaretAction(j)

                                        CaretPosition.Single.Element.NONE ->
                                            null
                                    }
                                }

                                is CaretPosition.Double -> {
                                    when (p.getElement(pos)) {
                                        CaretPosition.Double.Element.LEFT_BAR -> ModifySelectionAction(j, RectPoint.CENTER_LEFT)
                                        CaretPosition.Double.Element.RIGHT_BAR -> ModifySelectionAction(j, RectPoint.CENTER_RIGHT)
                                        CaretPosition.Double.Element.INTERIOR -> DisplayContextMenuAction(j)
                                        CaretPosition.Double.Element.NONE -> null
                                    }
                                }

                                is CaretPosition.DiscreteSelection -> {
                                    when (p.getElement(pos)) {
                                        CaretPosition.DiscreteSelection.Element.INTERIOR -> PlaceCaretAction() // SelectionToDoubleAction(j)
                                        CaretPosition.DiscreteSelection.Element.NONE -> null
                                    }
                                }

                                is CaretPosition.GridSelection -> {
                                    when (p.getElement(pos)) {
                                        CaretPosition.GridSelection.Element.CORNER_TL -> ModifySelectionAction(j, RectPoint.TOP_LEFT)
                                        CaretPosition.GridSelection.Element.CORNER_TR -> ModifySelectionAction(j, RectPoint.TOP_RIGHT)
                                        CaretPosition.GridSelection.Element.CORNER_BR -> ModifySelectionAction(j, RectPoint.BOTTOM_RIGHT)
                                        CaretPosition.GridSelection.Element.CORNER_BL -> ModifySelectionAction(j, RectPoint.BOTTOM_LEFT)
                                        CaretPosition.GridSelection.Element.INTERIOR -> DisplayContextMenuAction(j)
                                        CaretPosition.GridSelection.Element.NONE -> null
                                    }
                                }
                            }
                        } ?: PlaceCaretAction()
                    }
                }
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
        val red = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.RED }
        val baselinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.BLACK }
        val backgroundPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.DKGRAY
        }
        val magnifierBorder = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.LTGRAY
        }

        fun getSingleContextMenu() = ContextMenu(
            ContextMenuEntry.create<CaretPosition>(TextFormulaBox("paste")) {  }
        )
        fun getSelectionContextMenu() = ContextMenu(
            ContextMenuEntry.create<CaretPosition>(TextFormulaBox("copy")) {  },
            ContextMenuEntry.create<CaretPosition>(TextFormulaBox("cut")) {  },
            ContextMenuEntry.create<CaretPosition>(TextFormulaBox("paste")) {  }
        )

        const val DEFAULT_PADDING = FormulaBox.DEFAULT_TEXT_WIDTH
        const val MIN_HEIGHT = (FormulaBox.DEFAULT_TEXT_SIZE + 2 * DEFAULT_PADDING).toInt()
        val defaultPadding = Padding(DEFAULT_PADDING)
        const val VELOCITY_REDUCTION_PERIOD = 8L
        const val VELOCITY_REDUCTION_MULT = VELOCITY_REDUCTION_PERIOD * 0.001f
        const val VELOCITY_REDUCTION = 25000f // en px.s^-2
        const val ZOOM_TOLERENCE = 0.1f
        const val DOUBLE_TAP_DELAY = 150
        const val CONTEXT_MENU_OFFSET = FormulaBox.DEFAULT_TEXT_RADIUS * 0.25f
    }
}