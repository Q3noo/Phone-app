package app.notelet

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

/**
 * The note editor. It is a small floating window, so when it is opened from the
 * widget it appears right over the home screen.
 */
class EditNoteActivity : AppCompatActivity() {

    private var existing: Note? = null
    private var remindAt: Long? = null

    private lateinit var heading: TextView
    private lateinit var typeGroup: MaterialButtonToggleGroup
    private lateinit var textLayout: TextInputLayout
    private lateinit var textInput: TextInputEditText
    private lateinit var doneCheck: MaterialCheckBox
    private lateinit var reminderLabel: TextView
    private lateinit var clearReminder: Button
    private lateinit var repeatScroll: View
    private lateinit var repeatGroup: ChipGroup

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, R.string.notifications_denied, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_note)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        heading = findViewById(R.id.heading)
        typeGroup = findViewById(R.id.type_group)
        textLayout = findViewById(R.id.text_layout)
        textInput = findViewById(R.id.text_input)
        doneCheck = findViewById(R.id.done_check)
        reminderLabel = findViewById(R.id.reminder_label)
        clearReminder = findViewById(R.id.clear_reminder)
        repeatScroll = findViewById(R.id.repeat_scroll)
        repeatGroup = findViewById(R.id.repeat_group)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId > 0) {
            existing = NoteStore.get(this, noteId)
            if (existing == null) {
                Toast.makeText(this, R.string.note_missing, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        val note = existing

        if (savedInstanceState != null) {
            remindAt = savedInstanceState.getLong(STATE_REMIND_AT, -1L).takeIf { it > 0 }
        } else if (note != null) {
            textInput.setText(note.text)
            typeGroup.check(if (note.isTask) R.id.type_task else R.id.type_note)
            doneCheck.isChecked = note.done
            remindAt = note.remindAt
            repeatGroup.check(chipFor(note.repeat))
        } else {
            typeGroup.check(if (NoteStore.lastWasTask(this)) R.id.type_task else R.id.type_note)
            textInput.requestFocus()
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        }

        typeGroup.addOnButtonCheckedListener { _, _, _ -> updateTypeUi() }
        repeatGroup.setOnCheckedStateChangeListener { _, _ -> updateReminderUi() }
        textInput.doAfterTextChanged { textLayout.error = null }

        findViewById<View>(R.id.chip_15m).setOnClickListener { setReminder(minutesFromNow(15)) }
        findViewById<View>(R.id.chip_1h).setOnClickListener { setReminder(minutesFromNow(60)) }
        findViewById<Chip>(R.id.chip_tomorrow).apply {
            val tomorrowNine = tomorrowAt(9)
            text = getString(R.string.tomorrow_at, DateFormat.getTimeFormat(context).format(tomorrowNine))
            setOnClickListener { setReminder(tomorrowAt(9)) }
        }
        findViewById<View>(R.id.chip_pick).setOnClickListener { pickDateTime() }
        clearReminder.setOnClickListener {
            remindAt = null
            updateReminderUi()
        }

        findViewById<View>(R.id.cancel).setOnClickListener { finish() }
        findViewById<View>(R.id.save).setOnClickListener { save() }
        findViewById<View>(R.id.delete).apply {
            isVisible = note != null
            setOnClickListener { confirmDelete() }
        }

        updateTypeUi()
        updateReminderUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_REMIND_AT, remindAt ?: -1L)
    }

    private fun isTask() = typeGroup.checkedButtonId == R.id.type_task

    private fun updateTypeUi() {
        val task = isTask()
        doneCheck.isVisible = task && existing != null
        heading.setText(
            when {
                existing != null && task -> R.string.edit_task
                existing != null -> R.string.edit_note
                task -> R.string.new_task
                else -> R.string.new_note
            }
        )
        textLayout.hint = getString(if (task) R.string.hint_task else R.string.hint_note)
    }

    private fun updateReminderUi() {
        val at = remindAt
        clearReminder.isVisible = at != null
        repeatScroll.isVisible = at != null
        reminderLabel.text = when {
            at == null -> getString(R.string.no_reminder)
            at <= System.currentTimeMillis() && selectedRepeat() == Repeat.NONE ->
                getString(R.string.reminder_passed, Format.whenText(this, at))
            else -> getString(R.string.reminder_at, Format.whenText(this, at))
        }
    }

    private fun setReminder(at: Long) {
        remindAt = at
        updateReminderUi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun pickDateTime() {
        val cal = Calendar.getInstance()
        val current = remindAt
        if (current != null && current > System.currentTimeMillis()) {
            cal.timeInMillis = current
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
        }
        val dateDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day)
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        setReminder(cal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(this),
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        )
        dateDialog.datePicker.minDate = System.currentTimeMillis() - 1_000L
        dateDialog.show()
    }

    private fun save() {
        val text = textInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            textLayout.error = getString(R.string.error_empty)
            return
        }

        val now = System.currentTimeMillis()
        val old = existing
        val repeat = selectedRepeat()
        var at = remindAt
        val reminderChanged = old == null || old.remindAt != at || old.repeat != repeat
        if (at != null && reminderChanged && repeat == Repeat.NONE && at <= now) {
            Toast.makeText(this, R.string.error_past, Toast.LENGTH_LONG).show()
            return
        }
        if (at != null && repeat != Repeat.NONE && at <= now) at = repeat.nextAfter(at, now)

        val task = isTask()
        val done = task && doneCheck.isChecked
        val note = Note(
            id = old?.id ?: NoteStore.newId(this),
            text = text,
            isTask = task,
            done = done,
            remindAt = at,
            repeat = if (at == null) Repeat.NONE else repeat,
            snoozeUntil = if (reminderChanged || done) null else old?.snoozeUntil,
            lastNotifiedAt = old?.lastNotifiedAt ?: 0L,
            createdAt = old?.createdAt ?: now,
        )
        NoteActions.save(this, note)
        NoteStore.setLastWasTask(this, task)

        if (reminderChanged) {
            note.nextAlarmAt()?.let {
                Toast.makeText(this, getString(R.string.reminder_set, Format.whenText(this, it)), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun confirmDelete() {
        val note = existing ?: return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                NoteActions.delete(this, note.id)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectedRepeat(): Repeat = when (repeatGroup.checkedChipId) {
        R.id.repeat_hourly -> Repeat.HOURLY
        R.id.repeat_daily -> Repeat.DAILY
        R.id.repeat_weekdays -> Repeat.WEEKDAYS
        R.id.repeat_weekly -> Repeat.WEEKLY
        else -> Repeat.NONE
    }

    private fun chipFor(repeat: Repeat): Int = when (repeat) {
        Repeat.NONE -> R.id.repeat_none
        Repeat.HOURLY -> R.id.repeat_hourly
        Repeat.DAILY -> R.id.repeat_daily
        Repeat.WEEKDAYS -> R.id.repeat_weekdays
        Repeat.WEEKLY -> R.id.repeat_weekly
    }

    private fun minutesFromNow(minutes: Int): Long {
        val minute = 60_000L
        return (System.currentTimeMillis() + minutes * minute) / minute * minute
    }

    private fun tomorrowAt(hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val STATE_REMIND_AT = "remind_at"

        fun newIntent(context: Context): Intent = Intent(context, EditNoteActivity::class.java)

        fun editIntent(context: Context, id: Long): Intent =
            Intent(context, EditNoteActivity::class.java)
                .setData(noteUri(id))
                .putExtra(EXTRA_NOTE_ID, id)
    }
}
