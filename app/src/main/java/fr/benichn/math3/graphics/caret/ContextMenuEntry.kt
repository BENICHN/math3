package fr.benichn.math3.graphics.caret

import fr.benichn.math3.graphics.boxes.FormulaBox

data class ContextMenuEntry(
    val box: FormulaBox,
    val action: (Any) -> Unit
) {
    lateinit var finalAction: () -> Unit

    companion object {
        inline fun <reified T> create(box: FormulaBox, crossinline action: (T) -> Unit) =
            ContextMenuEntry(box) {
                if (it is T) {
                    action(it)
                }
            }
    }
}