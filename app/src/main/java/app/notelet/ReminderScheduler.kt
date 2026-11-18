package app.notelet

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Sets one system alarm per note, for the next time that note should pop up. */
object ReminderScheduler {

    fun schedule(context: Context, note: Note) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context, note.id)
        alarms.cancel(pending)

        val at = note.nextAlarmAt() ?: return
        // Missed while the phone was off? Show it right away.
        val triggerAt = maxOf(at, System.currentTimeMillis() + 1_000L)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
    }

    fun rescheduleAll(context: Context) {
        NoteStore.all(context).forEach { schedule(context, it) }
    }

    private fun alarmIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
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
