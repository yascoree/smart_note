package yassine.app.smart_note.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import yassine.app.smart_note.R
import yassine.app.smart_note.adapters.NoteAdapter
import yassine.app.smart_note.databinding.ActivityHomeBinding
import yassine.app.smart_note.firebase.FirebaseAuthService
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.repository.FirebaseAuthRepository
import yassine.app.smart_note.repository.SmartNoteRepository
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

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.loadNotes()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadNotes()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Mes Notes"
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onItemClick = { note -> openNoteDetail(note) },
            onFavoriteClick = { note -> viewModel.toggleFavorite(note.id) },
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
                    // Afficher chargement
                }
                is Resource.Success -> {
                    noteAdapter.updateNotes(resource.data ?: emptyList())
                    updateStats(resource.data ?: emptyList())
                    updateEmptyState(resource.data.isNullOrEmpty())
                }
                is Resource.Error -> {
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.searchResults.observe(this) { resource ->
            if (resource is Resource.Success) {
                noteAdapter.updateNotes(resource.data ?: emptyList())
                updateEmptyState(resource.data.isNullOrEmpty())
            }
        }

        viewModel.deleteState.observe(this) { resource ->
            if (resource is Resource.Success && resource.data == true) {
                Toast.makeText(this, "Note supprimée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStats(notes: List<Note>) {
        val favorites = notes.count { it.isFavorite }
        binding.tvTotalNotes.text = notes.size.toString()
        binding.tvFavoriteNotes.text = favorites.toString()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.layoutEmpty.visibility = android.view.View.VISIBLE
            binding.rvNotes.visibility = android.view.View.GONE
        } else {
            binding.layoutEmpty.visibility = android.view.View.GONE
            binding.rvNotes.visibility = android.view.View.VISIBLE
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ai_assistant -> {
                startActivity(Intent(this, AiAssistantActivity::class.java))
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        val authService = FirebaseAuthService(this)
        val authRepository = FirebaseAuthRepository(authService)
        authRepository.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

// Dans onOptionsItemSelected

}
