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
import fr.benichn.math3.graphics.boxes.types.PathPainting
import fr.benichn.math3.graphics.types.RectPoint
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
        Path(),
        PathPainting.Fill,
        RectF(0f, 0f, size.width, size.height),
        Color.TRANSPARENT,
        Color.WHITE
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

data class NumpadButtonInfo(val id: String, val auxIds: List<String>)

data class NumpadPageInfo(val width: Int, val height: Int, val coords: Pt, val buttons: Map<Pt, NumpadButtonInfo>) {
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
                val bi = btns.getJSONArray(k).let { arr ->
                    val id = arr.getString(0)
                    NumpadButtonInfo(id, (1 until arr.length()).map { i -> arr.getString(i) })
                }
                btnPt to bi
            }.toMap()
            NumpadPageInfo(pw, ph, pt, buttons)
        }.toList()
    }
}

class NumpadPageFormulaBox(page: NumpadPageInfo, size: SizeF, buttonPressed: Pt? = null) : FormulaBox() {
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

    init {
        updateButtonSize()
        addChildren()
        alignChildren()
    }

    fun coordsOf(c: FormulaBox): Pt {
        val i = ch.indexOf(c)
        return Pt(i % page.width, i / page.width)
    }
    fun getButtonId(c: FormulaBox) = coordsOf(c).let { pt -> page.buttons.getValue(pt).id }

    private fun updateButtonSize() {
        buttonSize = SizeF(
            size.width / page.width,
            size.height / page.height
        )
    }

    override fun findChildBox(pos: PointF): FormulaBox {
        val x = (pos.x / buttonSize.width).toInt()
        val y = (pos.y / buttonSize.height).toInt()
        val pt = Pt(
            clamp(x, 0, page.width-1),
            clamp(y, 0, page.height-1)
        )
        return ch[pt.y * page.width + pt.x]
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) = false

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
    }

    private fun addChildren() {
        val (pw, ph, _, buttons) = page
        for (j in 0 until ph) {
            for (i in 0 until pw) {
                val id = buttons.getValue(Pt(i, j)).id
                val b = getIconFromId(id)
                addBox(TransformerFormulaBox(b))
            }
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
        val (pw, ph, _, _) = page
        val rx = buttonSize.width * 0.5f
        val ry = buttonSize.height * 0.5f
        for (j in 0 until ph) {
            for (i in 0 until pw) {
                setChildTransform(j * pw + i, BoxTransform(PointF(
                    i * buttonSize.width + rx,
                    j * buttonSize.height + ry,
                )))
            }
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
            path,
            PathPainting.Stroke(0.25f),
            buttonPressed?.let { (i, j) ->
                val x = i * buttonSize.width
                val y = j * buttonSize.height
                RectF(x, y, x+buttonSize.width, y+buttonSize.height)
            } ?: RectF(),
            Color.BLACK,
            Color.rgb(230, 230, 230)
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