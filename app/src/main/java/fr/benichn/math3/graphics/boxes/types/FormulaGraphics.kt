package fr.benichn.math3.graphics.boxes.types

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.types.callback.ObservableProperty

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
    inline fun withPictures(f: (List<PaintedPath>) -> List<PaintedPath>) = FormulaGraphics(
        f(pictures),
        bounds,
        background
    )
    inline fun withBackground(f: (Int) -> Int) = FormulaGraphics(
        pictures,
        bounds,
        f(background)
    )
}

object Paints {
    val transparent
        get() = Paint().apply {
            color = Color.TRANSPARENT
        }
    fun stroke(width: Float, color: Int = Color.WHITE) = Paint().also {
        it.style = Paint.Style.STROKE
        it.strokeWidth = width
        it.color = color
    }
    fun fill(color: Int = Color.WHITE) = Paint().also {
        it.style = Paint.Style.FILL
        it.color = color
    }
}

data class PaintedPath(
    val path: Path = Path(),
    val paint: Paint = Paints.transparent,
    val persistentColor: Boolean = false
) {
    var forcedColor by ObservableProperty<PaintedPath, Int?>(this, null).apply {
        onChanged += { _, _ -> updateRealPaint() }
    }
    var realPaint = paint
        private set
    private fun updateRealPaint() {
        realPaint = if (persistentColor || forcedColor == null) paint else Paint(paint).also { it.color = forcedColor!! }
    }
}