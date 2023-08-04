package fr.benichn.math3.graphics.types

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF

data class RectPoint(val tx: Float, val ty: Float) {
    init {
        assert((tx.isNaN() || tx in 0.0..1.0) && (ty.isNaN() || ty in 0.0..1.0))
    }

    fun get(r: RectF): PointF = PointF(
        if (tx.isNaN()) 0f else r.left + tx * (r.right - r.left),
        if (ty.isNaN()) 0f else r.top + ty * (r.bottom - r.top)
    )

    fun get(r: Rect): PointF = PointF(
        if (tx.isNaN()) 0f else r.left + tx * (r.right - r.left),
        if (ty.isNaN()) 0f else r.top + ty * (r.bottom - r.top)
    )

    companion object {
        val TOP_LEFT = RectPoint(0f, 0f)
        val TOP_RIGHT = RectPoint(1f, 0f)
        val BOTTOM_RIGHT = RectPoint(1f, 1f)
        val BOTTOM_LEFT = RectPoint(0f, 1f)
        val CENTER = RectPoint(0.5f, 0.5f)
        val TOP_CENTER = RectPoint(0.5f, 0f)
        val BOTTOM_CENTER = RectPoint(0.5f, 1f)
        val CENTER_LEFT = RectPoint(0f, 0.5f)
        val CENTER_RIGHT = RectPoint(1f, 0.5f)
        val TOP_NAN = RectPoint(Float.NaN, 0f)
        val CENTER_NAN = RectPoint(Float.NaN, 0.5f)
        val BOTTOM_NAN = RectPoint(Float.NaN, 1f)
        val NAN_LEFT = RectPoint(0f, Float.NaN)
        val NAN_CENTER = RectPoint(0.5f, Float.NaN)
        val NAN_RIGHT = RectPoint(1f, Float.NaN)
        val NAN = RectPoint(Float.NaN, Float.NaN)
    }
}