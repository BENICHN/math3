package fr.benichn.math3.graphics.boxes.types

import android.graphics.RectF
import fr.benichn.math3.Utils.pos
import kotlin.math.max

data class RangeF(val start: Float = 0f, val end: Float = 0f) {
    companion object {
        fun fromRectH(r: RectF) = RangeF(r.left, r.right)
        fun fromRectV(r: RectF) = RangeF(r.top, r.bottom)
    }

    val center
        get() = (start + end)*0.5f

    val length
        get() = pos(end - start)
}