package app.notelet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

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

    // ---- Time frames: pop up at a random moment inside the frame ----

    private fun frame(fromHour: Int, hours: Int, repeat: Repeat = Repeat.NONE, date: Int = 18) =
        Note(id = 1, text = "Drink water", remindAt = at(date = date, h = fromHour), windowMinutes = hours * 60, repeat = repeat)

    @Test
    fun timeFramePopsUpSomewhereInsideIt() {
        val seen = mutableSetOf<Long>()
        for (seed in 0 until 200) {
            val note = frame(14, 2).planned(now = at(), random = Random(seed))
            val pop = note.popAt!!
            assertTrue(pop in at(h = 14)..at(h = 16))
            assertEquals("whole minutes only", 0L, pop % minute)
            assertEquals(pop, note.nextAlarmAt())
            seen += pop
        }
        assertTrue("should not always pick the same time", seen.size > 50)
    }

    @Test
    fun timeFrameThatHasAlreadyStartedPopsUpLaterInIt() {
        val now = at(h = 15)
        for (seed in 0 until 100) {
            val pop = frame(14, 2).planned(now, Random(seed)).popAt!!
            assertTrue(pop > now && pop <= at(h = 16))
        }
    }

    @Test
    fun timeFrameFiresOnceThenIsDone() {
        val note = frame(14, 2).planned(at(), Random(1))
        val (fired, due) = note.fire(note.popAt!!)
        assertTrue(due)
        assertNull(fired.nextAlarmAt())
        assertFalse(fired.fire(note.popAt!! + minute).second)
    }

    @Test
    fun dailyTimeFrameGetsAFreshRandomMomentEachDay() {
        val today = frame(14, 2, Repeat.DAILY).planned(at(), Random(3))
        val (tomorrow, due) = today.fire(today.popAt!!, Random(4))
        assertTrue(due)
        assertEquals(at(date = 19, h = 14), tomorrow.remindAt)
        assertTrue(tomorrow.popAt!! in at(date = 19, h = 14)..at(date = 19, h = 16))
        assertEquals(tomorrow.popAt, tomorrow.nextAlarmAt())
    }

    @Test
    fun repeatingTimeFrameThatEndedMovesToItsNextOccurrence() {
        val planned = frame(7, 1, Repeat.DAILY).planned(now = at(h = 12), random = Random(5))
        assertEquals(at(date = 19, h = 7), planned.remindAt)
    }

    @Test
    fun hourlyTimeFrameIsCappedAtOneHour() {
        val planned = frame(14, 3, Repeat.HOURLY).planned(at(), Random(6))
        assertEquals(60, planned.windowMinutes)
    }

    // ---- Moving a pop-up 15 minutes earlier or later ----

    @Test
    fun exactReminderMovesBy15Minutes() {
        val note = Note(id = 1, text = "Call", remindAt = at(h = 14))
        assertEquals(at(h = 14, m = 15), note.shifted(15, now = at())!!.nextAlarmAt())
        assertEquals(at(h = 13, m = 45), note.shifted(-15, now = at())!!.nextAlarmAt())
    }

    @Test
    fun reminderCannotBeMovedIntoThePast() {
        val note = Note(id = 1, text = "Soon", remindAt = at() + 10 * minute)
        assertNull(note.shifted(-15, now = at()))
        assertNull(Note(id = 2, text = "No reminder").shifted(15, at()))
    }

    @Test
    fun timeFrameMovesAsAWholeAndKeepsItsMoment() {
        val note = frame(14, 2).copy(randomAt = at(h = 15, m = 10))
        val later = note.shifted(15, now = at())!!
        assertEquals(at(h = 14, m = 15), later.remindAt)
        assertEquals(at(h = 15, m = 25), later.popAt)
        val earlier = note.shifted(-15, now = at())!!
        assertEquals(at(h = 13, m = 45), earlier.remindAt)
        assertEquals(at(h = 14, m = 55), earlier.popAt)
    }

    @Test
    fun movingAStartedFrameEarlierNeverPopsUpInThePast() {
        val now = at(h = 15)
        val note = frame(14, 2).copy(randomAt = at(h = 15, m = 5))
        val earlier = note.shifted(-15, now)!!
        assertTrue(earlier.popAt!! > now)
        assertTrue(earlier.popAt!! <= earlier.windowEnd!!)
    }

    @Test
    fun snoozedPopUpIsWhatMoves() {
        val note = Note(id = 1, text = "Meds", remindAt = at(date = 19), repeat = Repeat.DAILY, snoozeUntil = at() + 10 * minute, lastNotifiedAt = at())
        val later = note.shifted(15, now = at())!!
        assertEquals(at() + 25 * minute, later.snoozeUntil)
        assertEquals(note.remindAt, later.remindAt)
    }

    @Test
    fun savedNotesComeBackUnchanged() {
        val note = Note(
            id = 7,
            text = "Pay rent\nbefore noon",
            isTask = true,
            done = false,
            remindAt = at(),
            windowMinutes = 90,
            randomAt = at() + 30 * minute,
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
