package yassine.app.smart_note.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
    private var allNotes: List<Note> = emptyList()
    private var selectedFilter: String = FILTER_ALL
    private var currentQuery: String = ""
    
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
        setupChips()

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
                    allNotes = resource.data
                    applyFilters()
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
                    allNotes = resource.data
                    applyFilters()
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
                currentQuery = s?.toString().orEmpty()
                applyFilters()
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
            putExtra("note_favorite", note.isFavorite)
        }
        startActivity(intent)
    }


    private fun setupChips() {

        val chips = listOf(
            binding.chipAll,
            binding.chipWork,
            binding.chipPersonal,
            binding.chipIdeas,
            binding.chipAi
        )

        chips.forEach { chip ->
            chip.setOnClickListener {

                // reset all
                chips.forEach { it.isChecked = false }

                // activate selected
                chip.isChecked = true

                selectedFilter = when (chip.id) {
                    R.id.chip_work -> FILTER_WORK
                    R.id.chip_personal -> FILTER_PERSONAL
                    R.id.chip_ideas -> FILTER_IDEAS
                    R.id.chip_ai -> FILTER_STUDY
                    else -> FILTER_ALL
                }
                applyFilters()
            }
        }

        // default
        binding.chipAll.isChecked = true
        selectedFilter = FILTER_ALL
        applyFilters()
    }

    private fun applyFilters() {
        val query = currentQuery.trim().lowercase()

        val filtered = allNotes.filter { note ->
            val matchesType = when (selectedFilter) {
                FILTER_WORK -> note.noteType.equals("Work", ignoreCase = true)
                FILTER_PERSONAL -> note.noteType.equals("Personal", ignoreCase = true)
                FILTER_IDEAS -> note.noteType.equals("Ideas", ignoreCase = true)
                FILTER_STUDY -> note.noteType.equals("Study", ignoreCase = true)
                else -> true
            }

            val matchesQuery = query.isBlank() ||
                note.title.lowercase().contains(query) ||
                note.content.lowercase().contains(query)

            matchesType && matchesQuery
        }

        noteAdapter.updateNotes(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    companion object {
        private const val FILTER_ALL = "ALL"
        private const val FILTER_WORK = "WORK"
        private const val FILTER_PERSONAL = "PERSONAL"
        private const val FILTER_IDEAS = "IDEAS"
        private const val FILTER_STUDY = "STUDY"
    }
}
