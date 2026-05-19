package yassine.app.smart_note.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import yassine.app.smart_note.R
import yassine.app.smart_note.adapters.NoteAdapter
import yassine.app.smart_note.databinding.ActivityHomeBinding
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.ui.AiAssistantActivity
import yassine.app.smart_note.ui.ProfileActivity
import yassine.app.smart_note.utils.Resource
import yassine.app.smart_note.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var noteAdapter: NoteAdapter
    private val repository by lazy { SmartNoteRepository.getInstance(applicationContext) }
    
    private val viewModel: HomeViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(SmartNoteRepository.getInstance(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupBottomNavigation()

        binding.tvGreeting.text = "Good morning, ${repository.getUserName()}"

        viewModel.loadNotes()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadNotes()
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onItemClick = { note -> openNoteDetail(note) },
            onFavoriteClick = { note -> viewModel.toggleFavorite(note.id, note.isFavorite) },
            onDeleteClick = { note -> viewModel.deleteNote(note.id) }
        )

        binding.rvNotes.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = noteAdapter
        }
    }

    private fun setupObservers() {
        viewModel.notesState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    // Show progress if needed
                }
                is Resource.Success -> {
                    val notes = resource.data
                    noteAdapter.updateNotes(notes)
                    updateEmptyState(notes.isEmpty())
                }
                is Resource.Error -> {
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.searchResults.observe(this) { resource ->
            when (resource) {
                is Resource.Success -> {
                    noteAdapter.updateNotes(resource.data)
                    updateEmptyState(resource.data.isEmpty())
                }
                else -> {}
            }
        }

        viewModel.deleteState.observe(this) { resource ->
            when (resource) {
                is Resource.Success -> {
                    if (resource.data) {
                        Toast.makeText(this, "Note supprimée", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvNotes.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvNotes.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchNotes(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_ai -> {
                    startActivity(Intent(this, AiAssistantActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun openNoteDetail(note: Note) {
        val intent = Intent(this, NoteDetailsActivity::class.java).apply {
            putExtra("note_id", note.id)
            putExtra("note_title", note.title)
            putExtra("note_content", note.content)
            putExtra("note_color", note.color)
            putExtra("note_favorite", note.isFavorite)
        }
        startActivity(intent)
    }
}
