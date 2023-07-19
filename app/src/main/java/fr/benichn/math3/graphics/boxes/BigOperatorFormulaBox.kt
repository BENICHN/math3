package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.Utils.Companion.prepend
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ValueChangedEvent

class BigOperatorFormulaBox(
    limitsPosition: LimitsPosition = LimitsPosition.CENTER,
    limitsScale: Float = 0.75f,
    type: Type = Type.BOTTOM,
    operator: FormulaBox = FormulaBox(),
    below: FormulaBox = FormulaBox(),
    above: FormulaBox = FormulaBox(),
) : TopDownFormulaBox(
    limitsPosition,
    limitsScale,
    type,
    TransformerFormulaBox(operator, BoundsTransformer.Align(RectPoint.NAN_CENTER)).also {
        it.padding = Padding(DEFAULT_TEXT_RADIUS * 0.25f)
    },
    below,
    above
) {
    val dlgOperator = BoxProperty(this, operator)
    var operator by dlgOperator
    val dlgBelow = BoxProperty(this, below)
    var below by dlgBelow
    val dlgAbove = BoxProperty(this, above)
    var above by dlgAbove

    init {
        middleBoundsScaleX = 0.5f // !
        middleBoundsScaleY = 0.33f // !
        (middle as TransformerFormulaBox).dlgChild.connectValue(dlgOperator.onChanged)
        bottomContainer.dlgChild.connectValue(dlgBelow.onChanged)
        topContainer.dlgChild.connectValue(dlgAbove.onChanged)
    }
}