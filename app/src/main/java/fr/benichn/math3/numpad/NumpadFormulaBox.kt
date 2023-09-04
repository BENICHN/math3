package fr.benichn.math3.numpad

import android.animation.ValueAnimator
import android.graphics.PointF
import android.graphics.RectF
import android.util.SizeF
import androidx.core.animation.doOnEnd
import com.google.gson.JsonObject
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.numpad.types.NumpadPage
import fr.benichn.math3.numpad.types.Pt

class NumpadFormulaBox(pages: List<NumpadPage> = listOf(), size: SizeF = SizeF(0f, 0f)) : FormulaBox() {
    constructor(pages: JsonObject, size: SizeF = SizeF(0f, 0f)) : this(NumpadPage.listFromJSON(pages), size)

    val dlgPages = BoxProperty(this, pages).apply {
        onChanged += { _, e ->
            removeAllBoxes()
            addPages()
        }
    }
    var pages by dlgPages

    val dlgSize = BoxProperty(this, size).apply {
        onChanged += { _, _ ->
            resetPagesPositions()
        }
    }
    var size by dlgSize

    private var currentPageCoords: Pt = Pt.z
    val currentPage
        get() = pages[indexOfPage(currentPageCoords)]
    val currentPageBox
        get() = ch[indexOfPage(currentPageCoords)] as NumpadPageFormulaBox
    private var currentAnimation: ValueAnimator? = null

    init {
        addPages()
        resetPagesPositions()
    }

    override fun generateGraphics() = FormulaGraphics(
        PaintedPath(),
        bounds = RectF(0f, 0f, size.width, size.height),
    )

    private fun addPages() {
        for (p in pages) {
            val b = NumpadPageFormulaBox(p, size)
            b.dlgSize.connectTo(dlgSize)
            addBoxes(b)
        }
    }

    private fun resetPagesPositions() {
        val w = size.width
        val h = size.height
        for (i in ch.indices) {
            val p = pages[i]
            setChildTransform(ch[i], if (p.coords == currentPageCoords) BoxTransform() else BoxTransform(PointF(-w, -h)))
        }
    }

    private fun nextPos(d: Direction) : Pt {
        val u = when (d) {
            Direction.Up -> Pt(0, -1)
            Direction.Left -> Pt(1, 0)
            Direction.Down -> Pt(0, 1)
            Direction.Right -> Pt(-1, 0)
        }
        val newPos = currentPageCoords + u // on regarde la nouvelle position théorique
        var newCandidates = pages.filter { p -> // on regarde les positions ayant la même coordonnée que la nouvelle position selon la direction de u
            (p.coords-newPos).and(u) == Pt.z
        }
        if (newCandidates.isEmpty()) { // si abscence, on prend les positions minimisant cette coordonnée
            val mini = pages.minOf {p ->
                (p.coords * u).sum
            } * u.sum
            newCandidates = pages.filter { p ->
                p.coords.and(u).sum == mini
            }
        }
        return newCandidates.minBy { p ->
            (p.coords-newPos).l1
        }.coords
    }

    override fun findChildBox(pos: PointF) = currentPageBox

    private fun animateSwiping(deltaX: Float, deltaY: Float, duration: Long, vararg indices: Int, onEnd: () -> Unit = {}) =
        ValueAnimator.ofFloat(0f, 1f).also { va ->
            val x0s = indices.map { i -> ch[i].transform.origin.x }
            val y0s = indices.map { i -> ch[i].transform.origin.y }
            va.addUpdateListener {
                val t = Utils.easeOutExpo(it.animatedValue as Float)
                val dx = (deltaX * t).toInt()
                val dy = (deltaY * t).toInt()
                val xs = x0s.map { x0 -> x0 + dx }
                val ys = y0s.map { y0 -> y0 + dy }
                for (j in indices.indices) {
                    setChildTransform(ch[indices[j]], BoxTransform(PointF(xs[j], ys[j])))
                }
            }
            va.doOnEnd {
                onEnd()
            }
            va.duration = duration
            va.start()
        }

    fun swipe(d: Direction) = swipeTo(nextPos(d), d)

    private fun swipeTo(pos: Pt, d: Direction) {
        currentAnimation?.end()
        val w = size.width
        val h = size.height
        val oi = indexOfPage(currentPageCoords)
        val ni = indexOfPage(pos)
        if (oi == ni) return
        currentPageCoords = pos
        when (d) {
            Direction.Up -> {
                setChildTransform(ch[ni], BoxTransform.yOffset(h))
                currentAnimation = animateSwiping(0f, -h, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Left -> {
                setChildTransform(ch[ni], BoxTransform.xOffset(w))
                currentAnimation = animateSwiping(-w, 0f, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Down -> {
                setChildTransform(ch[ni], BoxTransform.yOffset(-h))
                currentAnimation = animateSwiping(0f, h, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
            Direction.Right -> {
                setChildTransform(ch[ni], BoxTransform.xOffset(-w))
                currentAnimation = animateSwiping(w, 0f, SWIPE_DURATION, oi, ni) {
                    setChildTransform(ch[oi], BoxTransform(PointF(-w, -h)))
                }
            }
        }
    }

    private fun indexOfPage(pt: Pt) = pages.indexOfFirst { p -> p.coords == pt }

    companion object {
        const val SWIPE_DURATION = 250L
    }
}