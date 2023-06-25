package fr.benichn.math3.numpad

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.GridLayout
import fr.benichn.math3.R
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.types.Callback
import org.json.JSONObject

class NumpadPageView(context: Context, w: Int, h: Int, buttons: JSONObject) : GridLayout(
    ContextThemeWrapper(
        context,
        R.style.numpad_page
    ), null, R.style.numpad_page
) {
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
                    spec((2 * i - 1), 1, 1.0f),
                    spec((2 * j - 1), 1, 1.0f)
                )
                addView(b)
            }
        }
    }
}