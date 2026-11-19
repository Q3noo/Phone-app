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

    // ---- Several pop-ups spread randomly across one frame ----

    @Test
    fun severalPopUpsAreSpreadAcrossTheWholeFrame() {
        for (seed in 0 until 200) {
            val times = frame(14, 3).copy(popCount = 3).planned(at(), Random(seed)).randomTimes
            assertEquals(3, times.size)
            assertEquals("in order, never two at once", times.sorted().distinct(), times)
            // One random moment in each hour of the 14:00-17:00 frame.
            assertTrue(times[0] in at(h = 14) until at(h = 15))
            assertTrue(times[1] in at(h = 15) until at(h = 16))
            assertTrue(times[2] in at(h = 16)..at(h = 17))
        }
    }

    @Test
    fun theMomentsChangeFromFrameToFrame() {
        val firsts = (0 until 50).map { frame(14, 3).copy(popCount = 3).planned(at(), Random(it)).randomTimes.first() }
        assertTrue(firsts.toSet().size > 20)
    }

    @Test
    fun eachPopUpInTheFrameShowsThenTheFrameIsDone() {
        var note = frame(14, 3).copy(popCount = 3).planned(at(), Random(7))
        val times = note.randomTimes
        for (time in times) {
            assertEquals(time, note.nextAlarmAt())
            val (next, due) = note.fire(time)
            assertTrue(due)
            note = next
        }
        assertNull("all three shown", note.nextAlarmAt())
    }

    @Test
    fun repeatingFrameMovesToTomorrowAfterItsLastPopUp() {
        var note = frame(14, 2, Repeat.DAILY).copy(popCount = 2).planned(at(), Random(8))
        val (afterFirst, _) = note.fire(note.randomTimes[0])
        assertEquals("still today's frame", at(h = 14), afterFirst.remindAt)
        assertEquals(note.randomTimes[1], afterFirst.nextAlarmAt())
        note = afterFirst.fire(note.randomTimes[1], Random(9)).first
        assertEquals(at(date = 19, h = 14), note.remindAt)
        assertEquals(2, note.randomTimes.size)
        assertTrue(note.randomTimes.all { it in at(date = 19, h = 14)..at(date = 19, h = 16) })
    }

    @Test
    fun popUpsMissedWhilePhoneWasOffShowOnce() {
        val note = frame(14, 3).copy(popCount = 3).planned(at(), Random(10))
        val (after, due) = note.fire(note.randomTimes[1] + minute) // the first two were missed
        assertTrue(due)
        assertEquals(note.randomTimes[2], after.nextAlarmAt())
    }

    @Test
    fun cannotAskForMorePopUpsThanMinutes() {
        val times = frame(14, 0).copy(windowMinutes = 2, popCount = 5).planned(at(), Random(11)).randomTimes
        assertEquals(listOf(at(h = 14), at(h = 14, m = 1), at(h = 14, m = 2)), times)
    }

    @Test
    fun movingAFrameMovesAllItsPopUps() {
        val note = frame(14, 3).copy(popCount = 2, randomTimes = listOf(at(h = 14, m = 30), at(h = 16)))
        val later = note.shifted(15, now = at())!!
        assertEquals(listOf(at(h = 14, m = 45), at(h = 16, m = 15)), later.randomTimes)
    }

    // ---- Updates: a task's next step ----

    @Test
    fun anUpdateCrossesOffTheOldStepAndStartsTheNextOne() {
        val task = Note(id = 1, text = "Call the plumber", isTask = true, remindAt = at(h = 10))
        val updated = task.withUpdate(
            "Plumber comes Thursday\nleave the gate open", remindAt = at(date = 19, h = 9),
            windowMinutes = 180, popCount = 2, repeat = Repeat.NONE, now = at(h = 11), random = Random(1),
        )
        assertEquals(listOf(Step("Call the plumber", at(h = 10), 0, doneAt = at(h = 11))), updated.steps)
        assertEquals("Plumber comes Thursday", updated.headline)
        assertFalse(updated.done)
        assertEquals(2, updated.randomTimes.size)
        assertTrue(updated.randomTimes.all { it in at(date = 19, h = 9)..at(date = 19, h = 12) })
        assertEquals(updated.randomTimes.first(), updated.nextAlarmAt())
    }

    @Test
    fun updatesPileUpInOrder() {
        var task = Note(id = 1, text = "Step one", isTask = true)
        task = task.withUpdate("Step two", null, 0, 1, Repeat.NONE, now = at())
        task = task.withUpdate("Step three", null, 0, 1, Repeat.NONE, now = at() + hour)
        assertEquals(listOf("Step one", "Step two"), task.steps.map { it.text })
        assertEquals("Step three", task.text)
        assertNull("no time given, no pop-up", task.nextAlarmAt())
    }

    @Test
    fun updatingAFinishedTaskOpensItAgain() {
        val done = Note(id = 1, text = "Send the form", isTask = true, done = true)
        val updated = done.withUpdate("They want a signature too", at(h = 15), 0, 1, Repeat.NONE, now = at())
        assertFalse(updated.done)
        assertEquals(at(h = 15), updated.nextAlarmAt())
    }

    @Test
    fun aRepeatingStepIsKeptWithoutItsNextTime() {
        val daily = Note(id = 1, text = "Water plants", isTask = true, remindAt = at(date = 19), repeat = Repeat.DAILY)
        val updated = daily.withUpdate("Plants moved outside", null, 0, 1, Repeat.NONE, now = at())
        assertNull(updated.steps.single().remindAt)
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
        val note = frame(14, 2).copy(randomTimes = listOf(at(h = 15, m = 10)))
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
        val note = frame(14, 2).copy(randomTimes = listOf(at(h = 15, m = 5)))
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
            popCount = 2,
            randomTimes = listOf(at() + 30 * minute, at() + 70 * minute),
            repeat = Repeat.WEEKLY,
            snoozeUntil = at() + minute,
            lastNotifiedAt = at() - day,
            createdAt = at() - 2 * day,
            steps = listOf(
                Step("Ask landlord", remindAt = at() - 2 * day, windowMinutes = 60, doneAt = at() - day),
                Step("Find bank details", doneAt = at() - hour),
            ),
        )
        assertEquals(note, Note.fromJson(note.toJson()))

        val plain = Note(id = 8, text = "No reminder", createdAt = at())
        assertEquals(plain, Note.fromJson(plain.toJson()))

        // A time frame saved by version 0.2, which had a single "randomAt" moment.
        val old = org.json.JSONObject(
            """{"id":9,"text":"Old","remindAt":${at()},"windowMinutes":60,"randomAt":${at() + 5 * minute},"repeat":"NONE"}"""
        )
        val loaded = Note.fromJson(old)
        assertEquals(listOf(at() + 5 * minute), loaded.randomTimes)
        assertEquals(1, loaded.popCount)
        assertEquals(at() + 5 * minute, loaded.nextAlarmAt())
    }
}
