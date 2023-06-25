package fr.benichn.math3.numpad

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.numpad.types.Pt
import org.json.JSONObject

class NumpadFragment : Fragment() {
    private lateinit var fl: FrameLayout

    val pageMap: MutableMap<Pt, NumpadPageView> = mutableMapOf()
    lateinit var pos: Pt
        private set

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val ctx = context
        return if (ctx == null) {
            null
        } else {
            fl = FrameLayout(ctx)
            fl
        }
    }

    fun nextPos(d: Direction) : Pt {
        val u = when (d) {
            Direction.Up -> Pt(0, -1)
            Direction.Left -> Pt(1, 0)
            Direction.Down -> Pt(0, 1)
            Direction.Right -> Pt(-1, 0)
        }
        val newPos = pos + u
        var newCandidates = pageMap.filter { (k, v) ->
            (k-newPos).and(u) == Pt.z
        }
        if (newCandidates.isEmpty()) {
            val mini = pageMap.minOf {(k, v) ->
                (k * u).sum
            } * u.sum
            newCandidates = pageMap.filter { (k, v) ->
                k.and(u).sum == mini
            }
        }
        return newCandidates.minBy { (k, v) ->
            (k-newPos).l1
        }.key
    }

    private fun onSwipe(d: Direction) {
        moveTo(nextPos(d), d)
    }

    var onButtonClicked: (String) -> Unit = {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addDefaultPages()
        goTo(Pt(0, 0))
    }

    private fun addDefaultPages() {
        val pages = JSONObject(
            requireContext().assets.open("numpad_pages.json").reader().use { it.readText() })
        Log.d("json", pages.toString())
        for (k in pages.keys()) {
            val coords = k.split(",").map { it.toInt() }
            val page = pages.getJSONObject(k)
            val pageview = NumpadPageView(
                requireContext(),
                page.getInt("w"),
                page.getInt("h"),
                page.getJSONObject("buttons")
            )
            pageview.onSwipe += { s, e -> onSwipe(e) }
            pageview.onButtonClicked += { s, e -> onButtonClicked(e) }
            pageMap[Pt(coords[0], coords[1])] = pageview
        }
    }

    fun goTo(pos: Pt) {
        this.pos = pos
        fl.removeAllViews()
        fl.addView(pageMap[pos])
    }

    private var isSwiping = false

    private fun moveTo(pos: Pt, d: Direction) {
        if (isSwiping) return
        val w = fl.width
        val h = fl.height
        val old = pageMap[this.pos]!!
        val new = pageMap[pos] ?: return
        if (old == new) return
        isSwiping = true
        fl.addView(new)
        when (d) {
            Direction.Up -> {
                Utils.setViewPosition(new, 0, h)
                Utils.animatePos(0, -h, SWIPE_DURATION, old, new) {
                    fl.removeView(old)
                    this.pos = pos
                    isSwiping = false
                }
            }
            Direction.Left -> {
                Utils.setViewPosition(new, w, 0)
                Utils.animatePos(-w, 0, SWIPE_DURATION, old, new) {
                    fl.removeView(old)
                    this.pos = pos
                    isSwiping = false
                }
            }
            Direction.Down -> {
                Utils.setViewPosition(new, 0, -h)
                Utils.animatePos(0, h, SWIPE_DURATION, old, new) {
                    fl.removeView(old)
                    this.pos = pos
                    isSwiping = false
                }
            }
            Direction.Right -> {
                Utils.setViewPosition(new, -w, 0)
                Utils.animatePos(w, 0, SWIPE_DURATION, old, new) {
                    fl.removeView(old)
                    this.pos = pos
                    isSwiping = false
                }
            }
        }
    }

    companion object {
        const val SWIPE_DURATION = 250L
    }
}