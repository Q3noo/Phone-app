package app.notelet

import android.content.Context

/** Every change to a note goes through here, so alarms, pop-ups and the widget stay in sync. */
object NoteActions {

    fun save(context: Context, note: Note) {
        val now = System.currentTimeMillis()
        val pop = note.popAt
        // A repeating reminder whose moment slipped by (e.g. while the task was done)
        // picks up again at its next occurrence instead of firing immediately.
        val normalized = if (!note.done && note.repeat != Repeat.NONE && pop != null && pop <= now) {
            note.planned(now)
        } else {
            note
        }
        NoteStore.put(context, normalized)
        ReminderScheduler.schedule(context, normalized)
        if (normalized.done) Notifications.cancel(context, normalized.id)
        NoteWidgetProvider.refreshAll(context)
    }

    fun delete(context: Context, id: Long) {
        NoteStore.remove(context, id)
        ReminderScheduler.cancel(context, id)
        Notifications.cancel(context, id)
        NoteWidgetProvider.refreshAll(context)
    }

    fun setDone(context: Context, id: Long, done: Boolean) {
        val note = NoteStore.get(context, id) ?: return
        save(context, note.copy(done = done, snoozeUntil = if (done) null else note.snoozeUntil))
    }

    fun toggleDone(context: Context, id: Long) {
        val note = NoteStore.get(context, id) ?: return
        setDone(context, id, !note.done)
    }

    fun snooze(context: Context, id: Long, minutes: Int) {
        val note = NoteStore.get(context, id) ?: return
        save(context, note.copy(snoozeUntil = System.currentTimeMillis() + minutes * MINUTE))
        Notifications.cancel(context, id)
    }

    /**
     * Adds an update to a task: the current step is crossed off and [text] becomes the
     * next step, with its own pop-up time. Returns the updated task.
     */
    fun addUpdate(
        context: Context,
        id: Long,
        text: String,
        remindAt: Long?,
        windowMinutes: Int,
        popCount: Int,
        repeat: Repeat,
    ): Note? {
        val note = NoteStore.get(context, id) ?: return null
        val updated = note.withUpdate(text, remindAt, windowMinutes, popCount, repeat, System.currentTimeMillis())
        Notifications.cancel(context, id) // the old step's pop-up no longer applies
        save(context, updated)
        return updated
    }

    /** Moves the upcoming pop-up by [minutes]. Returns false if it can't be moved. */
    fun shift(context: Context, id: Long, minutes: Int): Boolean {
        val note = NoteStore.get(context, id) ?: return false
        val moved = note.shifted(minutes, System.currentTimeMillis()) ?: return false
        save(context, moved)
        return true
    }

    /** Removes finished tasks. Returns them so the change can be undone. */
    fun clearDone(context: Context): List<Note> = clear(context) { it.done }

    /** Removes everything. Returns it all so the change can be undone. */
    fun clearAll(context: Context): List<Note> = clear(context) { true }

    /** Puts back notes removed by [clearDone] or [clearAll]. */
    fun restore(context: Context, notes: List<Note>) {
        if (notes.isEmpty()) return
        NoteStore.putAll(context, notes)
        notes.forEach { ReminderScheduler.schedule(context, it) }
        NoteWidgetProvider.refreshAll(context)
    }

    private fun clear(context: Context, predicate: (Note) -> Boolean): List<Note> {
        val removed = NoteStore.removeWhere(context, predicate)
        removed.forEach {
            ReminderScheduler.cancel(context, it.id)
            Notifications.cancel(context, it.id)
        }
        NoteWidgetProvider.refreshAll(context)
        return removed
    }
}
