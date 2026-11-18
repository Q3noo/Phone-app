package app.notelet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

/** The home-screen widget: a header with a + button and a scrolling list of notes. */
class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val REQUEST_ADD = -1
        private const val REQUEST_OPEN_APP = -2
        private const val REQUEST_ITEM = -3

        /** Redraw every copy of the widget after notes change. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            for (id in ids) manager.updateAppWidget(id, buildViews(context, id))
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)

            val adapterIntent = Intent(context, NoteWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            adapterIntent.data = Uri.parse(adapterIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_list, adapterIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val add = PendingIntent.getActivity(
                context,
                REQUEST_ADD,
                EditNoteActivity.newIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_add, add)
            views.setOnClickPendingIntent(R.id.widget_empty, add)

            val openApp = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, openApp)

            // Rows fill in which note and what to do (open it or tick it off),
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
    }
}
