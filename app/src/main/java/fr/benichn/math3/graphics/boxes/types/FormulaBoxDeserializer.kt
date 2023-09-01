package fr.benichn.math3.graphics.boxes.types

import com.google.gson.JsonObject
import fr.benichn.math3.graphics.boxes.FormulaBox

data class FormulaBoxDeserializer<out T : FormulaBox>(
    val tag: String,
    val factory: JsonObject.() -> T
)