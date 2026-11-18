package app.notelet

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Asks before the widget's Clear all button deletes everything. Only the dialog shows. */
class ConfirmClearActivity : NoteletActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notes = NoteStore.all(this)
        if (notes.isEmpty()) {
            finish()
            return
        }
        val done = notes.count { it.done }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(getString(R.string.clear_all_message, notes.size))
            .setPositiveButton(R.string.clear_all) { _, _ ->
                val removed = NoteActions.clearAll(this)
                Toast.makeText(applicationContext, getString(R.string.cleared, removed.size), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
        if (done > 0) {
            dialog.setNeutralButton(R.string.clear_only_done) { _, _ -> NoteActions.clearDone(this) }
        }
        dialog.setOnDismissListener { finish() }.show()
    }
}
