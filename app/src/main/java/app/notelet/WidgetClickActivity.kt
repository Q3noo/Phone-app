package app.notelet

import android.app.Activity
import android.os.Bundle

/**
 * Widget list rows can only share one click target, so taps land here first.
 * It has no screen: it either ticks the task off or opens the editor, then closes.
 */
class WidgetClickActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (id > 0) {
            when (intent.getStringExtra(EXTRA_ACTION)) {
                ACTION_TOGGLE -> NoteActions.toggleDone(this, id)
                else -> startActivity(EditNoteActivity.editIntent(this, id))
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "app.notelet.extra.WIDGET_ACTION"
        const val ACTION_OPEN = "open"
        const val ACTION_TOGGLE = "toggle"
    }
}
