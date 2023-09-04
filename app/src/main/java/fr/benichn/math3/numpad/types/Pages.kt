package fr.benichn.math3.numpad.types

import android.graphics.Rect
import com.google.gson.JsonObject
import fr.benichn.math3.Utils.opt
import fr.benichn.math3.graphics.Utils.prepend
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.types.Side
import kotlin.math.max

data class NumpadButtonGroup(
    val rect: Rect,
    val normal: NumpadButton,
    val shift: NumpadButton?
)

data class NumpadButton(
    val main: NumpadButtonElement,
    val aux: List<NumpadButtonElement>,
    val hideOther: Boolean
) {
    val hasAux
        get() = aux.isNotEmpty()
}

data class NumpadButtonElement(val rect: Rect, val id: String)
data class NumpadPage(val width: Int, val height: Int, val coords: Pt, val buttons: List<NumpadButtonGroup>) {
    fun getButton(pt: Pt) = buttons.first { it.rect.contains(pt.x, pt.y) }

    companion object {
        private fun getRect(s: String): Rect {
            val btnRanges = s.split(",").map { getRange(it) }
            return Rect(btnRanges[0].start, btnRanges[1].start, btnRanges[0].end, btnRanges[1].end)
        }

        private fun getRange(s: String): Range {
            val ends = s.split(":", limit = 2)
            return if (ends.size == 1) ends[0].toInt().let { i -> Range(i, i + 1) }
            else Range(ends[0].toInt(), ends[1].toInt())
        }

        fun listFromJSON(pages: JsonObject) = pages.keySet().map { pk ->
            val coords = pk.split(",").map { it.toInt() }
            val pt = Pt(coords[0], coords[1])
            val page = pages[pk].asJsonObject
            val pw = page["w"].asInt
            val ph = page["h"].asInt
            fun readButton(btnRect: Rect, obj: JsonObject): NumpadButton {
                val id = obj["id"].asString
                val main = NumpadButtonElement(btnRect, id)
                val aux = obj.opt("aux")?.asJsonArray?.let { arr ->
                    val auxIds = arr.map { it.asString }
                    val auxPos = obj.opt("auxPos")?.asJsonArray?.let { apArr ->
                        (0 until apArr.size()).map { i -> getRect(apArr[i].asString) }
                    } ?: getAuxPositions(pw, ph, btnRect, auxIds.size)
                    auxPos.zip(auxIds) { rect, id -> NumpadButtonElement(rect, id) }
                }
                val hideOther = aux == null || (obj.opt("hideOther")?.asBoolean ?: true)
                return NumpadButton(main, aux ?: listOf(), hideOther)
            }
            val btns = page["buttons"].asJsonObject
            val buttons = btns.keySet().map { k ->
                val btnRect = getRect(k)
                btns[k].asJsonObject.let { obj ->
                    val normal = readButton(btnRect, obj["normal"].asJsonObject)
                    val shift = obj.opt("shift")?.asJsonObject?.let { o -> readButton(btnRect, o) }
                    NumpadButtonGroup(btnRect, normal, shift)
                }
            }.toList()
            NumpadPage(pw, ph, pt, buttons)
        }.toList()

        private fun getAuxPositions(w: Int, h: Int, rect: Rect, n: Int, side: Side = Side.L): List<Rect> {
            val cx = rect.centerX()
            var rxm = cx - rect.left
            var rxp = rect.right - 1 - cx
            if (side == Side.R) {
                rxm.let {
                    rxm = rxp
                    rxp = it
                }
            }
            val cy = rect.centerY()
            val rym = cy - rect.top
            val ryp = rect.bottom - 1 - cy
            val ux = if (side == Side.L) Pt.r else Pt.l
            val uy = Pt.t
            val o = Pt(cx, cy)
            fun makeLoop(r: Int): List<Pt> {
                val ot = o + uy * (r + rym)
                val t = (0 .. r + rxp).map { i -> ot + ux * i }
                val ol = t.last()
                val l = (1 .. 2*r + ryp).map { i -> ol - uy * i }
                val ob = l.last()
                val b = (1 .. r + rxp).map { i -> ob - ux * i }
                val rt = (1 .. r + rxm).map { i -> ot - ux * i }
                val or = rt.last()
                val rr = (1 .. 2*r + ryp).map { i -> or - uy * i }
                val orr = rr.last()
                val rb = (1 until r + rym).map { i-> orr + ux * i }
                return listOf(t, l, b, rt, rr, rb).flatten()
            }
            val auxPos = (1 until max(w, h))
                .flatMap { r -> makeLoop(r) }
                .filter { p -> p.x in 0 until w && p.y in 0 until h }
            return auxPos.map { pt -> Rect(pt.x, pt.y, pt.x + 1, pt.y + 1) }.prepend(rect).take(n)
        }
    }
}