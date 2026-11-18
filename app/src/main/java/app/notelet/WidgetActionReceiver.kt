package app.notelet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the widget's Clear done button. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CLEAR_DONE) NoteActions.clearDone(context)
    }

    companion object {
        const val ACTION_CLEAR_DONE = "app.notelet.action.CLEAR_DONE"
    }
}
