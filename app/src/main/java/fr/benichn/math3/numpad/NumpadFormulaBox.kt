package fr.benichn.math3.numpad

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.SizeF
import androidx.core.animation.doOnEnd
import com.google.gson.JsonObject
import fr.benichn.math3.Utils.clamp
import fr.benichn.math3.Utils.opt
import fr.benichn.math3.graphics.Utils.prepend
import fr.benichn.math3.graphics.Utils.times
import fr.benichn.math3.graphics.Utils.with
import fr.benichn.math3.graphics.boxes.BracketFormulaBox
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.DiscreteOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.IntegralOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.RootFormulaBox
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
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.numpad.types.Pt
import fr.benichn.math3.types.callback.ValueChangedEvent
import kotlin.math.max

class NumpadFormulaBox(pages: List<NumpadPage> = listOf(), size: SizeF = SizeF(0f, 0f)) : FormulaBox() {
    constructor(pages: JsonObject, size: SizeF = SizeF(0f, 0f)) : this(NumpadPage.listFromJSON(pages), size)

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
            addBoxes(b)
        }
    }

    private fun resetPagesPositions() {
        val w = size.width
        val h = size.height
        for (i in ch.indices) {
            val p = pages[i]
            setChildTransform(ch[i], if (p.coords == currentPageCoords) BoxTransform() else BoxTransform(PointF(-w, -h)))
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
                    setChildTransform(ch[indices[j]], BoxTransform(PointF(xs[j], ys[j])))
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
                setChildTransform(ch[ni], BoxTransform.yOffset(h))
                currentAnimation = animateSwiping(0f, -h, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Left -> {
                setChildTransform(ch[ni], BoxTransform.xOffset(w))
                currentAnimation = animateSwiping(-w, 0f, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Down -> {
                setChildTransform(ch[ni], BoxTransform.yOffset(-h))
                currentAnimation = animateSwiping(0f, h, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Right -> {
                setChildTransform(ch[ni], BoxTransform.xOffset(-w))
                currentAnimation = animateSwiping(w, 0f, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
        }
    }

    private fun indexOfPage(pt: Pt) = pages.indexOfFirst { p -> p.coords == pt }

    companion object {
        const val SWIPE_DURATION = 250L
    }
}

class NumpadButtonFormulaBox(val buttonElement: NumpadButtonElement, size: SizeF, child: FormulaBox = FormulaBox(), isPressed: Boolean = false, hasAux: Boolean = false) : TransformerFormulaBox(
    child,
    BoundsTransformer.Align(RectPoint.CENTER),
    BoundsTransformer.Constant(BoxTransform.scale(0.66f)),
    updGr = false
) {
    private val dlgSize = BoxProperty(this, size).apply {
        onChanged += { _, _ -> updateTransformers() }
    }
    var size by dlgSize

    private val dlgIsPressed = BoxProperty(this, isPressed)
    var isPressed by dlgIsPressed

    private val dlgHasAux = BoxProperty(this, hasAux)
    var hasAux by dlgHasAux

    init {
        updateTransformers()
        updateGraphics()
    }

    private fun updateTransformers() {
        modifyTransformers { it.with(2, BoundsTransformer.ClampSize(size * 0.6f)) }
    }

    override fun generateGraphics() = size.run {
        val rx = width * 0.5f
        val ry = height * 0.5f
        FormulaGraphics(
            if (hasAux) PaintedPath(
                Path().apply {
                    moveTo(-rx*0.5f, -ry*0.8f)
                    lineTo(rx*0.5f, -ry*0.8f)
                },
                Paints.stroke(2f, Color.rgb(254, 211, 48))
            ) else null,
            bounds = RectF(-rx, -ry, rx, ry),
            background = if (isPressed) pressedColor else Color.WHITE)
    }

    companion object {
        val pressedColor = Color.rgb(230, 230, 230)
    }
}

data class NumpadButtonGroup(
    val rect: Rect,
    val normal: NumpadButton,
    val shift: NumpadButton?
)

data class NumpadButton(
    val main: NumpadButtonElement,
    val aux: List<NumpadButtonElement>,
    val hideOther: Boolean
) {
    val hasAux
        get() = aux.isNotEmpty()
}

data class NumpadButtonElement(val rect: Rect, val id: String)

data class NumpadPage(val width: Int, val height: Int, val coords: Pt, val buttons: List<NumpadButtonGroup>) {
    fun getButton(pt: Pt) = buttons.first { it.rect.contains(pt.x, pt.y) }

    companion object {
        private fun getRect(s: String): Rect {
            val btnRanges = s.split(",").map { getRange(it) }
            return Rect(btnRanges[0].start, btnRanges[1].start, btnRanges[0].end, btnRanges[1].end)
        }

        private fun getRange(s: String): Range {
            val ends = s.split(":", limit = 2)
            return if (ends.size == 1) ends[0].toInt().let { i -> Range(i,i+1) }
            else Range(ends[0].toInt(), ends[1].toInt())
        }

        fun listFromJSON(pages: JsonObject) = pages.keySet().map { pk ->
            val coords = pk.split(",").map { it.toInt() }
            val pt = Pt(coords[0], coords[1])
            val page = pages[pk].asJsonObject
            val pw = page["w"].asInt
            val ph = page["h"].asInt
            fun readButton(btnRect: Rect, obj: JsonObject): NumpadButton {
                val id = obj["id"].asString
                val main = NumpadButtonElement(btnRect, id)
                val aux = obj.opt("aux")?.asJsonArray?.let { arr ->
                    val auxIds = arr.map { it.asString }
                    val auxPos = obj.opt("auxPos")?.asJsonArray?.let { apArr ->
                        (0 until apArr.size()).map { i -> getRect(apArr[i].asString) }
                    } ?: getAuxPositions(pw, ph, btnRect, auxIds.size)
                    auxPos.zip(auxIds) { rect, id -> NumpadButtonElement(rect, id) }
                }
                val hideOther = aux == null || (obj.opt("hideOther")?.asBoolean ?: true)
                return NumpadButton(main, aux ?: listOf(), hideOther)
            }
            val btns = page["buttons"].asJsonObject
            val buttons = btns.keySet().map { k ->
                val btnRect = getRect(k)
                btns[k].asJsonObject.let { obj ->
                    val normal = readButton(btnRect, obj["normal"].asJsonObject)
                    val shift = obj.opt("shift")?.asJsonObject?.let { o -> readButton(btnRect, o) }
                    NumpadButtonGroup(btnRect, normal, shift)
                }
            }.toList()
            NumpadPage(pw, ph, pt, buttons)
        }.toList()

        private fun getAuxPositions(w: Int, h: Int, rect: Rect, n: Int, side: Side = Side.L): List<Rect> {
            val cx = rect.centerX()
            var rxm = cx - rect.left
            var rxp = rect.right - 1 - cx
            if (side == Side.R) {
                rxm.let {
                    rxm = rxp
                    rxp = it
                }
            }
            val cy = rect.centerY()
            val rym = cy - rect.top
            val ryp = rect.bottom - 1 - cy
            val ux = if (side == Side.L) Pt.r else Pt.l
            val uy = Pt.t
            val o = Pt(cx, cy)
            fun makeLoop(r: Int): List<Pt> {
                val ot = o + uy * (r + rym)
                val t = (0 .. r + rxp).map { i -> ot + ux * i }
                val ol = t.last()
                val l = (1 .. 2*r + ryp).map { i -> ol - uy * i }
                val ob = l.last()
                val b = (1 .. r + rxp).map { i -> ob - ux * i }
                val rt = (1 .. r + rxm).map { i -> ot - ux * i }
                val or = rt.last()
                val rr = (1 .. 2*r + ryp).map { i -> or - uy * i }
                val orr = rr.last()
                val rb = (1 until r + rym).map { i-> orr + ux * i }
                return listOf(t, l, b, rt, rr, rb).flatten()
            }
            val auxPos = (1 until max(w, h))
                .flatMap { r -> makeLoop(r) }
                .filter { p -> p.x in 0 until w && p.y in 0 until h }
            return auxPos.map { pt -> Rect(pt.x, pt.y, pt.x+1, pt.y+1) }.prepend(rect).take(n)
        }
    }
}

class NumpadPageFormulaBox(page: NumpadPage, size: SizeF, buttonPressed: Pt? = null, buttonExpanded: NumpadButton? = null, isShift: Boolean = false) : FormulaBox() {
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
            updateButtonsSizes()
            alignChildren()
        }
    }
    var size by dlgSize
    private lateinit var buttonSize: SizeF

    val dlgButtonPressed = BoxProperty(this, buttonPressed).apply {
        onChanged += { _, _ -> updatePressed() }
    }
    var buttonPressed by dlgButtonPressed

    val dlgIsShift = BoxProperty(this, isShift).apply {
        onChanged += { _, _ -> resetChildren() }
    }
    var isShift by dlgIsShift

    var isShiftLocked = false

    val dlgButtonExpanded = BoxProperty(this, buttonExpanded).apply {
        onChanged += { _, e -> resetChildren() }
    }
    var buttonExpanded by dlgButtonExpanded

    val realButtons
        get() =
            if (isShift) page.buttons.mapNotNull { it.shift }
            else page.buttons.map { it.normal }

    val mainButtonElements
        get() = realButtons.map { it.main }

    val realButtonElements
        get() = buttonExpanded?.run {
            if (hideOther) aux
            else {
                aux + mainButtonElements.filter { be ->
                    !aux.any { be.rect.intersect(it.rect) }
                }
            }
        } ?: mainButtonElements

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
    // fun posFromCoords(pt: Pt): PointF {
    //     val x = pt.x * buttonSize.width
    //     val y = pt.y * buttonSize.height
    //     return PointF(x, y)
    // }
    // fun rectFromCoords(pt: Pt): RectF {
    //     val pos = posFromCoords(pt)
    //     return RectF(pos.x, pos.y, pos.x + buttonSize.width, pos.y + buttonSize.height)
    // }

    private fun updateButtonSize() {
        buttonSize = SizeF(
            size.width / page.width,
            size.height / page.height
        )
    }

    private fun updateButtonsSizes() {
        ch.forEach { c -> (c as NumpadButtonFormulaBox).size = getButtonSize(c.buttonElement.rect) }
    }

    fun findButton(pos: PointF) =
        page.getButton(findCoords(pos))

    fun findId(pos: PointF) =
        (findBox(pos) as? NumpadButtonFormulaBox)?.buttonElement?.id

    fun findCoords(pos: PointF): Pt {
        val x = (pos.x / buttonSize.width).toInt()
        val y = (pos.y / buttonSize.height).toInt()
        return Pt(
            clamp(x, 0, page.width-1),
            clamp(y, 0, page.height-1)
        )
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) = false

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
    }

    private fun updatePressed() {
        ch.forEach { c -> (c as NumpadButtonFormulaBox).isPressed = buttonPressed?.let { pt -> c.buttonElement.rect.contains(pt.x, pt.y) } ?: false }
    }

    private fun resetChildren() {
        removeAllBoxes()
        addChildren()
        alignChildren()
    }

    private fun getButtonSize(rect: Rect) = SizeF(buttonSize.width * rect.width(), buttonSize.height * rect.height())

    private fun addChildren() {
        val bs = realButtonElements.map { be -> NumpadButtonFormulaBox(
            be,
            getButtonSize(be.rect),
            getIconFromId(be.id),
            hasAux = buttonExpanded == null && realButtons.any { btn -> btn.hasAux && btn.main.rect == be.rect })
        }
        addBoxes(bs)
        updatePressed()
    }

    private fun alignChildren() {
        ch.forEach { c ->
            val r = (c as NumpadButtonFormulaBox).buttonElement.rect
            setChildTransform(c, BoxTransform(PointF(
                r.left * buttonSize.width + c.size.width * 0.5f,
                r.top * buttonSize.height + c.size.height * 0.5f,
            )))
        }
        updateGraphics()
    }

    private fun isHSegmentSupported(x: Int, y: Int) =
        realButtonElements.any { be ->
            val r = be.rect
            (r.top == y || r.bottom == y) && r.left <= x && x+1 <= r.right
        }

    private fun isVSegmentSupported(x: Int, y: Int) =
        realButtonElements.any { be ->
            val r = be.rect
            (r.left == x || r.right == x) && r.top <= y && y+1 <= r.bottom
        }

    override fun generateGraphics(): FormulaGraphics {
        return FormulaGraphics(
            PaintedPath( // grille
                Path().apply {
                    val w = size.width
                    val h = size.height
                    val (pw, ph, _, _) = page
                    val bh = buttonSize.height
                    val bw = buttonSize.width
                    for (j in listOf(0, ph)) {
                        val y = j * bh
                        moveTo(0f, y)
                        rLineTo(w, 0f)
                    }
                    for (i in listOf(0, pw)) {
                        val x = i * bw
                        moveTo(x, 0f)
                        rLineTo(0f, h)
                    }
                    for (j in 1 until ph) {
                        val y = j * bh
                        var i0 = 0
                        for (i in 0 .. pw) {
                            if (i == pw || !isHSegmentSupported(i, j)) {
                                if (i0 != i) {
                                    moveTo(i0 * bw, y)
                                    lineTo(i * bw, y)
                                }
                                i0 = i+1
                            }
                        }
                    }
                    for (i in 1 until pw) {
                        val x = i * bw
                        var j0 = 0
                        for (j in 0 .. ph) {
                            if (j == ph || !isVSegmentSupported(i, j)) {
                                if (j0 != j) {
                                    moveTo(x, j0 * bh)
                                    lineTo(x, j * bh)
                                }
                                j0 = j+1
                            }
                        }
                    }
                },
                gridPaint,
                aboveChildren = true
            ),
            bounds = RectF(0f, 0f, size.width, size.height),
            background = Color.WHITE
        )
    }

    companion object {
        const val GRID_LINE_WIDTH = 0.25f
        val gridPaint = Paints.stroke(GRID_LINE_WIDTH, Color.BLACK)
        fun getIconFromId(id: String): FormulaBox = when (id) {
            "over" -> FractionFormulaBox()
            "sqrt" -> RootFormulaBox(RootFormulaBox.Type.SQRT)
            "sqrt_n" -> RootFormulaBox(RootFormulaBox.Type.ORDER)
            "brace" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACE)
            "bracket" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACKET)
            "chevron" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CHEVRON)
            "curly" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CURLY)
            "floor" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.FLOOR)
            "ceil" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CEIL)
            "abs" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BAR)
            "superscript_base" -> SequenceFormulaBox(InputFormulaBox(), ScriptFormulaBox(TopDownFormulaBox.Type.TOP))
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