package app.notelet

import android.Manifest
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * The note editor. It is a small floating window, so when it is opened from the
 * widget it appears right over the home screen.
 */
class EditNoteActivity : NoteletActivity() {

    private var existing: Note? = null

    /** Exact pop-up time, or the start of the time frame. */
    private var remindAt: Long? = null

    /** Length of the time frame (used when "Time frame" is selected). */
    private var windowMinutes = DEFAULT_WINDOW_MINUTES

    private lateinit var heading: TextView
    private lateinit var typeGroup: MaterialButtonToggleGroup
    private lateinit var textLayout: TextInputLayout
    private lateinit var textInput: TextInputEditText
    private lateinit var doneCheck: MaterialCheckBox
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var modeHint: TextView
    private lateinit var exactChips: View
    private lateinit var frameChips: View
    private lateinit var reminderLabel: TextView
    private lateinit var clearReminder: Button
    private lateinit var repeatSection: View
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
        modeGroup = findViewById(R.id.mode_group)
        modeHint = findViewById(R.id.mode_hint)
        exactChips = findViewById(R.id.exact_chips)
        frameChips = findViewById(R.id.frame_chips)
        reminderLabel = findViewById(R.id.reminder_label)
        clearReminder = findViewById(R.id.clear_reminder)
        repeatSection = findViewById(R.id.repeat_section)
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
            windowMinutes = savedInstanceState.getInt(STATE_WINDOW, DEFAULT_WINDOW_MINUTES)
        } else if (note != null) {
            textInput.setText(note.text)
            typeGroup.check(if (note.isTask) R.id.type_task else R.id.type_note)
            doneCheck.isChecked = note.done
            remindAt = note.remindAt
            if (note.windowMinutes > 0) {
                windowMinutes = note.windowMinutes
                modeGroup.check(R.id.mode_frame)
            }
            repeatGroup.check(chipFor(note.repeat))
        } else {
            typeGroup.check(if (NoteStore.lastWasTask(this)) R.id.type_task else R.id.type_note)
            textInput.requestFocus()
            @Suppress("DEPRECATION")
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        }

        typeGroup.addOnButtonCheckedListener { _, _, _ -> updateTypeUi() }
        modeGroup.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) updateReminderUi() }
        repeatGroup.setOnCheckedStateChangeListener { _, _ -> updateReminderUi() }
        textInput.doAfterTextChanged { textLayout.error = null }

        // Exact-time quick picks
        findViewById<View>(R.id.chip_15m).setOnClickListener { setReminder(minutesFromNow(15)) }
        findViewById<View>(R.id.chip_1h).setOnClickListener { setReminder(minutesFromNow(60)) }
        findViewById<Chip>(R.id.chip_tomorrow).apply {
            text = getString(R.string.tomorrow_at, Format.timeText(context, dayAt(1, 9)))
            setOnClickListener { setReminder(dayAt(1, 9)) }
        }
        findViewById<View>(R.id.chip_pick).setOnClickListener { pickExact() }

        // Time-frame quick picks
        findViewById<View>(R.id.chip_next_hour).setOnClickListener { setReminder(minutesFromNow(0), 60) }
        findViewById<View>(R.id.chip_next_3h).setOnClickListener { setReminder(minutesFromNow(0), 180) }
        findViewById<View>(R.id.chip_evening).setOnClickListener {
            val today = dayAt(0, 18)
            setReminder(if (System.currentTimeMillis() < today + 3 * 60 * MINUTE) today else dayAt(1, 18), 180)
        }
        findViewById<View>(R.id.chip_morning).setOnClickListener { setReminder(dayAt(1, 9), 180) }
        findViewById<View>(R.id.chip_pick_frame).setOnClickListener { pickFrame() }

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
        outState.putInt(STATE_WINDOW, windowMinutes)
    }

    private fun isTask() = typeGroup.checkedButtonId == R.id.type_task
    private fun isFrame() = modeGroup.checkedButtonId == R.id.mode_frame

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
        val frame = isFrame()
        exactChips.isVisible = !frame
        frameChips.isVisible = frame
        modeHint.isVisible = frame

        val at = remindAt
        clearReminder.isVisible = at != null
        repeatSection.isVisible = at != null
        if (at == null) {
            reminderLabel.setText(R.string.no_reminder)
            return
        }
        val text = if (frame) {
            getString(R.string.frame_at, Format.frameText(this, at, windowMinutes))
        } else {
            getString(R.string.reminder_at, Format.whenText(this, at))
        }
        val end = if (frame) at + windowMinutes * MINUTE else at
        reminderLabel.text = if (end <= System.currentTimeMillis() && selectedRepeat() == Repeat.NONE) {
            getString(R.string.reminder_passed, text)
        } else {
            text
        }
    }

    /** Sets an exact time, or with [window] a time frame starting at [at]. */
    private fun setReminder(at: Long, window: Int? = null) {
        remindAt = at
        if (window != null) windowMinutes = window
        updateReminderUi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun pickExact() {
        val initial = remindAt?.takeIf { it > System.currentTimeMillis() } ?: nextWholeHour()
        pickDate(initial) { day ->
            pickTime(R.string.pick_time_title, initial) { hour, minute ->
                setReminder(day.atTime(hour, minute))
            }
        }
    }

    private fun pickFrame() {
        val initial = remindAt?.takeIf { it + windowMinutes * MINUTE > System.currentTimeMillis() } ?: nextWholeHour()
        pickDate(initial) { day ->
            pickTime(R.string.from, initial) { fromHour, fromMinute ->
                val start = day.atTime(fromHour, fromMinute)
                pickTime(R.string.until, start + windowMinutes * MINUTE) { toHour, toMinute ->
                    var end = day.atTime(toHour, toMinute)
                    if (end <= start) end += 24 * 60 * MINUTE // e.g. 22:00 until 01:00
                    setReminder(start, ((end - start) / MINUTE).toInt().coerceIn(1, 24 * 60))
                }
            }
        }
    }

    private fun pickDate(initial: Long, onPicked: (Calendar) -> Unit) {
        val local = Calendar.getInstance().apply { timeInMillis = initial }
        val utcSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_day)
            .setSelection(utcSelection)
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection }
            onPicked(Calendar.getInstance().apply {
                clear()
                set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
            })
        }
        picker.show(supportFragmentManager, "date")
    }

    private fun pickTime(titleRes: Int, initial: Long, onPicked: (Int, Int) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTitleText(titleRes)
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(picker.hour, picker.minute) }
        picker.show(supportFragmentManager, "time_$titleRes")
    }

    private fun save() {
        val text = textInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            textLayout.error = getString(R.string.error_empty)
            return
        }

        val now = System.currentTimeMillis()
        val old = existing
        val start = remindAt
        val window = if (start != null && isFrame()) windowMinutes else 0
        val repeat = if (start == null) Repeat.NONE else selectedRepeat()
        val reminderChanged = old == null || old.remindAt != start ||
            old.windowMinutes != window || old.repeat != repeat

        if (start != null && reminderChanged && repeat == Repeat.NONE && start + window * MINUTE <= now) {
            Toast.makeText(this, R.string.error_past, Toast.LENGTH_LONG).show()
            return
        }

        val task = isTask()
        val done = task && doneCheck.isChecked
        var note = Note(
            id = old?.id ?: NoteStore.newId(this),
            text = text,
            isTask = task,
            done = done,
            remindAt = start,
            windowMinutes = window,
            randomAt = if (reminderChanged) null else old?.randomAt,
            repeat = repeat,
            snoozeUntil = if (reminderChanged || done) null else old?.snoozeUntil,
            lastNotifiedAt = old?.lastNotifiedAt ?: 0L,
            createdAt = old?.createdAt ?: now,
        )
        if (reminderChanged) note = note.planned(now)

        NoteActions.save(this, note)
        NoteStore.setLastWasTask(this, task)

        if (reminderChanged && note.hasUpcomingReminder) {
            val begin = note.remindAt!!
            val message = if (note.windowMinutes > 0) {
                getString(R.string.frame_set, Format.frameText(this, begin, note.windowMinutes))
            } else {
                getString(R.string.reminder_set, Format.whenText(this, begin))
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    private fun minutesFromNow(minutes: Int): Long =
        (System.currentTimeMillis() + minutes * MINUTE + MINUTE - 1) / MINUTE * MINUTE

    private fun nextWholeHour(): Long = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** [hour]:00 today (days = 0) or a number of days from now. */
    private fun dayAt(days: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun Calendar.atTime(hour: Int, minute: Int): Long = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val STATE_REMIND_AT = "remind_at"
        private const val STATE_WINDOW = "window"
        private const val DEFAULT_WINDOW_MINUTES = 60

        fun newIntent(context: Context): Intent = Intent(context, EditNoteActivity::class.java)

        fun editIntent(context: Context, id: Long): Intent =
            Intent(context, EditNoteActivity::class.java)
                .setData(noteUri(id))
                .putExtra(EXTRA_NOTE_ID, id)
    }
}
