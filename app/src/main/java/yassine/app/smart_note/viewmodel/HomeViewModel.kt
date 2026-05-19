package yassine.app.smart_note.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Resource

class HomeViewModel(private val repository: SmartNoteRepository) : ViewModel() {

    private val _notesState = MutableLiveData<Resource<List<Note>>>(Resource.Idle)
    val notesState: LiveData<Resource<List<Note>>> = _notesState

    private val _searchResults = MutableLiveData<Resource<List<Note>>>(Resource.Idle)
    val searchResults: LiveData<Resource<List<Note>>> = _searchResults

    private val _deleteState = MutableLiveData<Resource<Boolean>>(Resource.Idle)
    val deleteState: LiveData<Resource<Boolean>> = _deleteState

    init {
        loadNotes()
    }

    fun loadNotes() {
        _notesState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val notes = repository.getAllNotes()
                _notesState.value = Resource.Success(notes)
            } catch (e: Exception) {
                _notesState.value = Resource.Error(e.message ?: "Erreur de chargement")
            }
        }
    }

    fun searchNotes(query: String) {
        if (query.isBlank()) {
            loadNotes()
            return
        }
        _searchResults.value = Resource.Loading
        viewModelScope.launch {
            try {
                val results = repository.searchNotes(query)
                _searchResults.value = Resource.Success(results)
            } catch (e: Exception) {
                _searchResults.value = Resource.Error(e.message ?: "Erreur de recherche")
            }
        }
    }

    fun deleteNote(noteId: String) {
        _deleteState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val result = repository.deleteNote(noteId)
                if (result) {
                    loadNotes()
                    _deleteState.value = Resource.Success(true)
                } else {
                    _deleteState.value = Resource.Error("Erreur lors de la suppression")
                }
            } catch (e: Exception) {
                _deleteState.value = Resource.Error(e.message ?: "Erreur de suppression")
            }
        }
    }

    fun toggleFavorite(noteId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(noteId, !isFavorite)
                loadNotes()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
