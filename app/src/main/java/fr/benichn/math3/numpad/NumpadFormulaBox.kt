package fr.benichn.math3.numpad

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.SizeF
import androidx.core.animation.doOnEnd
import fr.benichn.math3.Utils.Companion.clamp
import fr.benichn.math3.graphics.Utils.Companion.times
import fr.benichn.math3.graphics.boxes.BracketFormulaBox
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.DiscreteOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.IntegralOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.ScriptFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TopDownFormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.numpad.types.Pt
import fr.benichn.math3.types.callback.ValueChangedEvent
import org.json.JSONObject

class NumpadFormulaBox(pages: List<NumpadPageInfo> = listOf(), size: SizeF = SizeF(0f, 0f)) : FormulaBox() {
    constructor(pages: JSONObject, size: SizeF = SizeF(0f, 0f)) : this(
        NumpadPageInfo.listFromJSON(
            pages
        ), size)

    val dlgPages = BoxProperty(this, pages).apply {
        onChanged += { _, e ->
            removeAllBoxes()
            addPages()
        }
    }
    var pages by dlgPages

    val dlgSize = BoxProperty(this, size).apply {
        onChanged += { _, _ ->
            resetPagesPositions()
        }
    }
    var size by dlgSize

    private var currentPageCoords: Pt = Pt.z
    val currentPage
        get() = pages[indexOfPage(currentPageCoords)]
    val currentPageBox
        get() = ch[indexOfPage(currentPageCoords)] as NumpadPageFormulaBox
    private var currentAnimation: ValueAnimator? = null

    init {
        addPages()
        resetPagesPositions()
    }

    override fun generateGraphics() = FormulaGraphics(
        PaintedPath(),
        bounds = RectF(0f, 0f, size.width, size.height),
    )

    private fun addPages() {
        for (p in pages) {
            val b = NumpadPageFormulaBox(p, size)
            b.dlgSize.connectTo(dlgSize)
            addBox(b)
        }
    }

    private fun resetPagesPositions() {
        val w = size.width
        val h = size.height
        for (i in ch.indices) {
            val p = pages[i]
            setChildTransform(i, if (p.coords == currentPageCoords) BoxTransform() else BoxTransform(PointF(-w, -h)))
        }
    }

    private fun nextPos(d: Direction) : Pt {
        val u = when (d) {
            Direction.Up -> Pt(0, -1)
            Direction.Left -> Pt(1, 0)
            Direction.Down -> Pt(0, 1)
            Direction.Right -> Pt(-1, 0)
        }
        val newPos = currentPageCoords + u // on regarde la nouvelle position théorique
        var newCandidates = pages.filter { p -> // on regarde les positions ayant la même coordonnée que la nouvelle position selon la direction de u
            (p.coords-newPos).and(u) == Pt.z
        }
        if (newCandidates.isEmpty()) { // si abscence, on prend les positions minimisant cette coordonnée
            val mini = pages.minOf {p ->
                (p.coords * u).sum
            } * u.sum
            newCandidates = pages.filter { p ->
                p.coords.and(u).sum == mini
            }
        }
        return newCandidates.minBy { p ->
            (p.coords-newPos).l1
        }.coords
    }

    override fun findChildBox(pos: PointF) = currentPageBox

    private fun animateSwiping(deltaX: Float, deltaY: Float, duration: Long, vararg indices: Int, onEnd: () -> Unit = {}) =
        ValueAnimator.ofFloat(0f, 1f).also { va ->
            val x0s = indices.map { i -> ch[i].transform.origin.x }
            val y0s = indices.map { i -> ch[i].transform.origin.y }
            va.addUpdateListener {
                val t = Utils.easeOutExpo(it.animatedValue as Float)
                val dx = (deltaX * t).toInt()
                val dy = (deltaY * t).toInt()
                val xs = x0s.map { x0 -> x0 + dx }
                val ys = y0s.map { y0 -> y0 + dy }
                for (j in indices.indices) {
                    setChildTransform(indices[j], BoxTransform(PointF(xs[j], ys[j])))
                }
            }
            va.doOnEnd {
                onEnd()
            }
            va.duration = duration
            va.start()
        }

