package app.notelet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.view.isVisible
import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * Roll wheels for picking a moment: month and day, then hour, minute and AM/PM.
 *
 * There is no year wheel: a month and day earlier than today means that date next
 * year (the summary line above the wheels shows the year when it isn't this one).
 * With [showDate] false only the time wheels show, on the day of [baseDay].
 */
class WheelPicker(
    private val context: Context,
    initial: Long,
    private val showDate: Boolean,
    timeLabelRes: Int,
    private val baseDay: Long = initial,
) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_wheels, null)

    private val month: NumberPicker = view.findViewById(R.id.wheel_month)
    private val day: NumberPicker = view.findViewById(R.id.wheel_day)
    private val hour: NumberPicker = view.findViewById(R.id.wheel_hour)
    private val minute: NumberPicker = view.findViewById(R.id.wheel_minute)
    private val amPm: NumberPicker = view.findViewById(R.id.wheel_ampm)
    private val summary: TextView = view.findViewById(R.id.wheel_summary)

    init {
        view.findViewById<View>(R.id.date_section).isVisible = showDate
        view.findViewById<TextView>(R.id.time_label).setText(timeLabelRes)

        val start = Calendar.getInstance().apply { timeInMillis = initial }
        val symbols = DateFormatSymbols.getInstance()

        month.setUp(0, 11, symbols.shortMonths.take(12).toTypedArray(), wrap = true)
        month.value = start.get(Calendar.MONTH)
        day.setUp(1, 31, null, wrap = true)
        fitDaysToMonth()
        day.value = start.get(Calendar.DAY_OF_MONTH).coerceAtMost(day.maxValue)

        hour.setUp(1, 12, null, wrap = true)
        hour.value = start.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        minute.setUp(0, 59, Array(60) { "%02d".format(it) }, wrap = true)
        minute.value = start.get(Calendar.MINUTE)
        amPm.setUp(0, 1, symbols.amPmStrings, wrap = false)
        amPm.value = start.get(Calendar.AM_PM)

        month.setOnValueChangedListener { _, _, _ ->
            fitDaysToMonth()
            updateSummary()
        }
        day.setOnValueChangedListener { _, _, _ -> updateSummary() }
        hour.setOnValueChangedListener { _, old, new ->
            // Rolling past 12 flips AM/PM, like a clock.
            if ((old == 11 && new == 12) || (old == 12 && new == 11)) amPm.value = 1 - amPm.value
            updateSummary()
        }
        minute.setOnValueChangedListener { _, _, _ -> updateSummary() }
        amPm.setOnValueChangedListener { _, _, _ -> updateSummary() }
        updateSummary()
    }

    /** The moment the wheels point at. */
    fun selectedTime(): Long {
        val cal = Calendar.getInstance()
        if (showDate) {
            cal.clear()
            cal.set(yearFor(month.value, day.value), month.value, day.value)
        } else {
            cal.timeInMillis = baseDay
        }
        cal.set(Calendar.HOUR_OF_DAY, hour.value % 12 + amPm.value * 12)
        cal.set(Calendar.MINUTE, minute.value)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun updateSummary() {
        val at = selectedTime()
        val text = if (showDate) Format.whenText(context, at) else Format.timeText(context, at)
        summary.text = if (showDate && at <= System.currentTimeMillis()) {
            context.getString(R.string.reminder_passed, text)
        } else {
            text
        }
    }

    /** A date before today means that date next year. */
    private fun yearFor(month: Int, day: Int): Int {
        val today = Calendar.getInstance()
        val thisMonth = today.get(Calendar.MONTH)
        val year = today.get(Calendar.YEAR)
        return if (month < thisMonth || (month == thisMonth && day < today.get(Calendar.DAY_OF_MONTH))) year + 1 else year
    }

    /** 28, 29, 30 or 31 days, depending on the month. */
    private fun fitDaysToMonth() {
        val days = Calendar.getInstance().apply {
            clear()
            set(yearFor(month.value, 31), month.value, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (day.maxValue != days) {
            val keep = day.value.coerceAtMost(days)
            day.maxValue = days
            day.value = keep
            day.wrapSelectorWheel = true
        }
    }

    private fun NumberPicker.setUp(min: Int, max: Int, labels: Array<String>?, wrap: Boolean) {
        minValue = min
        maxValue = max
        displayedValues = labels
        wrapSelectorWheel = wrap
        // Roll only: don't pop up the keyboard when the middle value is tapped.
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
}
