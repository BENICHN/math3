package fr.benichn.math3.graphics.boxes.types

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import fr.benichn.math3.types.callback.ObservableProperty

// abstract class PathPainting {
//     data object Fill : PathPainting() {
//         override fun getPaint(color: Int): Paint {
//             return Paint().also {
//                 it.style = Paint.Style.FILL
//                 it.color = color
//             }
//         }
//     }
//
//     data class Stroke(val width: Float) : PathPainting() {
//         override fun getPaint(color: Int): Paint {
//             return Paint().also {
//                 it.style = Paint.Style.STROKE
//                 it.strokeWidth = width
//                 it.color = color
//             }
//         }
//     }
//
//     data object Transparent : PathPainting() {
//         override fun getPaint(color: Int): Paint {
//             return Paint().also {
//                 it.color = Color.TRANSPARENT
//             }
//         }
//         fun getPaint() = getPaint(0)
//     }
//
//     abstract fun getPaint(color: Int) : Paint
// }

object Paints {
    val transparent
        get() = Paint().apply {
            color = Color.TRANSPARENT
        }
    fun stroke(width: Float, color: Int = Color.WHITE) = Paint().also {
        it.style = Paint.Style.STROKE
        it.strokeWidth = width
        it.color = color
    }
    fun fill(color: Int = Color.WHITE) = Paint().also {
        it.style = Paint.Style.FILL
        it.color = color
    }
}

data class PaintedPath(
    val path: Path = Path(),
    val paint: Paint = Paints.transparent,
    val persistentColor: Boolean = false
) {
    var forcedColor by ObservableProperty<PaintedPath, Int?>(this, null).apply {
        onChanged += { _, _ -> updateRealPaint() }
    }
    var realPaint = paint
        private set
    private fun updateRealPaint() {
        realPaint = if (persistentColor || forcedColor == null) paint else Paint(paint).also { it.color = forcedColor!! }
    }
}