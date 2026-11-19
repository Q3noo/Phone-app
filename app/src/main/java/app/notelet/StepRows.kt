package app.notelet

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** A finished step row (crossed out, with its time and the line down to the next step) for the app's screens. */
object StepRows {
    fun create(parent: ViewGroup, step: Step): View {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.widget_step_row, parent, false)
        row.findViewById<TextView>(R.id.step_text).apply {
            text = step.headline
            paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        row.findViewById<TextView>(R.id.step_time).text = Format.stepLine(parent.context, step)
        return row
    }

    fun fill(container: ViewGroup, steps: List<Step>) {
        container.removeAllViews()
        steps.forEach { container.addView(create(container, it)) }
    }
}
