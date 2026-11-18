package app.notelet

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

const val EXTRA_NOTE_ID = "app.notelet.extra.NOTE_ID"
const val MINUTE = 60_000L

/** A unique URI per note, so Android keeps each note's alarms and intents apart. */
fun noteUri(id: Long): Uri = Uri.parse("notelet://note/$id")

enum class Repeat(val labelRes: Int, val maxWindowMinutes: Int) {
    NONE(R.string.repeat_none, 24 * 60),
    HOURLY(R.string.repeat_hourly, 60),
    DAILY(R.string.repeat_daily, 24 * 60),
    WEEKDAYS(R.string.repeat_weekdays, 24 * 60),
    WEEKLY(R.string.repeat_weekly, 24 * 60);

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
 * [count] random whole minutes inside the frame [start, start + windowMinutes], all
 * after [now], in order. The (remaining) frame is split into [count] equal parts and
 * one moment is picked at random in each, so several pop-ups are spread across the
 * frame instead of bunching up.
 */
fun pickRandomTimes(start: Long, windowMinutes: Int, count: Int, now: Long, random: Random): List<Long> {
    val firstAllowed = ((maxOf(start, now + 1) - start + MINUTE - 1) / MINUTE).toInt()
    if (firstAllowed > windowMinutes) return listOf(start + windowMinutes * MINUTE)
    val slots = windowMinutes - firstAllowed + 1
    val n = count.coerceIn(1, slots)
    return List(n) { i ->
        val from = firstAllowed + i * slots / n
        val until = firstAllowed + (i + 1) * slots / n
        start + random.nextInt(from, until) * MINUTE
    }
}

/**
 * One note or task.
 *
 * A reminder is either an exact time ([windowMinutes] = 0, pops up at [remindAt]) or a
 * time frame from [remindAt] lasting [windowMinutes], which pops up [popCount] times at
 * [randomTimes]: moments picked at random inside the frame. A pop-up counts as shown
 * once [lastNotifiedAt] has caught up with it; repeating reminders move forward to the
 * next frame after its last pop-up.
 */
data class Note(
    val id: Long,
    val text: String,
    val isTask: Boolean = false,
    val done: Boolean = false,
    val remindAt: Long? = null,
    val windowMinutes: Int = 0,
    val popCount: Int = 1,
    val randomTimes: List<Long> = emptyList(),
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

    val isTimeFrame: Boolean get() = remindAt != null && windowMinutes > 0

    /** End of the time frame, or null for an exact-time reminder. */
    val windowEnd: Long? get() = remindAt?.takeIf { windowMinutes > 0 }?.plus(windowMinutes * MINUTE)

    /** When the reminder pops up next (for a time frame, its next random moment). */
    val popAt: Long?
        get() {
            val start = remindAt ?: return null
            if (windowMinutes == 0 || randomTimes.isEmpty()) return start
            return randomTimes.firstOrNull { it > lastNotifiedAt } ?: randomTimes.last()
        }

    /** True while a reminder (or snooze) is still waiting to pop up. */
    val hasUpcomingReminder: Boolean get() = nextAlarmAt() != null

    /** When this note should pop up next, or null if nothing is waiting. */
    fun nextAlarmAt(): Long? {
        if (done) return null
        val reminder = popAt?.takeIf { it > lastNotifiedAt }
        return listOfNotNull(reminder, snoozeUntil).minOrNull()
    }

    /**
     * Lines up the reminder from scratch: caps the frame to what the repeat allows,
     * moves a repeating reminder whose time has gone to its next occurrence, and
     * picks the random moments inside a time frame.
     */
    fun planned(now: Long, random: Random = Random.Default): Note {
        val start = remindAt
            ?: return copy(windowMinutes = 0, popCount = 1, randomTimes = emptyList(), repeat = Repeat.NONE)
        val window = windowMinutes.coerceIn(0, repeat.maxWindowMinutes)
        val span = window * MINUTE
        val nextStart = if (repeat != Repeat.NONE && start + span <= now) repeat.nextAfter(start, now - span) else start
        val pops = popCount.coerceAtLeast(1)
        return copy(
            remindAt = nextStart,
            windowMinutes = window,
            popCount = pops,
            randomTimes = if (window > 0) pickRandomTimes(nextStart, window, pops, now, random) else emptyList(),
        )
    }

    /**
     * What happens when this note's alarm goes off at [now]: returns the updated
     * note and whether the pop-up should be shown.
     */
    fun fire(now: Long, random: Random = Random.Default): Pair<Note, Boolean> {
        if (done) return this to false
        var updated = this
        var due = false

        val snooze = snoozeUntil
        if (snooze != null && snooze <= now + FIRE_GRACE_MS) {
            updated = updated.copy(snoozeUntil = null)
            due = true
        }

        val start = remindAt
        val pop = popAt
        if (start != null && pop != null && pop > lastNotifiedAt && pop <= now + FIRE_GRACE_MS) {
            val firedAt = maxOf(now, pop)
            // Pop-ups missed while the phone was off are shown once, together.
            val moreInThisFrame = windowMinutes > 0 && randomTimes.any { it > firedAt }
            updated = if (moreInThisFrame || repeat == Repeat.NONE) {
                updated.copy(lastNotifiedAt = firedAt)
            } else {
                val nextStart = repeat.nextAfter(start, firedAt)
                updated.copy(
                    lastNotifiedAt = firedAt,
                    remindAt = nextStart,
                    randomTimes = if (windowMinutes > 0) {
                        pickRandomTimes(nextStart, windowMinutes, popCount, firedAt, random)
                    } else {
                        emptyList()
                    },
                )
            }
            due = true
        }
        return updated to due
    }

    /**
     * The note with its upcoming pop-up moved by [minutes] (a time frame moves as a
     * whole), or null if there is nothing to move or it would land in the past.
     */
    fun shifted(minutes: Int, now: Long): Note? {
        if (done) return null
        val delta = minutes * MINUTE
        val earliest = now + MINUTE

        val snooze = snoozeUntil
        if (snooze != null) {
            val moved = snooze + delta
            return if (moved < earliest) null else copy(snoozeUntil = moved)
        }

        val start = remindAt ?: return null
        val pop = popAt ?: return null
        if (pop <= lastNotifiedAt) return null
        val newStart = start + delta

        if (windowMinutes == 0) {
            return if (newStart < earliest) null else copy(remindAt = newStart)
        }
        val newEnd = newStart + windowMinutes * MINUTE
        if (newEnd < earliest) return null
        val pending = randomTimes.filter { it > lastNotifiedAt }.ifEmpty { listOf(pop) }
        val lowest = maxOf(newStart, earliest)
        return copy(
            remindAt = newStart,
            randomTimes = pending.map { (it + delta).coerceIn(lowest, newEnd) }.distinct(),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("task", isTask)
        put("done", done)
        remindAt?.let { put("remindAt", it) }
        put("windowMinutes", windowMinutes)
        put("popCount", popCount)
        put("randomTimes", JSONArray(randomTimes))
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
            windowMinutes = json.optInt("windowMinutes", 0),
            popCount = json.optInt("popCount", 1).coerceAtLeast(1),
            randomTimes = json.optJSONArray("randomTimes")?.let { times -> List(times.length()) { times.getLong(it) } }
                // Notes saved by version 0.2 had a single random moment.
                ?: if (json.has("randomAt")) listOf(json.getLong("randomAt")) else emptyList(),
            repeat = Repeat.entries.firstOrNull { it.name == json.optString("repeat") } ?: Repeat.NONE,
            snoozeUntil = if (json.has("snoozeUntil")) json.getLong("snoozeUntil") else null,
            lastNotifiedAt = json.optLong("lastNotifiedAt"),
            createdAt = json.optLong("createdAt"),
        )
    }
}
