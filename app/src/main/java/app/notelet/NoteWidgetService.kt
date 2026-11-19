package app.notelet

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Supplies the widget's note cards. Open and finished items use separate layouts, so
 * all colours come from the layouts' day/night resources and never get stuck in the
 * wrong mode.
 */
class NoteWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var notes: List<Note> = emptyList()

        override fun onCreate() = Unit
        override fun onDestroy() = Unit

        override fun onDataSetChanged() {
            notes = NoteStore.sortedForDisplay(NoteStore.all(context))
        }

        override fun getCount(): Int = notes.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 2
        override fun getItemId(position: Int): Long = notes.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds(): Boolean = true

        override fun getViewAt(position: Int): RemoteViews {
            val note = notes.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.widget_note_item)
            return if (note.done) doneCard(note) else openCard(note)
        }

        private fun openCard(note: Note): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note_item)
            views.setTextViewText(R.id.item_title, note.headline)
            setTextOrHide(views, R.id.item_details, note.details.ifBlank { null })

            // Earlier steps, crossed out and joined by a line down to this one.
            views.removeAllViews(R.id.item_history)
            if (note.steps.isNotEmpty()) {
                val shown = note.steps.takeLast(MAX_STEPS_SHOWN)
                val hidden = note.steps.size - shown.size
                if (hidden > 0) {
                    val more = RemoteViews(context.packageName, R.layout.widget_step_more)
                    more.setTextViewText(R.id.step_more, context.getString(R.string.earlier_steps, hidden))
                    views.addView(R.id.item_history, more)
                }
                for (step in shown) views.addView(R.id.item_history, stepRow(step))
            }
            val hasSteps = note.steps.isNotEmpty()
            views.setViewVisibility(R.id.item_history, if (hasSteps) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.item_line_in, if (hasSteps) View.VISIBLE else View.GONE)

            // The current step's time, and the buttons under it.
            val reminder = Format.reminderLine(context, note)
            setTextOrHide(views, R.id.item_time, reminder)
            val canShift = reminder != null
            views.setViewVisibility(R.id.item_earlier, if (canShift) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.item_later, if (canShift) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.item_update, if (note.isTask) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.item_buttons, if (canShift || note.isTask) View.VISIBLE else View.GONE)
            views.setOnClickFillInIntent(R.id.item_earlier, shift(note, -SHIFT_MINUTES))
            views.setOnClickFillInIntent(R.id.item_later, shift(note, SHIFT_MINUTES))
            views.setOnClickFillInIntent(R.id.item_update, action(note, WidgetClickActivity.ACTION_UPDATE))

            views.setOnClickFillInIntent(R.id.item_root, action(note, WidgetClickActivity.ACTION_OPEN))
            if (note.isTask) {
                views.setImageViewResource(R.id.item_check, R.drawable.ic_task_open)
                views.setContentDescription(R.id.item_check, context.getString(R.string.mark_done))
                views.setOnClickFillInIntent(R.id.item_check, action(note, WidgetClickActivity.ACTION_TOGGLE))
            } else {
                views.setImageViewResource(R.id.item_check, R.drawable.ic_note)
                views.setContentDescription(R.id.item_check, context.getString(R.string.type_note))
                views.setOnClickFillInIntent(R.id.item_check, action(note, WidgetClickActivity.ACTION_OPEN))
            }
            return views
        }

        private fun stepRow(step: Step): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_step_row)
            row.setTextViewText(R.id.step_text, struck(step.headline))
            row.setTextViewText(R.id.step_time, Format.stepLine(context, step))
            return row
        }

        private fun doneCard(note: Note): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note_item_done)
            views.setTextViewText(R.id.item_title, struck(note.headline))
            views.setViewVisibility(R.id.item_update, if (note.isTask) View.VISIBLE else View.GONE)
            views.setOnClickFillInIntent(R.id.item_root, action(note, WidgetClickActivity.ACTION_OPEN))
            views.setOnClickFillInIntent(R.id.item_check, action(note, WidgetClickActivity.ACTION_TOGGLE))
            views.setOnClickFillInIntent(R.id.item_update, action(note, WidgetClickActivity.ACTION_UPDATE))
            return views
        }

        private fun setTextOrHide(views: RemoteViews, id: Int, text: CharSequence?) {
            if (text != null) {
                views.setTextViewText(id, text)
                views.setViewVisibility(id, View.VISIBLE)
            } else {
                views.setViewVisibility(id, View.GONE)
            }
        }

        private fun struck(text: String): CharSequence = SpannableString(text).apply {
            setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        private fun action(note: Note, action: String): Intent = Intent()
            .putExtra(EXTRA_NOTE_ID, note.id)
            .putExtra(WidgetClickActivity.EXTRA_ACTION, action)

        private fun shift(note: Note, minutes: Int): Intent =
            action(note, WidgetClickActivity.ACTION_SHIFT).putExtra(WidgetClickActivity.EXTRA_MINUTES, minutes)
    }

    companion object {
        const val SHIFT_MINUTES = 15

        /** Earlier steps shown on a widget card; older ones are summed up as "+N earlier steps". */
        const val MAX_STEPS_SHOWN = 3
    }
}
