package fr.benichn.math3.graphics

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.App
import fr.benichn.math3.R
import fr.benichn.math3.graphics.types.*
import kotlin.math.max
import kotlin.math.min

class Utils {
    companion object {
        fun getTextPathAndSize(textSize: Float, text: String): MeasuredPath {
            val res = Path()
            val paint = Paint()
            paint.typeface = App.instance.resources.getFont(R.font.source_code_pro_light)
            paint.textSize = textSize
            paint.getTextPath(text, 0, text.length, 0f, 0f-(paint.fontMetrics.ascent+paint.fontMetrics.bottom)/2, res)
            return MeasuredPath(res, paint.measureText(text), textSize)
        }
        fun getPathBounds(path: Path): RectF {
            val res = RectF()
            path.computeBounds(res, false)
            return res
        }

        fun sumOfRects(vararg rects: RectF): RectF = sumOfRects(rects.asIterable())

        fun sumOfRects(rects: Iterable<RectF>): RectF = rects.fold(RectF()) { acc, r ->
            RectF(min(acc.left, r.left), min(acc.top, r.top), max(acc.right, r.right), max(acc.bottom, r.bottom))
        }

        operator fun PointF.times(scale: Float): PointF = PointF(x*scale,y*scale)
        operator fun PointF.div(scale: Float): PointF = PointF(x/scale,y/scale)
    }
}