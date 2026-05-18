package yassine.app.smart_note.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import yassine.app.smart_note.databinding.ItemNoteBinding
import yassine.app.smart_note.models.Note
import java.text.SimpleDateFormat
import java.util.*

class NoteAdapter(
    private val onItemClick: (Note) -> Unit,
    private val onFavoriteClick: (Note) -> Unit,
    private val onDeleteClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private var notes = listOf<Note>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount(): Int = notes.size

    inner class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.apply {
                tvTitle.text = note.title.ifEmpty { "Sans titre" }
                tvContent.text = note.content.ifEmpty { "Aucun contenu" }
                tvDate.text = dateFormat.format(note.updatedAt)

                cardNote.setCardBackgroundColor(Color.parseColor(note.color))

                ivFavorite.setImageResource(
                    if (note.isFavorite) android.R.drawable.btn_star_big_on
                    else android.R.drawable.btn_star_big_off
                )

                ivFavorite.setOnClickListener { onFavoriteClick(note) }
                ivDelete.setOnClickListener { onDeleteClick(note) }
                cardNote.setOnClickListener { onItemClick(note) }
            }
        }
    }
}