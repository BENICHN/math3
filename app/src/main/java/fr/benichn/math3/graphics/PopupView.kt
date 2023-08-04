package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.toPoint
import androidx.core.graphics.toRect
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.Callback
import kotlin.math.min


class PopupView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var popupSource: View? = null
    private var popup: View? = null
    private var onPopupDestroyed: (() -> Unit)? = null

    val hasPopup
        get() = popup != null

    // private val notifyPopupDestroyed = Callback<PopupView, View>(this)
    // val onPopupDestroyed = notifyPopupDestroyed.Listener()

    fun setPopup(source: View, v: View, x: Int, y: Int, onDestroyed: (() -> Unit)? = null) {
        removePopup()
        v.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(x, y, -x, -y)
        }
        popup = v
        popupSource = source
        onPopupDestroyed = onDestroyed
        addView(v)
    }
    fun removePopup() {
        if (popup != null) {
            removeView(popup)
            val l = onPopupDestroyed
            popup = null
            popupSource = null
            onPopupDestroyed = null
            l?.invoke()
        }
    }
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        val hp = hasPopup
        return super.dispatchTouchEvent(e).also {
            if (hp) {
                popup?.run {
                    val viewRect = Rect()
                    getGlobalVisibleRect(viewRect)
                    if (!viewRect.contains(e.rawX.toInt(), e.rawY.toInt())) {
                        removePopup()
                    }
                }
            }
        }
    }

    companion object {
        private fun findPopupParent(v: View): PopupView? = (v.parent as? View)?.let { p -> if (p is PopupView) p else findPopupParent(p) }
        fun View.requirePopup(popup: View, sourceBounds: RectF, sourceRP: RectPoint, sourcePadding: Padding, onDestroyed: (() -> Unit)? = null) {
            findPopupParent(this)?.let { p ->
                popup.measure(0, 0)
                val w = popup.measuredWidth
                val h = popup.measuredHeight
                val pr = Rect(0, 0, w, h)
                val r = sourcePadding.applyOnRect(sourceBounds).toRect()
                p.offsetDescendantRectToMyCoords(this, r)
                val a = sourceRP.get(r)
                val adjustedTy = when {
                    sourceRP.ty < 0.5f -> { // top
                        if (a.y - h*0.5f < 0) { // overflow y
                            1f-sourceRP.ty
                        } else sourceRP.ty
                    }
                    sourceRP.ty == 0.5f -> sourceRP.ty // center
                    else -> { // bottom
                        if (a.y + h*0.5f < 0) { // overflow y
                            1f-sourceRP.ty
                        } else sourceRP.ty
                    }
                }
                val anchor = RectPoint(sourceRP.tx, adjustedTy).get(r).toPoint()
                val popupRp = when {
                    adjustedTy < 0.5f -> RectPoint.BOTTOM_CENTER
                    adjustedTy == 0.5f -> RectPoint.CENTER
                    else -> RectPoint.TOP_CENTER
                }
                val popupAnchor = popupRp.get(pr).toPoint()
                val u = anchor - popupAnchor
                val popupMovedRect = pr + u
                val ol = -min(0, popupMovedRect.left)
                val ot = -min(0, popupMovedRect.top)
                val or = -min(0, p.width - popupMovedRect.right)
                val ob = -min(0, p.width - popupMovedRect.bottom)
                val v = Point(u.x + ol - or, u.y + ot - ob)
                p.setPopup(this, popup, v.x, v.y, onDestroyed)
            }
        }
        fun View.destroyPopup() {
            findPopupParent(this)?.let { p ->
                if (p.popupSource == this) p.removePopup()
            }
        }
        // fun View.addOnPopupDestroyed(l: () -> Unit) {
        //     findPopupParent(this)?.let { p ->
        //         p.onPopupDestroyed += { _, s ->
        //             if (s == this) {
        //                 l()
        //             }
        //         }
        //     }
        // }
        fun View.getPopup() =
            findPopupParent(this)?.let { p ->
                if (p.popupSource == this) p.popup
                else null
            }
    }
}