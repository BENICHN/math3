package fr.benichn.math3.graphics.boxes.types

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

data class FormulaGraphics(val path: Path, val paint: Paint, val bounds: RectF) {
    constructor() : this(Path(), Paint(), RectF())
}


