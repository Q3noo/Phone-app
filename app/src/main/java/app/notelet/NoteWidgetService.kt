package app.notelet

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

/** Supplies the rows of the widget's list. */
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
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = notes.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds(): Boolean = true

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note_item)
            val note = notes.getOrNull(position) ?: return views

            val headline = note.headline
            if (note.done) {
                val struck = SpannableString(headline)
                struck.setSpan(StrikethroughSpan(), 0, struck.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.item_title, struck)
                views.setTextColor(R.id.item_title, ContextCompat.getColor(context, R.color.widget_secondary))
            } else {
                views.setTextViewText(R.id.item_title, headline)
                views.setTextColor(R.id.item_title, ContextCompat.getColor(context, R.color.widget_on_background))
            }

            val subtitle = Format.reminderLine(context, note)
                ?: note.details.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
            if (subtitle != null) {
                views.setTextViewText(R.id.item_subtitle, subtitle)
                views.setViewVisibility(R.id.item_subtitle, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.item_subtitle, View.GONE)
            }

            val open = Intent()
                .putExtra(EXTRA_NOTE_ID, note.id)
                .putExtra(WidgetClickActivity.EXTRA_ACTION, WidgetClickActivity.ACTION_OPEN)
            views.setOnClickFillInIntent(R.id.item_root, open)

            if (note.isTask) {
                views.setImageViewResource(
                    R.id.item_icon,
                    if (note.done) R.drawable.ic_check_box else R.drawable.ic_check_box_blank,
                )
                views.setContentDescription(
                    R.id.item_icon,
                    context.getString(if (note.done) R.string.mark_not_done else R.string.mark_done),
                )
                val toggle = Intent()
                    .putExtra(EXTRA_NOTE_ID, note.id)
                    .putExtra(WidgetClickActivity.EXTRA_ACTION, WidgetClickActivity.ACTION_TOGGLE)
                views.setOnClickFillInIntent(R.id.item_icon, toggle)
            } else {
                views.setImageViewResource(R.id.item_icon, R.drawable.ic_note)
                views.setContentDescription(R.id.item_icon, context.getString(R.string.type_note))
                // Rows get reused, so always replace whatever click this icon had before.
                views.setOnClickFillInIntent(R.id.item_icon, open)
            }
            return views
        }
    }
}
