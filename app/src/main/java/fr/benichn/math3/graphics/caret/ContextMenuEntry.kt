package fr.benichn.math3.graphics.caret

import fr.benichn.math3.graphics.boxes.FormulaBox

data class ContextMenuEntry(
    val box: FormulaBox,
    val action: (Any) -> List<CaretPosition>?
) {
    lateinit var finalAction: () -> Unit

    companion object {
        inline fun <reified T> create(box: FormulaBox, crossinline action: (T) -> List<CaretPosition>?) =
            ContextMenuEntry(box) {
                (it as? T)?.let { t -> action(t) }
            }
    }
}