package app.notelet

import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class NotesAdapter(
    private val onOpen: (Note) -> Unit,
    private val onToggleDone: (Note) -> Unit,
    private val onShift: (Note, Int) -> Unit,
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

        holder.card.setCardBackgroundColor(
            ContextCompat.getColor(context, if (note.done) R.color.nl_card_done else R.color.nl_card)
        )

        holder.check.setImageResource(
            when {
                !note.isTask -> R.drawable.ic_note
                note.done -> R.drawable.ic_task_done
                else -> R.drawable.ic_task_open
            }
        )
        holder.check.contentDescription = context.getString(
            when {
                !note.isTask -> R.string.type_note
                note.done -> R.string.mark_not_done
                else -> R.string.mark_done
            }
        )
        holder.check.setOnClickListener { if (note.isTask) onToggleDone(note) else onOpen(note) }

        holder.title.text = note.headline
        holder.title.paintFlags = if (note.done) {
            holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        holder.title.setTextColor(
            ContextCompat.getColor(context, if (note.done) R.color.nl_text_secondary else R.color.nl_text)
        )

        val details = note.details
        holder.details.text = details
        holder.details.isVisible = details.isNotEmpty() && !note.done

        val reminder = Format.reminderLine(context, note)
        holder.timeRow.isVisible = reminder != null
        holder.reminder.text = reminder
        holder.earlier.setOnClickListener { onShift(note, -NoteWidgetService.SHIFT_MINUTES) }
        holder.later.setOnClickListener { onShift(note, NoteWidgetService.SHIFT_MINUTES) }

        holder.itemView.setOnClickListener { onOpen(note) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val check: ImageView = view.findViewById(R.id.check)
        val title: TextView = view.findViewById(R.id.title)
        val details: TextView = view.findViewById(R.id.details)
        val timeRow: View = view.findViewById(R.id.time_row)
        val reminder: TextView = view.findViewById(R.id.reminder)
        val earlier: View = view.findViewById(R.id.earlier)
        val later: View = view.findViewById(R.id.later)
    }
}
