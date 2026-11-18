package app.notelet

import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

class NotesAdapter(
    private val onOpen: (Note) -> Unit,
    private val onToggleDone: (Note, Boolean) -> Unit,
) : RecyclerView.Adapter<NotesAdapter.Holder>() {

    private var notes: List<Note> = emptyList()

    @SuppressLint("NotifyDataSetChanged") // the list is small; a full redraw is fine
    fun submit(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = notes.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val note = notes[position]
        val context = holder.itemView.context

        holder.check.isVisible = note.isTask
        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = note.done
        holder.check.setOnCheckedChangeListener { _, checked -> onToggleDone(note, checked) }

        holder.title.text = note.headline
        holder.title.paintFlags = if (note.done) {
            holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        holder.title.alpha = if (note.done) 0.6f else 1f

        val details = note.details
        holder.details.text = details
        holder.details.isVisible = details.isNotEmpty() && !note.done

        val reminder = Format.reminderLine(context, note)
        holder.reminder.text = reminder
        holder.reminder.isVisible = reminder != null

        holder.itemView.setOnClickListener { onOpen(note) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val check: CheckBox = view.findViewById(R.id.check)
        val title: TextView = view.findViewById(R.id.title)
        val details: TextView = view.findViewById(R.id.details)
        val reminder: TextView = view.findViewById(R.id.reminder)
    }
}
