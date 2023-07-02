package fr.benichn.math3.graphics.boxes.types

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.unaryMinus
import fr.benichn.math3.graphics.types.RectPoint

// @JvmInline
// value class GraphicsTransform(private val f: (FormulaGraphics) -> FormulaGraphics) {
    // operator fun invoke(g: FormulaGraphics) = f(g)
    // operator fun times(gt: GraphicsTransform) = GraphicsTransform { g -> gt.f(f(g)) }
    // companion object {
        // fun id() = GraphicsTransform { g -> g }
        // fun transform(m: Matrix) = GraphicsTransform { g ->
            // FormulaGraphics(
                // Path(g.path).also { it.transform(m) },
                // g.painting,
                // RectF(g.bounds).also { m.mapRect(it) }
            // )
        // }
        // fun transform(tr: BoxTransform): GraphicsTransform {
            // val m = Matrix()
            // m.postScale(tr.scale, tr.scale)
            // m.postTranslate(tr.origin.x, tr.origin.y)
            // return transform(m)
        // }
        // fun align(rp: RectPoint) = GraphicsTransform { g ->
            // val tr = BoxTransform(-(rp.get(g.bounds)))
            // transform(tr)(g)
        // }
        // fun color(c: Int) = GraphicsTransform { g ->
            // FormulaGraphics(
                // g.path,
                // Paint(g.painting).also { it.color = c },
                // g.bounds
            // )
        // }
        // fun padding(left: Float, top: Float, right: Float, bottom: Float) = GraphicsTransform { g ->
            // FormulaGraphics(
                // Path(g.path).also { it.transform(Matrix().apply { setTranslate(left, top) }) },
                // g.painting,
                // g.bounds.let { RectF(it.left-left,it.top-top,it.right+right,it.bottom+bottom) }
            // )
        // }
        // fun padding(h: Float, v: Float) = padding(h, v, h, v)
        // fun padding(d: Float) = padding(d, d, d, d)
    // }
// }