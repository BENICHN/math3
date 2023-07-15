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
    operator: FormulaBox = FormulaBox(),
    below: FormulaBox = FormulaBox(),
    above: FormulaBox = FormulaBox(),
    type: ScriptFormulaBox.Type = ScriptFormulaBox.Type.SUB
) : TopDownFormulaBox(
    TransformerFormulaBox(operator, BoundsTransformer.Align(RectPoint.NAN_CENTER)).also {
        it.padding = Padding(DEFAULT_TEXT_RADIUS * 0.25f)
    },
    below,
    above,
    type
) {
    val dlgOperator = BoxProperty(this, operator)
    var operator by dlgOperator
    val dlgBelow = BoxProperty(this, below)
    var below by dlgBelow
    val dlgAbove = BoxProperty(this, above)
    var above by dlgAbove

    init {
        (middle as TransformerFormulaBox).dlgChild.connectValue(dlgOperator.onChanged)
        bottomContainer.let {
            it.dlgChild.connectValue(dlgBelow.onChanged)
            it.transformers = it.transformers.prepend(BoundsTransformer.Constant(BoxTransform.scale(0.75f)))
        }
        topContainer.let {
            it.dlgChild.connectValue(dlgAbove.onChanged)
            it.transformers = it.transformers.prepend(BoundsTransformer.Constant(BoxTransform.scale(0.75f)))
        }
    }
}