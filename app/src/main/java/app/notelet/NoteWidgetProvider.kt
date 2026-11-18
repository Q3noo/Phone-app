package app.notelet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews

/**
 * The home-screen widget: a header with a + button, a scrolling list of note cards,
 * and Clear done / Clear all buttons at the bottom.
 *
 * Every colour comes from day/night colour resources in the layouts, so the phone
 * switches them itself when it changes between light and dark mode.
 */
class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val REQUEST_ADD = -1
        private const val REQUEST_OPEN_APP = -2
        private const val REQUEST_ITEM = -3
        private const val REQUEST_CLEAR_DONE = -4
        private const val REQUEST_CLEAR_ALL = -5

        /** Redraw every copy of the widget after notes change. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val notes = NoteStore.all(context)
            for (id in ids) manager.updateAppWidget(id, buildViews(context, id, notes))
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun buildViews(context: Context, widgetId: Int, notes: List<Note>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)

            val adapterIntent = Intent(context, NoteWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            adapterIntent.data = Uri.parse(adapterIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_list, adapterIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Header: counts, open the app, add a note.
            val open = notes.count { !it.done }
            val done = notes.count { it.done }
            views.setTextViewText(
                R.id.widget_count,
                if (done > 0) context.getString(R.string.count_open_done, open, done)
                else context.getString(R.string.count_open, open),
            )
            views.setViewVisibility(R.id.widget_count, if (notes.isEmpty()) View.GONE else View.VISIBLE)

            val add = activityIntent(context, REQUEST_ADD, EditNoteActivity.newIntent(context))
            views.setOnClickPendingIntent(R.id.widget_add, add)
            views.setOnClickPendingIntent(R.id.widget_empty, add)
            views.setOnClickPendingIntent(
                R.id.widget_title,
                activityIntent(context, REQUEST_OPEN_APP, Intent(context, MainActivity::class.java)),
            )

            // Footer: clear buttons, only when there is something to clear.
            views.setViewVisibility(R.id.widget_footer, if (notes.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_clear_done, if (done > 0) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(
                R.id.widget_clear_done,
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CLEAR_DONE,
                    Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_CLEAR_DONE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_clear_all,
                activityIntent(context, REQUEST_CLEAR_ALL, Intent(context, ConfirmClearActivity::class.java)),
            )

            // Cards fill in which note and what to do (open, tick off, move the pop-up),
            // so this one has to stay mutable.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val itemTemplate = PendingIntent.getActivity(
                context,
                REQUEST_ITEM,
                Intent(context, WidgetClickActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            views.setPendingIntentTemplate(R.id.widget_list, itemTemplate)
            return views
        }

        private fun activityIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
