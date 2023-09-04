package fr.benichn.math3.numpad

import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.SizeF
import fr.benichn.math3.Utils
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
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.numpad.types.NumpadButton
import fr.benichn.math3.numpad.types.NumpadPage
import fr.benichn.math3.numpad.types.Pt
import fr.benichn.math3.types.callback.ValueChangedEvent

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
            Utils.clamp(x, 0, page.width - 1),
            Utils.clamp(y, 0, page.height - 1)
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

    private fun getButtonSize(rect: Rect) =
        SizeF(buttonSize.width * rect.width(), buttonSize.height * rect.height())

    private fun addChildren() {
        val bs = realButtonElements.map { be ->
            NumpadButtonFormulaBox(
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
            setChildTransform(c, BoxTransform(
                PointF(
                    r.left * buttonSize.width + c.size.width * 0.5f,
                    r.top * buttonSize.height + c.size.height * 0.5f,
                )
            )
            )
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
                        for (i in 0..pw) {
                            if (i == pw || !isHSegmentSupported(i, j)) {
                                if (i0 != i) {
                                    moveTo(i0 * bw, y)
                                    lineTo(i * bw, y)
                                }
                                i0 = i + 1
                            }
                        }
                    }
                    for (i in 1 until pw) {
                        val x = i * bw
                        var j0 = 0
                        for (j in 0..ph) {
                            if (j == ph || !isVSegmentSupported(i, j)) {
                                if (j0 != j) {
                                    moveTo(x, j0 * bh)
                                    lineTo(x, j * bh)
                                }
                                j0 = j + 1
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
            "superscript_base" -> SequenceFormulaBox(
                InputFormulaBox(),
                ScriptFormulaBox(TopDownFormulaBox.Type.TOP)
            )
            "superscript" -> SequenceFormulaBox(
                InputFormulaBox(),
                ScriptFormulaBox(TopDownFormulaBox.Type.TOP)
            )
            "subscript" -> SequenceFormulaBox(
                InputFormulaBox(),
                ScriptFormulaBox(TopDownFormulaBox.Type.BOTTOM)
            )
            "int_indef" -> IntegralOperatorFormulaBox(TopDownFormulaBox.Type.NONE)
            "int_def" -> IntegralOperatorFormulaBox(TopDownFormulaBox.Type.BOTH)
            "deriv" -> DerivativeOperatorFormulaBox(TopDownFormulaBox.Type.BOTTOM)
            "deriv_n" -> DerivativeOperatorFormulaBox(TopDownFormulaBox.Type.BOTH)
            "sum_indef" -> DiscreteOperatorFormulaBox(
                "∑",
                DiscreteOperatorFormulaBox.Type.INDEFINITE
            )
            "sum_bounds" -> DiscreteOperatorFormulaBox("∑", DiscreteOperatorFormulaBox.Type.BOUNDS)
            "sum_list" -> DiscreteOperatorFormulaBox("∑", DiscreteOperatorFormulaBox.Type.LIST)
            "prod_indef" -> DiscreteOperatorFormulaBox(
                "∏",
                DiscreteOperatorFormulaBox.Type.INDEFINITE
            )
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