package app.notelet

import android.content.Context
import android.text.format.DateUtils

object Format {

    /** "Today, 6:30 PM", "Tomorrow, 09:00" or "Fri, Nov 28, 9:00 AM" (follows the phone's 12/24h setting). */
    fun whenText(context: Context, at: Long): String {
        val time = timeText(context, at)
        return when {
            DateUtils.isToday(at) -> context.getString(R.string.today_at, time)
            DateUtils.isToday(at - DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.tomorrow_at, time)
            else -> DateUtils.formatDateTime(
                context,
                at,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME,
            )
        }
    }

    fun timeText(context: Context, at: Long): String =
        DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_TIME)

    /** "Today, 2:00 PM – 4:00 PM" */
    fun frameText(context: Context, start: Long, windowMinutes: Int): String =
        context.getString(R.string.frame_range, whenText(context, start), timeText(context, start + windowMinutes * MINUTE))

    /** The reminder as set: the exact time, or the time frame. */
    fun reminderText(context: Context, note: Note): String? {
        val start = note.remindAt ?: return null
        return if (note.windowMinutes > 0) {
            val frame = context.getString(R.string.frame_at, frameText(context, start, note.windowMinutes))
            if (note.popCount > 1) "$frame · ${context.getString(R.string.pops_count, note.popCount)}" else frame
        } else {
            context.getString(R.string.reminder_at, whenText(context, start))
        }
    }

    /** The line shown on a note about its next pop-up, or null if none is waiting. */
    fun reminderLine(context: Context, note: Note): String? {
        if (note.done) return null
        note.snoozeUntil?.let { return context.getString(R.string.snoozed_until, whenText(context, it)) }
        if (!note.hasUpcomingReminder) return null
        val line = reminderText(context, note) ?: return null
        return if (note.repeat == Repeat.NONE) line else "$line · ${context.getString(note.repeat.labelRes)}"
    }
}
