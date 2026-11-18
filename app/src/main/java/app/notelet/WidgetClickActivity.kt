package app.notelet

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Widget cards can only share one click target, so taps land here first.
 * It has no screen: it ticks a task off, moves its pop-up, or opens the editor, then closes.
 */
class WidgetClickActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (id > 0) {
            when (intent.getStringExtra(EXTRA_ACTION)) {
                ACTION_TOGGLE -> NoteActions.toggleDone(this, id)
                ACTION_SHIFT -> {
                    val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                    if (minutes != 0 && !NoteActions.shift(this, id, minutes)) {
                        Toast.makeText(applicationContext, R.string.cant_move, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> startActivity(EditNoteActivity.editIntent(this, id))
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "app.notelet.extra.WIDGET_ACTION"
        const val EXTRA_MINUTES = "app.notelet.extra.MINUTES"
        const val ACTION_OPEN = "open"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_SHIFT = "shift"
    }
}
