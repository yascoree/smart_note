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
        val color = intent.getStringExtra("note_color") ?: "#FFFFFF"
        isFavorite = intent.getBooleanExtra("note_favorite", false)

        binding.tvTitle.text = noteTitle
        binding.tvContent.text = noteContent

        // Set background color
        try {
            binding.cardContent.setCardBackgroundColor(Color.parseColor(color))
        } catch (e: Exception) {
            binding.cardContent.setCardBackgroundColor(Color.WHITE)
        }

        // Set favorite icon
        updateFavoriteIcon()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Note Details"
    }

    private fun setupClickListeners() {
        binding.fabEdit.setOnClickListener {
            Toast.makeText(this, "Edit feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.fabShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$noteTitle\n\n$noteContent")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Note"))
        }

        binding.fabFavorite.setOnClickListener {
            if (noteId.isBlank()) {
                Toast.makeText(this, "Note introuvable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                isFavorite = repository.toggleFavorite(noteId)
                updateFavoriteIcon()
                Toast.makeText(
                    this@NoteDetailsActivity,
                    if (isFavorite) "Added to favorites" else "Removed from favorites",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.fabDelete.setOnClickListener {
            if (noteId.isBlank()) {
                Toast.makeText(this, "Note introuvable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val deleted = repository.deleteNote(noteId)
                if (deleted) {
                    Toast.makeText(this@NoteDetailsActivity, "Note supprimée", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@NoteDetailsActivity, "Erreur lors de la suppression", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFavoriteIcon() {
        if (isFavorite) {
            binding.fabFavorite.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            binding.fabFavorite.setImageResource(android.R.drawable.btn_star_big_off)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
