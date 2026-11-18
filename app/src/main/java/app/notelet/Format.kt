package app.notelet

import android.content.Context
import android.text.format.DateUtils

object Format {

    /** "Today, 6:30 PM", "Tomorrow, 09:00" or "Fri, Nov 28, 9:00 AM" (follows the phone's 12/24h setting). */
    fun whenText(context: Context, at: Long): String {
        val time = DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_TIME)
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

    /** The line shown under a note about its next pop-up, or null if none is waiting. */
    fun reminderLine(context: Context, note: Note): String? {
        if (note.done) return null
        note.snoozeUntil?.let { return context.getString(R.string.snoozed_until, whenText(context, it)) }
        val at = note.remindAt?.takeIf { it > note.lastNotifiedAt } ?: return null
        val line = context.getString(R.string.reminder_at, whenText(context, at))
        return if (note.repeat == Repeat.NONE) line else "$line · ${context.getString(note.repeat.labelRes)}"
    }
}
