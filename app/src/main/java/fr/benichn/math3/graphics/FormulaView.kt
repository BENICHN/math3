package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.times
import com.google.gson.JsonElement
import fr.benichn.math3.App
import fr.benichn.math3.ContextMenuView
import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.Utils.toJsonArray
import fr.benichn.math3.Utils.toPt
import fr.benichn.math3.formulas.FormulaGroupedToken.Companion.readGroupedTokenFlattened
import fr.benichn.math3.graphics.PopupView.Companion.destroyPopup
import fr.benichn.math3.graphics.PopupView.Companion.getCoordsInPopupView
import fr.benichn.math3.graphics.PopupView.Companion.requirePopup
import fr.benichn.math3.graphics.Utils.l2
import fr.benichn.math3.graphics.Utils.with
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.GridFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.hasText
import fr.benichn.math3.graphics.boxes.isLetters
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.types.CellMode
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.numpad.NamesBar
import fr.benichn.math3.numpad.types.Pt
import fr.benichn.math3.types.callback.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.StringReader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class FormulaView(context: Context, attrs: AttributeSet? = null) : FormulaViewer(context, attrs) {
    override val initialBoxTransformers: Array<BoundsTransformer>
        get() = arrayOf(BoundsTransformer.Align(RectPoint.TOP_LEFT), BoundsTransformer.id)

    var cellMode = CellMode.ONE_LETTER_VAR

    var caret: BoxCaret
        private set
    private val origin
        get() = PointF(FormulaBox.DEFAULT_TEXT_WIDTH, FormulaBox.DEFAULT_TEXT_RADIUS)
    var offset = PointF()
        set(value) {
            field = adjustOffset(value)
            invalidate()
        }

    private var accVelocity = PointF()

    private val notifyScaleChanged = VCC<FormulaView, Float>(this)
    val onScaleChanged = notifyScaleChanged.Listener()

    var scale
        get() = (box.transformers[1] as BoundsTransformer.Constant).value.scale
        set(value) {
            box.modifyTransformers { it.with(1, BoundsTransformer.Constant(BoxTransform.scale(value))) }
        }

    var magneticScale = 1f

    init {
        // setBackgroundColor(defaultBackgroundColor)
        App.instance.main.namesBarView.onButtonClicked += { _, s ->
            onNameSelected(s)
        }
        child = InputFormulaBox()
        box.dlgTransformers.onChanged += { _, e ->
            notifyScaleChanged(
                (e.old[1] as BoundsTransformer.Constant).value.scale,
                (e.new[1] as BoundsTransformer.Constant).value.scale
            )
        }
        box.onBoundsChanged += { _, _ ->
            requestLayout()
            adjustOffset()
        }
        caret = box.createCaret()
        caret.onPositionsChanged += { _, _ ->
            moveToCaret()
            updatePattern()
        }
        MainScope().launch {
            while (true) {
                val diff = accVelocity * VELOCITY_REDUCTION_MULT * 0.25f
                val newVel = accVelocity.run {
                    PointF(
                        x - VELOCITY_REDUCTION * VELOCITY_REDUCTION_MULT * x.sign,
                        y - VELOCITY_REDUCTION * VELOCITY_REDUCTION_MULT * y.sign
                    )
                }
                accVelocity = newVel.run {
                    PointF(
                        if (x.sign == accVelocity.x.sign) x else 0f,
                        if (y.sign == accVelocity.y.sign) y else 0f
                    )
                }
                if (diff != PointF(0f, 0f)) {
                    offset += diff
                }
                delay(VELOCITY_REDUCTION_PERIOD)
            }
        }
    }

    val input = child as InputFormulaBox

    private var contextMenu: ContextMenu? = null

    private fun findSingle(absPos: PointF) = box.findSingle(absPos)!!
    private fun doubleFromSingles(p1: CaretPosition.Single, p2: CaretPosition.Single) = CaretPosition.Double.fromSingles(p1, p2)!!

    private fun moveToCaret() {
        val p = caret.positions.lastOrNull()
        val br = p?.absPos?.run { RectF(x, y-FormulaBox.DEFAULT_TEXT_RADIUS, x, y+FormulaBox.DEFAULT_TEXT_RADIUS) } ?: (p as? CaretPosition.Single)?.barRect ?: return
        val r = defaultPadding.applyOnRect(br) + origin + offset
        offset += PointF(
            min(0f, width - r.right) + max(0f, -r.left),
            min(0f, height - r.bottom) + max(0f, -r.top)
        )
    }

    private fun adjustOffset() {
        offset = offset
    }

    private fun onNameSelected(name: String) {
        patternBoxes?.let { pb ->
            if (pb.isNotEmpty()) {
                CaretPosition.Single.getParentInput(pb[0])?.let { (inp, i) ->
                    pb.forEach { it.delete() }
                    val b = MatrixFormulaBox(matrixType = MatrixFormulaBox.Type.PARAMS) // !
                    inp.addBoxes(i, TextFormulaBox(name), b)
                    caret.positions = caret.positions.with(caret.positions.lastIndex, b.grid.inputs[0].lastSingle) // !
                }
            }
        }
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
        destroyPopup()
        caret.positions = listOf()
    }

    fun setCaretOnEnd() {
        caret.positions = listOf(input.lastSingle)
        moveToCaret()
    }

    private fun getPatternBoxes() = caret.positions.lastOrNull()?.let { p ->
        when (p) {
            is CaretPosition.Single -> {
                val (inp, index) = p.parentInput
                val i0 =
                    if (p.box.isLetters()) index
                    else if (index < inp.ch.lastIndex && inp.ch[index+1].isLetters()) index+1
                    else -1
                if (i0 != -1) {
                    val (start, end) = inp.ch.withIndex().toList().let { chi ->
                        chi.indexOfLast { (i, b) -> i < i0 && !b.isLetters() }+1 to
                        chi.indexOfFirst { (i, b) -> i > i0 && !b.isLetters() }.let { j -> if (j == -1) inp.ch.size else j }
                    }
                    val letters = inp.ch.subList(start,end)
                    val firstMaj = letters.indexOfFirst { b -> b.hasText { it.isNotEmpty() && it[0].isUpperCase() } }
                    letters.subList(if (firstMaj == -1) 0 else firstMaj, letters.size).toList()
                } else null
            }
            else -> null
        }
    }

    private var patternBoxes: List<FormulaBox>? = null

    private fun updatePattern() {
        patternBoxes = getPatternBoxes()
        App.instance.main.namesBarView.let { nv ->
            val pattern = patternBoxes?.joinToString("") { it.toWolfram() }
            if (pattern?.run { length >= NamesBar.MIN_PATTERN_LENGT } == true) {
                nv.visibility = VISIBLE
                nv.barBox.pattern = pattern
            } else {
                nv.visibility = GONE
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        destroyPopup()
        adjustOffset()
    }

    private data class PositionUp(
        val absPos: PointF,
        val millis: Long,
        val index: Int?
    )

    private var lastPlaceUp: PositionUp? = null
    private var isAdding = false

    var movingState = MovingState.NONE
        private set
    enum class MovingState {
        NONE,
        MOVING,
        PINCHING
    }

    private abstract inner class FormulaViewAction : TouchAction({ it - (origin + offset) })

    private inner class MoveViewAction : FormulaViewAction() {
        var baseGap = 0f
        var baseScale = 0f
        var baseOffset = PointF()

        override fun onDown() {
            movingState = MovingState.MOVING
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            accVelocity += velocity
        }

        override fun onPinchDown() {
            baseScale = scale
            baseGap = (prim.lastAbsPosition - pinch.lastAbsPosition).length()
            baseOffset = offset
        }

        override fun onPinchMove() {
            movingState = MovingState.PINCHING
            val newGap = (prim.lastAbsPosition - pinch.lastAbsPosition).length()
            val ratio = (newGap / baseGap).let { if (abs(baseScale * it - magneticScale) < ZOOM_TOLERENCE) magneticScale/baseScale else it }
            val totalDiff = (prim.totalAbsDiff + pinch.totalAbsDiff) * 0.5f
            fun getPos(p: PointF) = p - (origin + baseOffset + totalDiff)
            val c = (getPos(prim.lastAbsPosition) + getPos(pinch.lastAbsPosition)) * 0.5f
            scale = baseScale * ratio
            offset = baseOffset + totalDiff + c * (1 - ratio)
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish() {
            movingState = MovingState.NONE
        }

        override fun onMove() {
            offset += prim.lastAbsDiff
        }

    }

    private val notifyEnter = Callback<FormulaView, Unit>(this)
    val onEnter = notifyEnter.Listener()

    private fun requireContextMenu(
        contextMenu: ContextMenu,
        sourceBounds: RectF,
        sourceRP: RectPoint = RectPoint.TOP_CENTER,
        sourcePadding: Padding = Padding(CONTEXT_MENU_OFFSET)) {
        requirePopup(ContextMenuView(context).also {
            it.contextMenu = contextMenu.also { cm ->
                cm.destroyPopup = { destroyPopup() }
                this.contextMenu = cm
            }
        }, sourceBounds + origin + offset, sourceRP, sourcePadding) { this.contextMenu = null }
    }

    private var magnifier: FormulaMagnifier? = null

    private fun requireMagnifier(
        absPos: PointF
    ) {
        getCoordsInPopupView()?.let { o ->
            val mg = FormulaMagnifier(context)
            mg.box = box
            mg.absPos = absPos
            mg.origin = offset + origin + o
            magnifier = mg
            requirePopup(mg, 0, 0) { magnifier = null }
        }
    }

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
                var isContextMenuSet = false
                b.contextMenu?.also { cm ->
                    if (cm.trigger(cm.source!!.accTransform.invert.applyOnPoint(prim.downPosition))) {
                        val p = CaretPosition.DiscreteSelection.fromBox(cm.source!!)!!
                        caret.positions = if (isAdding) {
                            getFiltered(caret.positions + p)
                        } else {
                            listOf(p)
                        }
                        cm.ents.forEach { ent ->
                            ent.finalAction = {
                                cm.source!!.let {
                                    val newPos = ent.action(it) ?: /*it.getInitialSingle() ?: */listOf(CaretPosition.Single.fromBox(it))
                                    caret.positions = getFiltered(caret.positions.dropLast(1) + newPos)
                                }
                            }
                        }
                        cm.index = caret.positions.lastIndex
                        requireContextMenu(cm, cm.source!!.accRealBounds)
                        isContextMenuSet = true
                    }
                }
                if (!isContextMenuSet) {
                    val p = findSingle(prim.downPosition)
                    caret.positions = if (isAdding) {
                        getFiltered(caret.positions + p)
                    } else {
                        listOf(p)
                    }
                }
            } else {
                clearCaretPositions()
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

        override fun beforeFinish() {
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
                if (!isAdding && newSingle == downSingle) (displayContextMenu(index))
                lastPlaceUp = PositionUp(prim.downAbsPosition, System.currentTimeMillis(), if (isAdding) null else index)
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish() {
            if (magnifier != null) destroyPopup()
            if (!hasMoved && isAdding) {
                caret.positions = basePositions
            } else {
                caret.positions = getFiltered(basePositions + (newSingle ?: findSingle(prim.lastPosition)))
            }
        }

        override fun onMove() {
            magnifier?.also { mg -> mg.absPos = prim.lastPosition } ?: run { requireMagnifier(prim.lastPosition) }
            caret.positions = getFiltered(basePositions + findSingle(prim.lastPosition).withModif(prim.lastPosition))
        }

    }

    private inner class CreateSelectionAction : FormulaViewAction() {
        private lateinit var downSingle: CaretPosition.Single
        private lateinit var basePositions: List<CaretPosition>

        override fun onDown() {
            downSingle = findSingle(prim.lastPosition)
            basePositions = if (isAdding) caret.positions else listOf()
            caret.positions = getFiltered(basePositions + doubleFromSingles(downSingle, downSingle).withModif(prim.lastPosition/*, downSingle.getAbsPosition()*/))
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            val s = findSingle(prim.lastPosition)
            val d = doubleFromSingles(downSingle, s)
            if (d.startIndex == d.endIndex) {
                caret.positions = getFiltered(
                    if (isReadOnly) basePositions
                    else basePositions + d.leftSingle
                )
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish() {
            caret.positions = caret.positions.map { it.withoutModif() }
        }

        override fun onMove() {
            val s = findSingle(prim.lastPosition)
            val p = CaretPosition.GridSelection.fromBoxes(downSingle.box, s.box)?.withModif(prim.lastPosition/*, downSingle.getAbsPosition()*/) ?:
                    doubleFromSingles(downSingle, s).withModif(prim.lastPosition/*, downSingle.getAbsPosition()*/)
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
                if (d.startIndex == d.endIndex) {
                    caret.positions = getFiltered(
                        if (isReadOnly) basePositions
                        else basePositions + d.leftSingle
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

        override fun beforeFinish() {
            if (hasMoved) {
                caret.positions = caret.positions.map { it.withoutModif() }
            }
        }

        override fun onMove() {
            val s = findSingle(prim.lastPosition)
            val p =
                CaretPosition.GridSelection.fromBoxes(fixedSingle.box, s.box)?.withModif(prim.lastPosition/*, fixedSingle.getAbsPosition()*/) ?:
                    doubleFromSingles(fixedSingle, s).withModif(prim.lastPosition/*, fixedSingle.getAbsPosition()*/)
            caret.positions = getFiltered(basePositions + p)
        }

    }

    private fun displayContextMenu(index: Int) {
        val p = caret.positions[index]
        val r = when (p) {
            is CaretPosition.Double -> p.bounds
            is CaretPosition.GridSelection -> p.bounds
            is CaretPosition.Single -> p.barRect
            else -> throw UnsupportedOperationException()
        }
        val cm = if (p is CaretPosition.Single) getSingleContextMenu() else getSelectionContextMenu()
        cm.ents.forEach { ent ->
            ent.finalAction = {
                val newPos = ent.action(caret.positions[index])
                caret.positions = getFiltered(caret.positions.dropLast(1) + (newPos ?: listOf()))
            }
        }
        cm.index = index
        requireContextMenu(cm, r)
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

         override fun beforeFinish() {
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

        override fun beforeFinish() {
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
                notifyEnter()
                val s = findSingle(prim.downPosition)
                val pi = s.parentInput
                val n = pi.input.ch.size
                caret.positions = getFiltered(caret.positions.filterIndexed { i, _ -> i != index } +
                        when {
                            n > 1 -> listOf(CaretPosition.Double(pi.input, 0, n-1))
                            isReadOnly -> listOf()
                            else -> listOf(CaretPosition.Single(pi.input, 0))
                        }
                )
                if (!isReadOnly) displayContextMenu(caret.positions.size-1)
            }
        }
        override fun onPinchDown() {
        }
        override fun onPinchMove() {
        }
        override fun onPinchUp() {
        }
        override fun beforeFinish() {
        }
    }

    var syncHeight by ObservableProperty(this, true).apply {
        onChanged += { _, _ -> requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (syncHeight) {
            setMeasuredDimension(
                widthMeasureSpec,
                ceil(box.realBounds.height() + 2 * DEFAULT_PADDING).toInt()
            )
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    // private fun updatePosition(old: CaretPosition, s: CaretPosition.Single, addedAfter: Int): CaretPosition {
    //     fun updateIndex(i: Int) =
    //         if (i > s.index) i + addedAfter else i
    //     return when (old) {
    //         is CaretPosition.DiscreteSelection -> old
    //         is CaretPosition.Double -> {
    //             if (old.box == s.box) {
    //                 CaretPosition.Double(
    //                     old.box,
    //                     Range(updateIndex(old.indexRange.start), updateIndex(old.indexRange.end))
    //                 )
    //             } else old
    //         }
    //         is CaretPosition.GridSelection -> old
    //         is CaretPosition.Single -> {
    //             if (old.box == s.box) {
    //                 CaretPosition.Single(
    //                     old.box,
    //                     updateIndex(old.index)
    //                 )
    //             } else old
    //         }
    //     }
    // }

    // private fun updatePositions(ps: MutableList<CaretPosition?>, s: CaretPosition.Single, addedAfter: Int) =
    //     ps.indices.forEach { i -> ps[i] = ps[i]?.let { p -> updatePosition(p, s, addedAfter) } }

    private fun verifyPositions(ps: MutableList<CaretPosition?>) {
        ps.indices.forEach { i -> ps[i] = ps[i]?.let { p ->
            if (p.isValid(box)) p else null
        } }
        // ps.indices.forEach { i -> // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //     ps[i] = ps[i]?.let { p ->
        //         val ck = ps.withIndex().any { (j, pos) -> j != i && pos != null && (
        //                 pos.contains(p.box) ||
        //                 p.selectedBoxes.any { sb -> pos.contains(sb) } ||
        //                 p is CaretPosition.Single && p.index.let { k -> k in 1 until p.box.ch.size && pos.contains(p.box.ch[k]) && pos.contains(p.box.ch[k-1]) }
        //                 )
        //         }
        //         if (ck) null else p
        //     }
        // }
    }

    private fun getVerified(ps: List<CaretPosition>): List<CaretPosition?> {
        val psm = ps.toMutableList<CaretPosition?>()
        verifyPositions(psm)
        return psm
    }

    private fun getFiltered(ps: List<CaretPosition?>) = getVerified(ps.filterNotNull()).filterNotNull().distinct()

    fun sendAdd(newBox: () -> FormulaBox) {
        if (isReadOnly) return
        touchAction?.finish()
        destroyPopup()

        val ps = caret.positions.toMutableList<CaretPosition?>()

        fun addInInput(box: InputFormulaBox, selectionStart: Int, selectionEnd: Int): CaretPosition.Single {
            val nb = newBox()
            // val noEnv = ps.count { pos -> pos != null && pos.box == box } >= 2
            val ib = InitialBoxes(
                /*if (noEnv) listOf() else*/ box.ch.subList(1, selectionStart+1).toList(),
                box.ch.subList(selectionStart+1, selectionEnd+1).toList(),
                /*if (noEnv) listOf() else*/ box.ch.subList(selectionEnd+1, box.ch.size).toList()
            )
            for (b in ib.selectedBoxes) {
                b.delete()
            }
            box.addBoxes(selectionStart+1, nb)
            val fb = nb.addInitialBoxes(ib)
            box.addBoxesAfter(nb, fb.boxesAfter)
            box.addBoxesBefore(nb, fb.boxesBefore)
            // if (noEnv) {
            //     updatePositions(
            //         ps,
            //         CaretPosition.Single(box, newI),
            //         fb.run { boxesBefore.size + boxesAfter.size } + 1 - ib.selectedBoxes.size
            //     )
            // }
            return nb.getInitialSingle() ?: CaretPosition.Single.fromBox(nb)!!
        }

        for (j in ps.indices) {
            val p = ps[j] ?: continue
            ps[j] = when (p) {
                is CaretPosition.DiscreteSelection -> p
                is CaretPosition.Single -> {
                    val pi = p.parentInput
                    addInInput(pi.input, pi.index, pi.index)
                }
                is CaretPosition.Double -> {
                    addInInput(p.input, p.startIndex, p.endIndex)
                }
                is CaretPosition.GridSelection -> {
                    ps.addAll(p.selectedInputs.map { box ->
                        addInInput(box, 0, box.ch.size-1)
                    })
                    null
                }
            }
            verifyPositions(ps)
        }

        caret.positions = ps.filterNotNull().distinct()
    }

    fun sendDelete() {
        if (isReadOnly) return
        touchAction?.finish()
        destroyPopup()

        val ps = caret.positions.toMutableList<CaretPosition?>()
        for (j in ps.indices) {
            val p = ps[j] ?: continue
            ps[j] = if (p is CaretPosition.GridSelection) {
                p.selectedInputs.map {
                    it.clearBoxes() // il ne peut pas y avoir de singles dedans
                    ps.add(it.lastSingle)
                }
                null
            } else {
                val dr = when (p) {
                    is CaretPosition.Single -> {
                        val b = p.box
                        val pi = p.parentInput
                        if (pi.index == 0) {
                            if (CaretPosition.Single.getParentInput(pi.input) != null) {
                                pi.input.delete()
                            } else {
                                DeletionResult(p)
                            }
                        } else {
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
                        // updatePositions(
                        //     ps,
                        //     dr.newPos,
                        //     dr.finalBoxes.run { boxesBefore.size + boxesAfter.size } - if (dr.deleted) 1 else 0
                        // )
                        if (!dr.finalBoxes.isEmpty) {
                            dr.newPos.parentInput.let { (inp, i) -> inp.addFinalBoxes(i, dr.finalBoxes) }
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

    fun getSingleContextMenu() = ContextMenu(
        ContextMenuEntry.create<CaretPosition>(TextFormulaBox("paste")) { p ->
            val (input, i) = (p as CaretPosition.Single).parentInput
            App.pasteFromClipboard().let { items ->
                FormulaBoxesClipData.formClips(items, cellMode)?.let { cd ->
                    if (cd is FormulaBoxesClipData.Grid && !input.isFilled) {
                        val g = input.parent?.parent
                        if (g is GridFormulaBox && g.shape == Pt(1,1)) {
                            g.addRows(1, cd.shape.y-1)
                            g.addColumns(1, cd.shape.x-1)
                            g.preventUpdate()
                            return@create g.inputs.mapIndexed { i, inp ->
                                inp.addBoxes(cd.inputsValue[i])
                                inp.lastSingle
                            }.also { g.retrieveUpdate() }
                        }
                    }
                    val bs = cd.toBoxes()
                    input.addBoxes(i+1, bs)
                    listOf(bs.lastOrNull()?.let { b -> CaretPosition.Single(b) } ?: p)
                }
            }
        }
    )
    fun copySelection(p: CaretPosition) = when (p) {
        is CaretPosition.DiscreteSelection -> throw UnsupportedOperationException()
        is CaretPosition.Double -> App.copyToClipboard(
            p.selectedBoxes.joinToString("") { it.toWolfram(cellMode) },
            p.selectedBoxes.map { it.toJson() }.toJsonArray().toString()
        )
        is CaretPosition.GridSelection -> {
            App.copyToClipboard("{" + p.ptsRange.rows.joinToString(",") { i ->
                "{" + p.ptsRange.columns.joinToString(",") { j ->
                    p.box.getInput(Pt(j, i)).toWolfram(cellMode)
                } + "}"
            } + "}",
                FormulaBox.makeJsonObject("grid") {
                    add("shape", p.ptsRange.run { br - tl + Pt(1,1) }.toJson())
                    add("inputs", p.ptsRange.map { pt -> p.box.getInput(pt).toJson() }.toJsonArray())
                }.toString()
            )
        }
        is CaretPosition.Single -> throw UnsupportedOperationException()
    }
    fun getSelectionContextMenu() = ContextMenu(
        ContextMenuEntry.create<CaretPosition>(TextFormulaBox("copy")) { p ->
            copySelection(p)
            (p as? CaretPosition.Double)?.run { listOf(rightSingle) }
        },
        ContextMenuEntry.create<CaretPosition>(TextFormulaBox("cut")) { p ->
            copySelection(p)
            if (isReadOnly) listOf(p)
            else when (p) {
                is CaretPosition.Double -> {
                    p.input.removeBoxes(p.selectedBoxes)
                    listOf(p.leftSingle)
                }
                is CaretPosition.GridSelection -> {
                    p.selectedInputs.forEach { b -> b.clear() }
                    null
                }
                else -> throw UnsupportedOperationException()
            }
        },
        ContextMenuEntry.create<CaretPosition>(TextFormulaBox("paste")) { p ->
            when (p) {
                is CaretPosition.Double -> {
                    p.selectedBoxes.forEach { b -> b.delete() }
                    App.pasteFromClipboard().let { items ->
                        FormulaBoxesClipData.formClips(items, cellMode)?.let { cd ->
                            if (cd is FormulaBoxesClipData.Grid && !p.input.isFilled) {
                                val g = p.input.parent?.parent
                                if (g is GridFormulaBox && g.shape == Pt(1,1)) {
                                    g.addRows(1, cd.shape.y-1)
                                    g.addColumns(1, cd.shape.x-1)
                                    g.preventUpdate()
                                    return@create g.inputs.mapIndexed { i, inp ->
                                        inp.addBoxes(cd.inputsValue[i])
                                        inp.lastSingle
                                    }.also { g.retrieveUpdate() }
                                }
                            }
                            val bs = cd.toBoxes()
                            p.input.addBoxes(p.startIndex + 1, bs)
                            listOf(bs.lastOrNull()?.let { b -> CaretPosition.Single(b) } ?: p.leftSingle)
                        }
                    }
                }
                is CaretPosition.GridSelection -> {
                    val inps = p.selectedInputs
                    inps.forEach { b -> b.clear() }
                    App.pasteFromClipboard().let { items ->
                        FormulaBoxesClipData.formClips(items, cellMode)?.let { cd ->
                            when {
                                cd is FormulaBoxesClipData.Grid && cd.shape == p.ptsRange.run { br - tl + Pt(1,1) } -> {
                                    inps.mapIndexed { i, inp ->
                                        inp.addBoxes(cd.inputsValue[i])
                                        inp.lastSingle
                                    }
                                }
                                else -> {
                                    inps.map { inp ->
                                        inp.addBoxes(cd.toBoxes())
                                        inp.lastSingle
                                    }
                                }
                            }
                        }
                    }
                }
                else -> null
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        val o = origin + offset
        // val y = box.child.transform.origin.y + o.y
        // canvas.drawLine(0f, y, o.x + box.realBounds.left - FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        // canvas.drawLine(width.toFloat(), y, o.x + box.realBounds.right + FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        (origin + offset).let { canvas.translate(it.x, it.y) }
        super.onDraw(canvas)
    }

    override fun createTouchAction(e: MotionEvent): TouchAction {
        accVelocity = PointF()
        val ap = PointF(e.x, e.y)
        val pos = ap - (offset + origin)
        return contextMenu?.let { cm ->
            val p = caret.positions[cm.index]
            if (p is CaretPosition.Double && p.getElement(pos) == CaretPosition.Double.Element.INTERIOR
                || p is CaretPosition.GridSelection && p.getElement(pos) == CaretPosition.GridSelection.Element.INTERIOR
                || p is CaretPosition.DiscreteSelection && p.getElement(pos) == CaretPosition.DiscreteSelection.Element.INTERIOR
            ) {
                PlaceCaretAction()
            } else null
        } ?: lastPlaceUp?.let { (absPos, t, i) ->
            if ((System.currentTimeMillis() - t < DOUBLE_TAP_DELAY) && l2(absPos - ap) <= BoxCaret.SINGLE_MAX_TOUCH_DIST_SQ) {
                SelectWordAction(i)
            } else {
                lastPlaceUp = null
                null
            }
        } ?: caret.positions.withIndex().firstNotNullOfOrNull { (j, p) ->
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
                        CaretPosition.Double.Element.LEFT_BAR -> ModifySelectionAction(
                            j,
                            RectPoint.CENTER_LEFT
                        )

                        CaretPosition.Double.Element.RIGHT_BAR -> ModifySelectionAction(
                            j,
                            RectPoint.CENTER_RIGHT
                        )

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
                        CaretPosition.GridSelection.Element.CORNER_TL -> ModifySelectionAction(
                            j,
                            RectPoint.TOP_LEFT
                        )

                        CaretPosition.GridSelection.Element.CORNER_TR -> ModifySelectionAction(
                            j,
                            RectPoint.TOP_RIGHT
                        )

                        CaretPosition.GridSelection.Element.CORNER_BR -> ModifySelectionAction(
                            j,
                            RectPoint.BOTTOM_RIGHT
                        )

                        CaretPosition.GridSelection.Element.CORNER_BL -> ModifySelectionAction(
                            j,
                            RectPoint.BOTTOM_LEFT
                        )

                        CaretPosition.GridSelection.Element.INTERIOR -> DisplayContextMenuAction(
                            j
                        )

                        CaretPosition.GridSelection.Element.NONE -> null
                    }
                }
            }
        } ?: PlaceCaretAction()
    }

    fun moveToPreviousInput() {
        caret.positions = caret.positions.map { p ->
            when (p) {
                is CaretPosition.Double -> {
                    p.previousSingle ?: p
                }
                is CaretPosition.Single -> {
                    p.previousSingle ?: p
                }
                else -> p
            }
        }
    }

    fun moveToNextInput() {
        caret.positions = caret.positions.map { p ->
            when (p) {
                is CaretPosition.Double -> {
                    p.nextSingle ?: p
                }
                is CaretPosition.Single -> {
                    p.nextSingle ?: p
                }
                else -> p
            }
        }
    }

    companion object {
        val baselinePaint = Paints.stroke(1f, Color.BLACK)
        val defaultBackgroundColor = Color.DKGRAY
        val magnifierBorder = Paints.stroke(1f, Color.LTGRAY)

        const val DEFAULT_PADDING = FormulaBox.DEFAULT_TEXT_WIDTH
        // const val MIN_HEIGHT = (FormulaBox.DEFAULT_TEXT_SIZE + 2 * DEFAULT_PADDING).toInt()
        val defaultPadding = Padding(DEFAULT_PADDING)
        const val VELOCITY_REDUCTION_PERIOD = 8L
        const val VELOCITY_REDUCTION_MULT = VELOCITY_REDUCTION_PERIOD * 0.001f
        const val VELOCITY_REDUCTION = 25000f // en px.s^-2
        const val ZOOM_TOLERENCE = 0.1f
        const val DOUBLE_TAP_DELAY = 150
        const val CONTEXT_MENU_OFFSET = FormulaBox.DEFAULT_TEXT_RADIUS * 0.25f
    }
}

sealed class FormulaBoxesClipData {
    data class Input(
        val boxes: () -> List<FormulaBox>
    ) : FormulaBoxesClipData() {
        val boxesValue = boxes()
        override fun generateBoxes() = if (reduced) boxes() else boxesValue
    }
    data class Grid(
        val shape: Pt,
        val inputs: () -> List<List<FormulaBox>>
    ) : FormulaBoxesClipData() {
        val inputsValue = inputs()
        override fun generateBoxes() = listOf(
            MatrixFormulaBox(
                shape
            ).apply {
                grid.inputs.forEachIndexed { i, inp ->
                    inp.addBoxes((if (reduced) inputs() else inputsValue)[i])
                }
            }
        )
    }
    protected var reduced = false
    fun toBoxes() =
        generateBoxes().also {
            if (!reduced) reduced = true
        }
    protected abstract fun generateBoxes(): List<FormulaBox>
    companion object {
        fun formClips(items: List<String>, mode: Int) =
            (if (items.size > 1) try {
                val json = App.gson.fromJson(items[1], JsonElement::class.java)
                when {
                    json.isJsonArray -> {
                        Input(json.asJsonArray.let { { it.toBoxes() } })
                    }
                    json.isJsonObject -> {
                        val obj = json.asJsonObject
                        when (obj["tag"].asString) {
                            "grid" -> {
                                Grid(
                                    obj.getAsJsonArray("shape").toPt(),
                                    obj.getAsJsonArray("inputs").map { it.asJsonArray }.let { arr -> { arr.map { it.toBoxes() } } }
                                )
                            }
                            else -> null
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.d("exc", e.toString())
                null
            } else null) ?: if (items.isNotEmpty()) {
                Input(StringReader(items[0]).readGroupedTokenFlattened().let { { it.toBoxes(mode) } })
            } else null
    }
}