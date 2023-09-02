package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.Utils.toPt
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.numpad.types.Pt

class MatrixFormulaBox(shape: Pt = Pt(1, 1), matrixType: Type = Type.MATRIX) :
    BracketsSequenceFormulaBox(
        TransformerFormulaBox(GridFormulaBox(shape)),
    ) {
    val grid = sequence.ch[1].ch[0] as GridFormulaBox

    enum class Type {
        MATRIX,
        PARAMS,
        LIST
    }

    val dlgMatrixType = BoxProperty(this, matrixType)
    var matrixType by dlgMatrixType

    init {
        (sequence.ch[1] as TransformerFormulaBox).dlgTransformers.connectValue(dlgMatrixType.onChanged, matrixType) { type ->
            listOf(BoundsTransformer.Align(when (type) {
                Type.MATRIX, Type.LIST -> RectPoint.CENTER
                Type.PARAMS -> RectPoint.NAN_CENTER
            }))
        }
        dlgType.connectValue(dlgMatrixType.onChanged, matrixType) { type ->
            when (type) {
                Type.MATRIX -> BracketFormulaBox.Type.BRACE
                Type.PARAMS -> BracketFormulaBox.Type.BRACKET
                Type.LIST -> BracketFormulaBox.Type.CURLY
            }
        }
    }

    override fun addInitialBoxes(ib: InitialBoxes) = grid.addInitialBoxes(ib)

    override fun getInitialSingle() = grid.getInitialSingle()

    override fun toWolfram(mode: Int): String {
        val (ds, de) = when (matrixType) {
            Type.MATRIX, Type.LIST -> '{' to '}'
            Type.PARAMS -> '[' to ']'
        }
        val twoD = matrixType == Type.MATRIX
        return grid.rows.joinToString(",") { row -> "${if (twoD) ds else ""}${row.joinToString(", ") { b -> b.toWolfram(mode) }}${if (twoD) de else ""}" }.let { "$ds$it$de" }
    }

    // override fun toSage() =
    //     grid.rows.joinToString(",") { row -> "[${row.joinToString(", ") { b -> b.toSage() }}]" }.let { "[$it]" }

    override fun toJson() = makeJsonObject("matrix") {
        addProperty("type", matrixType.toString())
        add("grid", grid.toJson())
    }

    companion object {
        init {
            deserializers.add(FormulaBoxDeserializer("matrix") {
                val gr = getAsJsonObject("grid")
                val inps = gr.getAsJsonArray("inputs")
                MatrixFormulaBox(
                    gr.getAsJsonArray("shape").toPt(),
                    Type.valueOf(get("type").asString)
                ).apply {
                    grid.inputs.forEachIndexed { i, inp -> inp.addBoxes(inps[i].asJsonArray.toBoxes()) }
                }
            })
        }
    }
}