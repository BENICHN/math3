package fr.benichn.math3.graphics.boxes.types

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

data class FormulaGraphics(val path: Path, val painting: PathPainting, val bounds: RectF) {
    constructor() : this(Path(), PathPainting.Transparent, RectF())
}