    fun swipe(d: Direction) = swipeTo(nextPos(d), d)

    private fun swipeTo(pos: Pt, d: Direction) {
        currentAnimation?.end()
        val w = size.width
        val h = size.height
        val oi = indexOfPage(currentPageCoords)
        val ni = indexOfPage(pos)
        if (oi == ni) return
        currentPageCoords = pos
        when (d) {
            Direction.Up -> {
                setChildTransform(ni, BoxTransform.yOffset(h))
                currentAnimation = animateSwiping(0f, -h, SWIPE_DURATION, oi, ni) {
                    setChildTransform(oi, BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Left -> {
                setChildTransform(ni, BoxTransform.xOffset(w))
                currentAnimation = animateSwiping(-w, 0f, SWIPE_DURATION, oi, ni) {
                    setChildTransform(oi, BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Down -> {
                setChildTransform(ni, BoxTransform.yOffset(-h))
                currentAnimation = animateSwiping(0f, h, SWIPE_DURATION, oi, ni) {
                    setChildTransform(oi, BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Right -> {
                setChildTransform(ni, BoxTransform.xOffset(-w))
                currentAnimation = animateSwiping(w, 0f, SWIPE_DURATION, oi, ni) {
                    setChildTransform(oi, BoxTransform(PointF(-w, -h)))
                }
            }
        }
    }

    private fun indexOfPage(pt: Pt) = pages.indexOfFirst { p -> p.coords == pt }

    companion object {
        const val SWIPE_DURATION = 250L
    }
}

data class NumpadButtonInfo(val pt: Pt, val id: String, val aux: List<NumpadButtonInfo>) {
    val hasAux
        get() = aux.isNotEmpty()
}

data class NumpadPageInfo(val width: Int, val height: Int, val coords: Pt, val buttons: List<NumpadButtonInfo>) {
    fun getButton(pt: Pt) = buttons.first { it.pt == pt }

    companion object {
        fun listFromJSON(pages: JSONObject) = pages.keys().asSequence().map { k ->
            val coords = k.split(",").map { it.toInt() }
            val pt = Pt(coords[0], coords[1])
            val page = pages.getJSONObject(k)
            val pw = page.getInt("w")
            val ph = page.getInt("h")
            val btns = page.getJSONObject("buttons")
            val buttons = btns.keys().asSequence().map { k ->
                val btnCoords = k.split(",").map { it.toInt() }
                val btnPt = Pt(btnCoords[0], btnCoords[1])
                btns.getJSONArray(k).let { arr ->
                    val id = arr.getString(0)
                    val auxIds = (1 until arr.length()).map { i -> arr.getString(i) }
                    val auxPos = getAuxPositions(pw, ph, btnPt, auxIds.size)
                    val aux = auxIds.zip(auxPos) { id, pt -> NumpadButtonInfo(pt, id, listOf()) }
                    NumpadButtonInfo(btnPt, id, aux)
                }
            }.toList()
            NumpadPageInfo(pw, ph, pt, buttons)
        }.toList()

        fun getAuxPositions(w: Int, h: Int, pos: Pt, n: Int, side: Side = Side.L): List<Pt> {
            val auxOffsets = when (side) {
                Side.L -> {
                    listOf(
                        Pt(0, 0),
                        Pt(0, 1),
                        Pt(1, 1),
                        Pt(1, 0),
                        Pt(1, -1),
                        Pt(0, -1),
                        Pt(-1, 1),
                        Pt(-1, 0),
                        Pt(-1, -1)
                    )
                }
                Side.R -> {
                    listOf(
                        Pt(0, 0),
                        Pt(0, 1),
                        Pt(-1, 1),
                        Pt(-1, 0),
                        Pt(-1, -1),
                        Pt(0, -1),
                        Pt(1, 1),
                        Pt(1, 0),
                        Pt(1, -1)
                    )
                }
            }
            val auxPos = auxOffsets.map { u -> pos + u }.filter { p ->
                p.x in 0 .. w &&
                        p.y in 0 .. h
            }
            return auxPos.take(n)
        }
    }
}

class NumpadPageFormulaBox(page: NumpadPageInfo, size: SizeF, buttonPressed: Pt? = null, buttonExpanded: Pt? = null) : FormulaBox() {
    val dlgPage = BoxProperty(this, page).apply {
        onChanged += { _, _ ->
            removeAllBoxes()
            updateButtonSize()
            addChildren()
            alignChildren()
        }
    }
    var page by dlgPage

    val dlgSize = BoxProperty(this, size).apply {
        onChanged += { _, _ ->
            updateButtonSize()
            updateTransformers()
            alignChildren()
        }
    }
    var size by dlgSize
    private lateinit var buttonSize: SizeF

    val dlgButtonPressed = BoxProperty(this, buttonPressed)
    var buttonPressed by dlgButtonPressed

    val dlgButtonExpanded = BoxProperty(this, buttonExpanded).apply {
        onChanged += { _, e ->
            removeAllBoxes()
            addChildren()
            alignChildren()
        }
    }
    var buttonExpanded by dlgButtonExpanded

    val realButtons
        get() = buttonExpanded?.let { pt -> page.getButton(pt).aux } ?: page.buttons

    init {
        updateButtonSize()
        addChildren()
        alignChildren()
    }

    // fun coordsOf(c: FormulaBox): Pt {
    //     val i = ch.indexOf(c)
    //     return Pt(i % page.width, i / page.width)
    // }
    // fun getButtonId(c: FormulaBox) = coordsOf(c).let { pt -> page.getButton(pt).id }
    fun posFromCoords(pt: Pt): PointF {
        val x = pt.x * buttonSize.width
        val y = pt.y * buttonSize.height
        return PointF(x, y)
    }
    fun rectFromCoords(pt: Pt): RectF {
        val pos = posFromCoords(pt)
        return RectF(pos.x, pos.y, pos.x + buttonSize.width, pos.y + buttonSize.height)
    }

    private fun updateButtonSize() {
        buttonSize = SizeF(
            size.width / page.width,
            size.height / page.height
        )
    }

    fun findAuxButton(pos: PointF) = buttonExpanded?.let { _ ->
        val pt = findCoords(pos)
        realButtons.firstOrNull { it.pt == pt }
    }

    fun findButton(pos: PointF) =
        page.getButton(findCoords(pos))

    fun findCoords(pos: PointF): Pt {
        val x = (pos.x / buttonSize.width).toInt()
        val y = (pos.y / buttonSize.height).toInt()
        return Pt(
            clamp(x, 0, page.width-1),
            clamp(y, 0, page.height-1)
        )
    }

    override fun findChildBox(pos: PointF): FormulaBox {
        val pt = findCoords(pos)
        val i = realButtons.indexOfFirst { btn -> btn.pt == pt }
        return if (i == -1) this else ch[i]
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) = false

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
    }

    private fun addButton(btn: NumpadButtonInfo) {
        val b = getIconFromId(btn.id)
        addBox(TransformerFormulaBox(b))
    }

    private fun addChildren() {
        for (btn in realButtons) {
            addButton(btn)
        }
        updateTransformers()
    }

    private fun updateTransformers() {
        for (c in ch) {
            (c as TransformerFormulaBox).transformer =
                BoundsTransformer.Align(RectPoint.CENTER) *
                BoundsTransformer.Constant(BoxTransform.scale(0.66f)) *
                BoundsTransformer.ClampSize(buttonSize * 0.6f)
        }
    }

    private fun alignChildren() {
        val rx = buttonSize.width * 0.5f
        val ry = buttonSize.height * 0.5f
        realButtons.forEachIndexed { i, btn ->
            setChildTransform(i, BoxTransform(PointF(
                btn.pt.x * buttonSize.width + rx,
                btn.pt.y * buttonSize.height + ry,
            )))
        }
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val path = Path()
        val w = size.width
        val h = size.height
        val (pw, ph, _, _) = page
        for (j in 0..ph) {
            val y = j * buttonSize.height
            path.moveTo(0f, y)
            path.rLineTo(w, 0f)
        }
        for (i in 0..pw) {
            val x = i * buttonSize.width
            path.moveTo(x, 0f)
            path.rLineTo(0f, h)
        }
        return FormulaGraphics(
            buttonPressed?.let { pt -> // btnPressed
                PaintedPath(
                    Path().apply {
                        addRect(rectFromCoords(pt), Path.Direction.CCW)
                    },
                    Paints.fill(Color.rgb(230, 230, 230))
                )
            },
            buttonExpanded?.let { _ -> // aux
                val auxPts = realButtons.map { it.pt }
                PaintedPath(
                    Path().apply {
                        for (i in 0 .. page.width) {
                            for (j in 0 .. page.height) {
                                val pt = Pt(i, j)
                                if (pt !in auxPts) {
                                    addRect(rectFromCoords(pt), Path.Direction.CCW)
                                }
                            }
                        }
                    },
                    Paints.fill(Color.WHITE)
                )
            },
            PaintedPath( // grille
                path,
                Paints.stroke(0.25f, Color.BLACK)
            ),
            bounds = RectF(0f, 0f, size.width, size.height),
            background = Color.WHITE
        )
    }

    companion object {
        fun getIconFromId(id: String): FormulaBox = when (id) {
            "over" -> FractionFormulaBox()
            "brace" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACE)
            "bracket" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACKET)
            "chevron" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CHEVRON)
            "curly" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CURLY)
            "floor" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.FLOOR)
            "ceil" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CEIL)
            "abs" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BAR)
            "superscript" -> SequenceFormulaBox(InputFormulaBox(), ScriptFormulaBox(TopDownFormulaBox.Type.TOP))
            "subscript" -> SequenceFormulaBox(InputFormulaBox(), ScriptFormulaBox(TopDownFormulaBox.Type.BOTTOM))
            "int_indef" -> IntegralOperatorFormulaBox(TopDownFormulaBox.Type.NONE)
            "int_def" -> IntegralOperatorFormulaBox(TopDownFormulaBox.Type.BOTH)
            "deriv" -> DerivativeOperatorFormulaBox(TopDownFormulaBox.Type.BOTTOM)
            "deriv_n" -> DerivativeOperatorFormulaBox(TopDownFormulaBox.Type.BOTH)
            "sum_indef" -> DiscreteOperatorFormulaBox("∑", DiscreteOperatorFormulaBox.Type.INDEFINITE)
            "sum_bounds" -> DiscreteOperatorFormulaBox("∑", DiscreteOperatorFormulaBox.Type.BOUNDS)
            "sum_list" -> DiscreteOperatorFormulaBox("∑", DiscreteOperatorFormulaBox.Type.LIST)
            "prod_indef" -> DiscreteOperatorFormulaBox("∏", DiscreteOperatorFormulaBox.Type.INDEFINITE)
            "prod_bounds" -> DiscreteOperatorFormulaBox("∏", DiscreteOperatorFormulaBox.Type.BOUNDS)
            "prod_list" -> DiscreteOperatorFormulaBox("∏", DiscreteOperatorFormulaBox.Type.LIST)
            "matrix" -> MatrixFormulaBox(Pt(2, 2))
            else -> TextFormulaBox(id)
        }.apply {
            setForegroundRecursive { when(it) {
                is InputFormulaBox -> Color.GRAY
                else -> Color.BLACK
            } }
        }
    }
}