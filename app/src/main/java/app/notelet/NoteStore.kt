package app.notelet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/** Keeps every note as one small JSON array on the phone. */
object NoteStore {
    private const val PREFS = "notelet"
    private const val KEY_NOTES = "notes"
    private const val KEY_NOTES_BACKUP = "notes_unreadable_backup"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_LAST_WAS_TASK = "last_was_task"
    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Note> = synchronized(lock) { read(context) }

    fun get(context: Context, id: Long): Note? = all(context).firstOrNull { it.id == id }

    fun newId(context: Context): Long = synchronized(lock) {
        val prefs = prefs(context)
        val id = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).commit()
        id
    }

    fun put(context: Context, note: Note) = update(context) { notes ->
        notes.filter { it.id != note.id } + note
    }

    fun remove(context: Context, id: Long) = update(context) { notes ->
        notes.filter { it.id != id }
    }

    fun lastWasTask(context: Context): Boolean = prefs(context).getBoolean(KEY_LAST_WAS_TASK, false)

    fun setLastWasTask(context: Context, task: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAST_WAS_TASK, task).apply()
    }

    /** Open items first (soonest pop-up on top, then newest), finished tasks last. */
    fun sortedForDisplay(notes: List<Note>): List<Note> = notes.sortedWith(
        compareBy<Note> { it.done }
            .thenBy { it.nextAlarmAt() ?: Long.MAX_VALUE }
            .thenByDescending { it.createdAt }
    )

    private fun update(context: Context, transform: (List<Note>) -> List<Note>) {
        synchronized(lock) { write(context, transform(read(context))) }
    }

    private fun read(context: Context): List<Note> {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { Note.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            // Keep a copy instead of silently losing everything.
            Log.e("NoteStore", "Could not read saved notes", e)
            prefs.edit().putString(KEY_NOTES_BACKUP, raw).commit()
            emptyList()
        }
    }

    private fun write(context: Context, notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_NOTES, array.toString()).commit()
    }
}
