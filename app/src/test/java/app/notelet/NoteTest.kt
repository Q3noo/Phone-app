package app.notelet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NoteTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    /** Wednesday 18 Nov 2026, 09:00 local time. */
    private fun at(year: Int = 2026, month: Int = Calendar.NOVEMBER, date: Int = 18, h: Int = 9, m: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, date, h, m, 0)
        }.timeInMillis

    private fun dayOfWeek(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)

    @Test
    fun oneOffReminderFiresExactlyOnce() {
        val note = Note(id = 1, text = "Call mum", remindAt = at())
        assertEquals(at(), note.nextAlarmAt())

        val (afterFirst, firstDue) = note.fire(at())
        assertTrue(firstDue)
        assertNull("nothing left to show", afterFirst.nextAlarmAt())

        val (_, secondDue) = afterFirst.fire(at() + minute)
        assertFalse("must not pop up twice", secondDue)
    }

    @Test
    fun reminderInTheFutureIsNotShownEarly() {
        val note = Note(id = 1, text = "Later", remindAt = at() + hour)
        val (updated, due) = note.fire(at())
        assertFalse(due)
        assertEquals(note, updated)
    }

    @Test
    fun dailyReminderMovesToTomorrow() {
        val note = Note(id = 1, text = "Water plants", remindAt = at(), repeat = Repeat.DAILY)
        val (updated, due) = note.fire(at())
        assertTrue(due)
        assertEquals(at(date = 19), updated.remindAt)
        assertEquals(at(date = 19), updated.nextAlarmAt())
    }

    @Test
    fun alarmArrivingSlightlyEarlyStillAdvancesRepeat() {
        val note = Note(id = 1, text = "Stretch", remindAt = at(), repeat = Repeat.DAILY)
        val (updated, due) = note.fire(at() - 2_000L)
        assertTrue(due)
        assertEquals(at(date = 19), updated.nextAlarmAt())
    }

    @Test
    fun missedRepeatingReminderShowsOnceThenCatchesUp() {
        // Phone was off for three days.
        val note = Note(id = 1, text = "Vitamins", remindAt = at(date = 15), repeat = Repeat.DAILY)
        val (updated, due) = note.fire(at(h = 12))
        assertTrue(due)
        assertEquals(at(date = 19), updated.remindAt)
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val friday = at(date = 20)
        assertEquals(Calendar.FRIDAY, dayOfWeek(friday))
        val next = Repeat.WEEKDAYS.nextAfter(friday, friday)
        assertEquals(Calendar.MONDAY, dayOfWeek(next))
        assertEquals(at(date = 23), next)
    }

    @Test
    fun hourlyAndWeeklyStepCorrectly() {
        assertEquals(at() + hour, Repeat.HOURLY.nextAfter(at(), at()))
        assertEquals(at(date = 25), Repeat.WEEKLY.nextAfter(at(), at()))
    }

    @Test
    fun snoozeShowsAgainWithoutDisturbingTheRepeat() {
        val note = Note(
            id = 1,
            text = "Meds",
            remindAt = at(date = 19),
            repeat = Repeat.DAILY,
            snoozeUntil = at() + 10 * minute,
            lastNotifiedAt = at(),
        )
        assertEquals(at() + 10 * minute, note.nextAlarmAt())

        val (updated, due) = note.fire(at() + 10 * minute)
        assertTrue(due)
        assertNull(updated.snoozeUntil)
        assertEquals(at(date = 19), updated.nextAlarmAt())
    }

    @Test
    fun finishedTasksNeverPopUp() {
        val note = Note(id = 1, text = "Done already", isTask = true, done = true, remindAt = at(), snoozeUntil = at())
        assertNull(note.nextAlarmAt())
        assertFalse(note.fire(at()).second)
    }

    @Test
    fun firstLineIsTheTitle() {
        val note = Note(id = 1, text = "\n  Buy milk  \nsemi-skimmed\n2 litres\n")
        assertEquals("Buy milk", note.headline)
        assertEquals("semi-skimmed\n2 litres", note.details)
        assertEquals("", Note(id = 2, text = "Just one line").details)
    }

    @Test
    fun savedNotesComeBackUnchanged() {
        val note = Note(
            id = 7,
            text = "Pay rent\nbefore noon",
            isTask = true,
            done = false,
            remindAt = at(),
            repeat = Repeat.WEEKLY,
            snoozeUntil = at() + minute,
            lastNotifiedAt = at() - day,
            createdAt = at() - 2 * day,
        )
        assertEquals(note, Note.fromJson(note.toJson()))

        val plain = Note(id = 8, text = "No reminder", createdAt = at())
        assertEquals(plain, Note.fromJson(plain.toJson()))
    }
}
