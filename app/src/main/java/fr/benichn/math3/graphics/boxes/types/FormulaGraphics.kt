package fr.benichn.math3.graphics.boxes.types

import android.graphics.Color
import android.graphics.RectF

data class FormulaGraphics(val pictures: List<PaintedPath> = listOf(), val bounds: RectF = RectF(), val background: Int = Color.TRANSPARENT) {
    constructor(vararg pictures: PaintedPath?, bounds: RectF, background: Int = Color.TRANSPARENT) : this(
        pictures.filterNotNull(),
        bounds,
        background)
    inline fun withBounds(f: (RectF) -> RectF) = FormulaGraphics(
        pictures,
        f(bounds),
        background
    )
}