package fr.benichn.math3.graphics.boxes.types

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF

data class FormulaGraphics(val path: Path, val painting: PathPainting, val bounds: RectF, val foreground: Int = Color.WHITE, val background: Int = Color.TRANSPARENT) {
    constructor() : this(Path(), PathPainting.Transparent, RectF())
    inline fun withBounds(f: (RectF) -> RectF) = FormulaGraphics(
        path,
        painting,
        f(bounds),
        foreground,
        background
    )
    inline fun withPath(f: (Path) -> Path) = FormulaGraphics(
        f(path),
        painting,
        bounds,
        foreground,
        background
    )
    inline fun withPainting(f: (PathPainting) -> PathPainting) = FormulaGraphics(
        path,
        f(painting),
        bounds,
        foreground,
        background
    )
    inline fun withForeground(f: (Int) -> Int) = FormulaGraphics(
        path,
        painting,
        bounds,
        f(foreground),
        background
    )
    inline fun withBackground(f: (Int) -> Int) = FormulaGraphics(
        path,
        painting,
        bounds,
        foreground,
        f(background)
    )
}


