package app.notelet

import android.content.Context

/** Every change to a note goes through here, so alarms, pop-ups and the widget stay in sync. */
object NoteActions {

    fun save(context: Context, note: Note) {
        val now = System.currentTimeMillis()
        val remindAt = note.remindAt
        // A repeating reminder whose time slipped by (e.g. while the task was done)
        // picks up again at its next occurrence instead of firing immediately.
        val normalized = if (!note.done && note.repeat != Repeat.NONE && remindAt != null && remindAt <= now) {
            note.copy(remindAt = note.repeat.nextAfter(remindAt, now))
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
        save(context, note.copy(snoozeUntil = System.currentTimeMillis() + minutes * 60_000L))
        Notifications.cancel(context, id)
    }
}
