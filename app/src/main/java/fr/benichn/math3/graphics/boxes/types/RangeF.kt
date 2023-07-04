package fr.benichn.math3.graphics.boxes.types

import android.graphics.RectF

data class RangeF(val start: Float = 0f, val end: Float = 0f) {
    companion object {
        fun fromRectH(r: RectF) = RangeF(r.left, r.right)
        fun fromRectV(r: RectF) = RangeF(r.top, r.bottom)
    }
}