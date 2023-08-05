package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.toPoint
import androidx.core.graphics.toRect
import androidx.core.graphics.toRectF
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.RectPoint
import kotlin.math.min


class PopupView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var popupSource: View? = null
    private var popup: View? = null
    private var onPopupDestroyed: (() -> Unit)? = null

    val hasPopup
        get() = popup != null

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
        fun View.findPopupParent(): PopupView? = (parent as? View)?.let { p -> if (p is PopupView) p else p.findPopupParent() }
        fun View.requirePopup(popup: View, sourceBounds: RectF, sourceRP: RectPoint, sourcePadding: Padding, onDestroyed: (() -> Unit)? = null) {
            findPopupParent()?.let { p ->
                popup.measure(0, 0)
                val w = popup.measuredWidth
                val h = popup.measuredHeight
                val r = sourcePadding.applyOnRect(sourceBounds).toRect()
                Log.d("r", r.toString())
                p.offsetDescendantRectToMyCoords(this, r)
                Log.d("rp", r.toString())
                val v = getPopupPosition(Size(p.width, p.height), Size(w, h), r, sourceRP)
                Log.d("v", v.toString())
                p.setPopup(this, popup, v.x, v.y, onDestroyed)
            }
        }
        fun getPopupPosition(popupViewSize: Size, popupSize: Size, sourceRealBounds: Rect, sourceRP: RectPoint): Point {
            val w = popupSize.width
            val h = popupSize.height
            val pr = Rect(0, 0, w, h)
            val a = sourceRP.get(sourceRealBounds)
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
            val anchor = RectPoint(sourceRP.tx, adjustedTy).get(sourceRealBounds).toPoint()
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
            val or = -min(0, popupViewSize.width - popupMovedRect.right)
            val ob = -min(0, popupViewSize.width - popupMovedRect.bottom)
            return Point(u.x + ol - or, u.y + ot - ob)
        }
        fun getPopupPositionF(popupViewSize: SizeF, popupSize: SizeF, sourceRealBounds: RectF, sourceRP: RectPoint): PointF {
            val w = popupSize.width
            val h = popupSize.height
            val pr = RectF(0f, 0f, w, h)
            val a = sourceRP.get(sourceRealBounds)
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
            val anchor = RectPoint(sourceRP.tx, adjustedTy).get(sourceRealBounds)
            val popupRp = when {
                adjustedTy < 0.5f -> RectPoint.BOTTOM_CENTER
                adjustedTy == 0.5f -> RectPoint.CENTER
                else -> RectPoint.TOP_CENTER
            }
            val popupAnchor = popupRp.get(pr)
            val u = anchor - popupAnchor
            val popupMovedRect = pr + u
            val ol = -min(0f, popupMovedRect.left)
            val ot = -min(0f, popupMovedRect.top)
            val or = -min(0f, popupViewSize.width - popupMovedRect.right)
            val ob = -min(0f, popupViewSize.width - popupMovedRect.bottom)
            return PointF(u.x + ol - or, u.y + ot - ob)
        }
        fun View.requirePopup(popup: View, x: Int, y: Int, onDestroyed: (() -> Unit)? = null) {
            findPopupParent()?.let { p ->
                p.setPopup(this, popup, x, y, onDestroyed)
            }
        }
        fun View.getCoordsInPopupView() =
            findPopupParent()?.let { p ->
                val r = Rect(0,0,1,1)
                p.offsetDescendantRectToMyCoords(this, r)
                RectPoint.TOP_LEFT.get(r)
            }
        fun View.destroyPopup() {
            findPopupParent()?.let { p ->
                if (p.popupSource == this) p.removePopup()
            }
        }
        fun View.getPopup() =
            findPopupParent()?.let { p ->
                if (p.popupSource == this) p.popup
                else null
            }
    }
}