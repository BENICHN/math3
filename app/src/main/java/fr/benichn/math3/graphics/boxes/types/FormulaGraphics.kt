package fr.benichn.math3.graphics.boxes.types

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

data class FormulaGraphics(val path: Path, val painting: PathPainting, val bounds: RectF) {
    constructor() : this(Path(), PathPainting.Transparent, RectF())
    inline fun withBounds(f: (RectF) -> RectF) = FormulaGraphics(
        path,
        painting,
        f(bounds)
    )
    inline fun withPath(f: (Path) -> Path) = FormulaGraphics(
        f(path),
        painting,
        bounds
    )
    inline fun withPainting(f: (PathPainting) -> PathPainting) = FormulaGraphics(
        path,
        f(painting),
        bounds
    )
}


