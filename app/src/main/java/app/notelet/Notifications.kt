package app.notelet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Builds the pop-ups. A high-importance channel makes Android show them as heads-up banners. */
object Notifications {
    private const val CHANNEL_ID = "reminders"
    const val SNOOZE_MINUTES = 10

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // checked by canPost()
    fun showReminder(context: Context, note: Note) {
        if (!canPost(context)) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            EditNoteActivity.editIntent(context, note.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(note.headline)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val details = note.details
        if (details.isNotEmpty()) {
            builder.setContentText(details)
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
        } else if (note.isTask) {
            builder.setContentText(context.getString(R.string.notification_task_hint))
        }

        if (note.isTask) {
            builder.addAction(
                R.drawable.ic_check,
                context.getString(R.string.action_done),
                actionIntent(context, note.id, NotificationActionReceiver.ACTION_DONE),
            )
        }
        builder.addAction(
            R.drawable.ic_notification,
            context.getString(R.string.action_snooze, SNOOZE_MINUTES),
            actionIntent(context, note.id, NotificationActionReceiver.ACTION_SNOOZE),
        )

        NotificationManagerCompat.from(context).notify(note.id.toInt(), builder.build())
    }

    fun cancel(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }

    private fun actionIntent(context: Context, id: Long, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(noteUri(id))
            .putExtra(EXTRA_NOTE_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
