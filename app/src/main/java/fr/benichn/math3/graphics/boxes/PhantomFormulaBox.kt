package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PathPainting

class PhantomFormulaBox(customBounds: RectF = RectF()) : FormulaBox() {
    val dlgCustomBounds = BoxProperty(this, customBounds)
    var customBounds by dlgCustomBounds

    override fun generateGraphics() = FormulaGraphics(
        Path(),
        PathPainting.Transparent,
        customBounds
    )
}