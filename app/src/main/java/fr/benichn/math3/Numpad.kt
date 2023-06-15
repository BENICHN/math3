package fr.benichn.math3

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.fragment.app.Fragment
import org.json.JSONObject
import kotlin.math.abs

enum class Direction {
    Up,
    Left,
    Down,
    Right
}

data class Pt(val x: Int, val y: Int) {
    val sum: Int
        get() = x+y
    val l1: Int
        get() = abs(x)+ abs(y)
    val l2: Int
        get() = x*x+ y*y
    operator fun plus(p: Pt): Pt {
        return Pt(x+p.x,y+p.y)
    }
    operator fun minus(p: Pt): Pt {
        return Pt(x-p.x,y-p.y)
    }
    operator fun times(p: Pt): Pt {
        return Pt(x*p.x,y*p.y)
    }
    fun and(p: Pt): Pt {
        return Pt(
            if (p.x != 0) x else 0,
            if (p.y != 0) y else 0
        )
    }
    companion object {
        val z = Pt(0,0)
    }
}

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
            Direction.Up -> Pt(0,-1)
            Direction.Left -> Pt(1,0)
            Direction.Down -> Pt(0,1)
            Direction.Right -> Pt(-1,0)
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
        goTo(Pt(0,0))
    }

    private fun addDefaultPages() {
        val pages = JSONObject(requireContext().assets.open("numpad_pages.json").reader().use { it.readText() })
        Log.d("json", pages.toString())
        for (k in pages.keys()) {
            val coords = k.split(",").map { it.toInt() }
            val page = pages.getJSONObject(k)
            val pageview = NumpadPageView(requireContext(), page.getInt("w"), page.getInt("h"), page.getJSONObject("buttons"))
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

class NumpadPageView(context: Context, w: Int, h: Int, buttons: JSONObject) : GridLayout(ContextThemeWrapper(context, R.style.numpad_page), null, R.style.numpad_page) {
    val onSwipe = Callback<NumpadPageView, Direction>(this)
    val onButtonClicked = Callback<NumpadPageView, String>(this)

    fun hline(columnCount: Int, index: Int): View {
        return View(context).apply {
            layoutParams = LayoutParams(
                spec(index, 1, 0f),
                spec(0, columnCount, 0f)
            ).apply {
                height = 1
            }
        }
    }
    fun vline(rowCount: Int, index: Int): View {
        return View(context).apply {
            layoutParams = LayoutParams(
                spec(0, rowCount, 0f),
                spec(index, 1, 0f)
            ).apply {
                width = 1
            }
        }
    }

    init {
        rowCount = 2*h+1
        columnCount = 2*w+1
        addView(hline(columnCount, 0))
        addView(vline(rowCount, 0))
        for (i in 1..h) {
            addView(hline(columnCount, 2*i))
        }
        for (j in 1..w) {
            addView(vline(rowCount, 2*j))
        }
        for (i in 1..h) {
            for (j in 1..w) {
                val b = NumpadButton(context, buttons.getString("${i},${j}"))
                b.setOnClickListener {
                    onButtonClicked(b.id)
                }
                b.onSwipe += { s, d ->
                    onSwipe(d)
                }
                b.layoutParams = LayoutParams(
                    spec((2*i-1), 1, 1.0f),
                    spec((2*j-1), 1, 1.0f)
                )
                addView(b)
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
class NumpadButton(context: Context, val id: String) : androidx.appcompat.widget.AppCompatImageButton(ContextThemeWrapper(context, R.style.numpad_btn), null, R.style.numpad_btn) {
    val onSwipe = Callback<NumpadButton, Direction>(this)
    init {
        setOnTouchListener(object : SwipeTouchListener() {
            override fun onSwipeBottom() {
                onSwipe(Direction.Down)
            }
            override fun onSwipeLeft() {
                onSwipe(Direction.Left)
            }
            override fun onSwipeRight() {
                onSwipe(Direction.Right)
            }
            override fun onSwipeTop() {
                onSwipe(Direction.Up)
            }
        })
        stateListAnimator = null
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> isPressed = true
        }
        return super.onTouchEvent(event)
    }
}