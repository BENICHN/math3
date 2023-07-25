package fr.benichn.math3.graphics.boxes.types

import android.graphics.RectF

data class Padding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    constructor(h: Float, v: Float) : this(h, v, h, v)
    constructor(d: Float) : this(d, d, d, d)
    constructor() : this(0f, 0f, 0f, 0f)
    fun applyOnRect(r: RectF) =
        RectF(r.left-left, r.top-top, r.right+right, r.bottom+bottom)

    operator fun plus(p: Padding) = Padding(left + p.left, top + p.top, right + p.right, bottom + p.bottom)
}