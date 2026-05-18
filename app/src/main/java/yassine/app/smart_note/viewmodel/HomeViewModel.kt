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

    private val _notesState = MutableLiveData<Resource<List<Note>>>()
    val notesState: LiveData<Resource<List<Note>>> = _notesState

    private val _searchResults = MutableLiveData<Resource<List<Note>>>()
    val searchResults: LiveData<Resource<List<Note>>> = _searchResults

    private val _deleteState = MutableLiveData<Resource<Boolean>>(Resource.Idle())
    val deleteState: LiveData<Resource<Boolean>> = _deleteState

    private val _favoriteState = MutableLiveData<Resource<Boolean>>(Resource.Idle())
    val favoriteState: LiveData<Resource<Boolean>> = _favoriteState

    init {
        loadNotes()
    }

    fun loadNotes() {
        _notesState.value = Resource.Loading()
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
        _searchResults.value = Resource.Loading()
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
        _deleteState.value = Resource.Loading()
        viewModelScope.launch {
            try {
                val result = repository.deleteNote(noteId)
                if (result) {
                    loadNotes() // Recharger la liste
                    _deleteState.value = Resource.Success(true)
                } else {
                    _deleteState.value = Resource.Error("Erreur lors de la suppression")
                }
            } catch (e: Exception) {
                _deleteState.value = Resource.Error(e.message ?: "Erreur de suppression")
            }
        }
    }

    fun toggleFavorite(noteId: String, currentStatus: Boolean) {
        _favoriteState.value = Resource.Loading()
        viewModelScope.launch {
            try {
                val result = repository.toggleFavorite(noteId, !currentStatus)
                if (result) {
                    loadNotes() // Recharger la liste
                    _favoriteState.value = Resource.Success(true)
                } else {
                    _favoriteState.value = Resource.Error("Erreur lors du changement")
                }
            } catch (e: Exception) {
                _favoriteState.value = Resource.Error(e.message ?: "Erreur")
            }
        }
    }
}
