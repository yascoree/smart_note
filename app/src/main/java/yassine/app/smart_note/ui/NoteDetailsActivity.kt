package yassine.app.smart_note.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import yassine.app.smart_note.databinding.ActivityNoteDetailsBinding
import yassine.app.smart_note.repository.SmartNoteRepository

class NoteDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailsBinding
    private var noteId = ""
    private var isFavorite = false
    private var noteTitle = ""
    private var noteContent = ""
    private var noteColor = "#FFFFFF"
    private val repository by lazy { SmartNoteRepository.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadNoteData()
        setupToolbar()
        setupClickListeners()
    }

    private fun loadNoteData() {
        noteId = intent.getStringExtra("note_id") ?: ""
        noteTitle = intent.getStringExtra("note_title") ?: ""
        noteContent = intent.getStringExtra("note_content") ?: ""
        noteColor = intent.getStringExtra("note_color") ?: "#FFFFFF"
        isFavorite = intent.getBooleanExtra("note_favorite", false)

        binding.tvTitle.text = noteTitle
        binding.tvContent.text = noteContent

        // Set background color
        try {
            binding.root.setBackgroundColor(Color.parseColor(noteColor))
        } catch (e: Exception) {
            binding.root.setBackgroundColor(Color.WHITE)
        }

        // Set favorite icon
        updateFavoriteIcon()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener {
            if (noteId.isBlank()) {
                Toast.makeText(this, "Note introuvable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, AddNoteActivity::class.java).apply {
                putExtra("is_edit", true)
                putExtra("note_id", noteId)
                putExtra("note_title", noteTitle)
                putExtra("note_content", noteContent)
                putExtra("note_color", noteColor)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$noteTitle\n\n$noteContent")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Note"))
        }

        binding.btnFavorite.setOnClickListener {
            if (noteId.isBlank()) {
                Toast.makeText(this, "Note introuvable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val newStatus = !isFavorite
                    val updated = repository.toggleFavorite(noteId, newStatus)
                    if (updated) {
                        isFavorite = newStatus
                        updateFavoriteIcon()
                        Toast.makeText(
                            this@NoteDetailsActivity,
                            if (isFavorite) "Added to favorites" else "Removed from favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this@NoteDetailsActivity, "Erreur lors de la mise à jour", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@NoteDetailsActivity, e.message ?: "Erreur lors de la mise à jour", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFavoriteIcon() {
        if (isFavorite) {
            binding.btnFavorite.setIconResource(android.R.drawable.btn_star_big_on)
        } else {
            binding.btnFavorite.setIconResource(android.R.drawable.btn_star_big_off)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
