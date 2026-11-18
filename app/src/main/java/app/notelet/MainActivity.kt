package app.notelet

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** The full list of notes and tasks, for when the widget isn't enough. */
class MainActivity : NoteletActivity() {

    private lateinit var adapter: NotesAdapter
    private lateinit var emptyView: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomBar: BottomAppBar
    private var notificationsWarning: Snackbar? = null

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateNotificationWarning() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        adapter = NotesAdapter(
            onOpen = { note -> startActivity(EditNoteActivity.editIntent(this, note.id)) },
            onToggleDone = { note ->
                NoteActions.toggleDone(this, note.id)
                reload()
            },
            onShift = { note, minutes ->
                if (!NoteActions.shift(this, note.id, minutes)) {
                    Toast.makeText(this, R.string.cant_move, Toast.LENGTH_SHORT).show()
                }
                reload()
            },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        emptyView = findViewById(R.id.empty)
        findViewById<View>(R.id.fab).setOnClickListener { startActivity(EditNoteActivity.newIntent(this)) }
        findViewById<View>(R.id.add_widget).setOnClickListener { pinWidget() }

        bottomBar = findViewById(R.id.bottom_bar)
        bottomBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear_done -> {
                    clearDone()
                    true
                }
                R.id.action_clear_all -> {
                    confirmClearAll()
                    true
                }
                else -> false
            }
        }

        // Safety net: Android drops an app's alarms if it is force-stopped.
        ReminderScheduler.rescheduleAll(this)

        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
        updateNotificationWarning()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_add_widget -> {
            pinWidget()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun reload() {
        val all = NoteStore.all(this)
        adapter.submit(NoteStore.sortedForDisplay(all))
        emptyView.isVisible = all.isEmpty()

        val open = all.count { !it.done }
        val done = all.count { it.done }
        toolbar.subtitle = when {
            all.isEmpty() -> null
            done > 0 -> getString(R.string.count_open_done, open, done)
            else -> getString(R.string.count_open, open)
        }
        bottomBar.menu.findItem(R.id.action_clear_done)?.isEnabled = done > 0
        bottomBar.menu.findItem(R.id.action_clear_all)?.isEnabled = all.isNotEmpty()
    }

    private fun clearDone() {
        val removed = NoteActions.clearDone(this)
        reload()
        if (removed.isEmpty()) {
            Toast.makeText(this, R.string.nothing_done, Toast.LENGTH_SHORT).show()
        } else {
            offerUndo(getString(R.string.cleared_done, removed.size), removed)
        }
    }

    private fun confirmClearAll() {
        val count = NoteStore.all(this).size
        if (count == 0) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(getString(R.string.clear_all_message, count))
            .setPositiveButton(R.string.clear_all) { _, _ ->
                val removed = NoteActions.clearAll(this)
                reload()
                offerUndo(getString(R.string.cleared, removed.size), removed)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun offerUndo(message: String, removed: List<Note>) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.fab)
            .setAction(R.string.undo) {
                NoteActions.restore(this, removed)
                reload()
            }
            .show()
    }

    private fun updateNotificationWarning() {
        if (Notifications.canPost(this)) {
            notificationsWarning?.dismiss()
            notificationsWarning = null
            return
        }
        if (notificationsWarning?.isShownOrQueued == true) return
        notificationsWarning = Snackbar.make(findViewById(R.id.root), R.string.notifications_off, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(R.id.fab)
            .setAction(R.string.turn_on) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
            .also { it.show() }
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, NoteWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, R.string.add_widget_manually, Toast.LENGTH_LONG).show()
        }
    }
}
