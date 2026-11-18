package app.notelet

import android.net.Uri
import org.json.JSONObject
import java.util.Calendar

const val EXTRA_NOTE_ID = "app.notelet.extra.NOTE_ID"

/** A unique URI per note, so Android keeps each note's alarms and intents apart. */
fun noteUri(id: Long): Uri = Uri.parse("notelet://note/$id")

enum class Repeat(val labelRes: Int) {
    NONE(R.string.repeat_none),
    HOURLY(R.string.repeat_hourly),
    DAILY(R.string.repeat_daily),
    WEEKDAYS(R.string.repeat_weekdays),
    WEEKLY(R.string.repeat_weekly);

    /** The first occurrence after [now], stepping forward from [from]. */
    fun nextAfter(from: Long, now: Long): Long {
        if (this == NONE) return from
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        while (cal.timeInMillis <= now || (this == WEEKDAYS && cal.isWeekend())) {
            when (this) {
                HOURLY -> cal.add(Calendar.HOUR_OF_DAY, 1)
                DAILY, WEEKDAYS -> cal.add(Calendar.DAY_OF_YEAR, 1)
                WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                NONE -> Unit
            }
        }
        return cal.timeInMillis
    }

    private fun Calendar.isWeekend(): Boolean {
        val day = get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }
}

/**
 * One note or task.
 *
 * [remindAt] is the next time the pop-up should appear. A reminder counts as
 * already shown once [lastNotifiedAt] has caught up with it; repeating
 * reminders move [remindAt] forward each time they fire.
 */
data class Note(
    val id: Long,
    val text: String,
    val isTask: Boolean = false,
    val done: Boolean = false,
    val remindAt: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    val snoozeUntil: Long? = null,
    val lastNotifiedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** First line: used as the pop-up title and the list title. */
    val headline: String
        get() = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()

    /** Everything after the first line. */
    val details: String
        get() = text.trim().lines().drop(1).joinToString("\n").trim()

    /** When this note should pop up next, or null if nothing is waiting. */
    fun nextAlarmAt(): Long? {
        if (done) return null
        val reminder = remindAt?.takeIf { it > lastNotifiedAt }
        return listOfNotNull(reminder, snoozeUntil).minOrNull()
    }

    /**
     * What happens when this note's alarm goes off at [now]: returns the updated
     * note and whether the pop-up should be shown.
     */
    fun fire(now: Long): Pair<Note, Boolean> {
        if (done) return this to false
        var updated = this
        var due = false

        val snooze = snoozeUntil
        if (snooze != null && snooze <= now + FIRE_GRACE_MS) {
            updated = updated.copy(snoozeUntil = null)
            due = true
        }

        val at = remindAt
        if (at != null && at > lastNotifiedAt && at <= now + FIRE_GRACE_MS) {
            val firedAt = maxOf(now, at)
            updated = updated.copy(
                lastNotifiedAt = firedAt,
                remindAt = if (repeat == Repeat.NONE) at else repeat.nextAfter(at, firedAt),
            )
            due = true
        }
        return updated to due
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("task", isTask)
        put("done", done)
        remindAt?.let { put("remindAt", it) }
        put("repeat", repeat.name)
        snoozeUntil?.let { put("snoozeUntil", it) }
        put("lastNotifiedAt", lastNotifiedAt)
        put("createdAt", createdAt)
    }

    companion object {
        /** Alarms can arrive a moment early; treat anything this close as due. */
        const val FIRE_GRACE_MS = 5_000L

        fun fromJson(json: JSONObject) = Note(
            id = json.getLong("id"),
            text = json.optString("text"),
            isTask = json.optBoolean("task"),
            done = json.optBoolean("done"),
            remindAt = if (json.has("remindAt")) json.getLong("remindAt") else null,
            repeat = Repeat.entries.firstOrNull { it.name == json.optString("repeat") } ?: Repeat.NONE,
            snoozeUntil = if (json.has("snoozeUntil")) json.getLong("snoozeUntil") else null,
            lastNotifiedAt = json.optLong("lastNotifiedAt"),
            createdAt = json.optLong("createdAt"),
        )
    }
}
