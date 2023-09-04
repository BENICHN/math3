package fr.benichn.math3.numpad

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.util.SizeF
import fr.benichn.math3.graphics.Utils.times
import fr.benichn.math3.graphics.Utils.with
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.numpad.types.NumpadButtonElement

class NumpadButtonFormulaBox(val buttonElement: NumpadButtonElement, size: SizeF, child: FormulaBox = FormulaBox(), isPressed: Boolean = false, hasAux: Boolean = false) : TransformerFormulaBox(
    child,
    BoundsTransformer.Align(RectPoint.CENTER),
    BoundsTransformer.Constant(BoxTransform.scale(0.66f)),
    updGr = false
) {
    val dlgSize = BoxProperty(this, size).apply {
        onChanged += { _, _ -> updateTransformers() }
    }
    var size by dlgSize

    val dlgIsPressed = BoxProperty(this, isPressed)
    var isPressed by dlgIsPressed

    val dlgHasAux = BoxProperty(this, hasAux)
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
                    moveTo(-rx * 0.5f, -ry * 0.8f)
                    lineTo(rx * 0.5f, -ry * 0.8f)
                },
                Paints.stroke(2f, Color.rgb(254, 211, 48))
            ) else null,
            bounds = RectF(-rx, -ry, rx, ry),
            background = if (isPressed) pressedColor else Color.WHITE
        )
    }

    companion object {
        val pressedColor = Color.rgb(230, 230, 230)
    }
}