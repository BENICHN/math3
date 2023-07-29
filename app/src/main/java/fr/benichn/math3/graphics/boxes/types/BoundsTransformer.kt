package fr.benichn.math3.graphics.boxes.types

import android.graphics.RectF
import android.util.SizeF
import androidx.core.graphics.unaryMinus
import fr.benichn.math3.graphics.types.RectPoint
import kotlin.math.max

abstract class BoundsTransformer {
    data class Constant(val value: BoxTransform) : BoundsTransformer() {
        override fun invoke(bounds: RectF) = value
    }
    data class ClampSize(val size: SizeF) : BoundsTransformer() {
        override fun invoke(bounds: RectF): BoxTransform {
            val wr = bounds.width() / size.width
            val hr = bounds.height() / size.height
            val r = maxOf(wr, hr, 1f)
            return BoxTransform.scale(1/r)
        }

    }
    data class Align(val rp: RectPoint) : BoundsTransformer() {
        override fun invoke(bounds: RectF): BoxTransform {
            val p = rp.get(bounds)
            return BoxTransform(-p)
        }

    }
    abstract operator fun invoke(bounds: RectF): BoxTransform
    operator fun times(btr: BoundsTransformer) = object : BoundsTransformer() {
        override fun invoke(bounds: RectF): BoxTransform {
            val tr = this@BoundsTransformer(bounds)
            val newBounds = tr.applyOnRect(bounds)
            return tr * btr(newBounds)
        }
    }
    companion object {
        val id = Constant(BoxTransform())
    }
}