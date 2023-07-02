package fr.benichn.math3.graphics.boxes.types

import android.graphics.RectF
import androidx.core.graphics.unaryMinus
import fr.benichn.math3.graphics.types.RectPoint

abstract class BoundsTransformer {
    data object Id : BoundsTransformer() {
        override fun invoke(bounds: RectF) = BoxTransform()
    }
    data class Constant(val bt: BoxTransform) : BoundsTransformer() {
        override fun invoke(bounds: RectF) = bt
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
}