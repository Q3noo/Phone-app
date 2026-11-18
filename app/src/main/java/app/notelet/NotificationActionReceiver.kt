package app.notelet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Done and Snooze buttons on a pop-up. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_DONE -> NoteActions.setDone(context, id, true)
            ACTION_SNOOZE -> NoteActions.snooze(context, id, Notifications.SNOOZE_MINUTES)
        }
        Notifications.cancel(context, id)
    }

    companion object {
        const val ACTION_DONE = "app.notelet.action.DONE"
        const val ACTION_SNOOZE = "app.notelet.action.SNOOZE"
    }
}
