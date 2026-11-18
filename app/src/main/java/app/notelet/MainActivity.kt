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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

/** The full list of notes and tasks, for when the widget isn't enough. */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: NotesAdapter
    private lateinit var emptyView: View
    private var notificationsWarning: Snackbar? = null

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateNotificationWarning() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = NotesAdapter(
            onOpen = { note -> startActivity(EditNoteActivity.editIntent(this, note.id)) },
            onToggleDone = { note, done ->
                NoteActions.setDone(this, note.id, done)
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
        val notes = NoteStore.sortedForDisplay(NoteStore.all(this))
        adapter.submit(notes)
        emptyView.isVisible = notes.isEmpty()
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
