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

            val details = note.details.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
            if (details != null) {
                views.setTextViewText(R.id.item_details, details)
                views.setViewVisibility(R.id.item_details, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.item_details, View.GONE)
            }

            val reminder = Format.reminderLine(context, note)
            if (reminder != null) {
                views.setTextViewText(R.id.item_time, reminder)
                views.setViewVisibility(R.id.item_time_row, View.VISIBLE)
                views.setOnClickFillInIntent(R.id.item_earlier, shift(note, -SHIFT_MINUTES))
                views.setOnClickFillInIntent(R.id.item_later, shift(note, SHIFT_MINUTES))
            } else {
                views.setViewVisibility(R.id.item_time_row, View.GONE)
            }

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

        private fun doneCard(note: Note): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note_item_done)
            val struck = SpannableString(note.headline)
            struck.setSpan(StrikethroughSpan(), 0, struck.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            views.setTextViewText(R.id.item_title, struck)
            views.setOnClickFillInIntent(R.id.item_root, action(note, WidgetClickActivity.ACTION_OPEN))
            views.setOnClickFillInIntent(R.id.item_check, action(note, WidgetClickActivity.ACTION_TOGGLE))
            return views
        }

        private fun action(note: Note, action: String): Intent = Intent()
            .putExtra(EXTRA_NOTE_ID, note.id)
            .putExtra(WidgetClickActivity.EXTRA_ACTION, action)

        private fun shift(note: Note, minutes: Int): Intent =
            action(note, WidgetClickActivity.ACTION_SHIFT).putExtra(WidgetClickActivity.EXTRA_MINUTES, minutes)
    }

    companion object {
        const val SHIFT_MINUTES = 15
    }
}
