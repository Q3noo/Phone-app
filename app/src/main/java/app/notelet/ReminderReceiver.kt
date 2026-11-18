package app.notelet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runs when a note's alarm goes off: shows the pop-up and lines up the next one. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val note = NoteStore.get(context, id) ?: return
        if (note.done) return

        val (updated, due) = note.fire(System.currentTimeMillis())
        NoteStore.put(context, updated)
        if (due) Notifications.showReminder(context, updated)
        ReminderScheduler.schedule(context, updated)
        NoteWidgetProvider.refreshAll(context)
    }

    companion object {
        const val ACTION_FIRE = "app.notelet.action.FIRE_REMINDER"
    }
}
