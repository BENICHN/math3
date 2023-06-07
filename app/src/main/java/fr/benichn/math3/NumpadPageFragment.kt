package fr.benichn.math3

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.Animation
import android.view.animation.Transformation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import java.time.Duration
import kotlin.math.abs

enum class Direction {
    Up,
    Left,
    Down,
    Right
}

@SuppressLint("ClickableViewAccessibility")
class NumpadButton(context: Context) : AppCompatButton(context) {
    var onSwipe : (Direction) -> Unit = {}
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
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> isPressed = true
        }
        return super.onTouchEvent(event)
    }
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

    fun onSwipe(d: Direction) {
        moveTo(nextPos(d), d)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addDefaultPages()
        goTo(Pt(0,0))
    }

    private fun addDefaultPages() {
        pageMap[Pt(0,0)] = NumpadPageView(requireContext(), 3, 3, "c")
        pageMap[Pt(0,0)]?.onSwipe = { d ->
            onSwipe(d)
        }
        pageMap[Pt(1,0)] = NumpadPageView(requireContext(), 3, 3, "r")
        pageMap[Pt(1,0)]?.onSwipe = { d ->
            onSwipe(d)
        }
        pageMap[Pt(0,-1)] = NumpadPageView(requireContext(), 3, 3, "b")
        pageMap[Pt(0,-1)]?.onSwipe = { d ->
            onSwipe(d)
        }
        pageMap[Pt(-1,0)] = NumpadPageView(requireContext(), 3, 3, "l")
        pageMap[Pt(-1,0)]?.onSwipe = { d ->
            onSwipe(d)
        }
        pageMap[Pt(0,1)] = NumpadPageView(requireContext(), 3, 3, "t")
        pageMap[Pt(0,1)]?.onSwipe = { d ->
            onSwipe(d)
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

    // fun goUp() {
    //     val h = fl.height
    //     fl.addView(pages[1])
    //     Utils.setViewPosition(pages[1], 0, -h)
    //     Utils.animatePos( 0, h, 250, pages[0], pages[1]) {
    //         Log.d("ZXCV", "FGDFFD")
    //         fl.removeView(pages[0])
    //     }
    // }

    companion object {
        const val SWIPE_DURATION = 200L
    }
}

class NumpadPageView(context: Context, w: Int, h: Int, text: String) : GridLayout(context) {
    var onSwipe : (Direction) -> Unit = {}
    var onButtonClicked: (Int, Int) -> Unit = {_, _ ->}
    init {
        rowCount = h
        columnCount = w
        for (i in 1..h) {
            for (j in 1..w) {
                val b = NumpadButton(context)
                b.background = AppCompatResources.getDrawable(context, R.drawable.btn_selector)
                b.setTextColor(Color.BLACK)
                b.stateListAnimator = null
                b.text = "${text} ~ ${i}, ${j}"
                b.setOnClickListener {
                    onButtonClicked(i, j)
                }
                b.onSwipe = { d ->
                    onSwipe(d)
                }
                b.layoutParams = GridLayout.LayoutParams(
                    spec(i-1, 1, 1.0f),
                    spec(j-1, 1, 1.0f)
                )
                addView(b)
            }
        }
    }
}