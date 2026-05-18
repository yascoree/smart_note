package yassine.app.smart_note.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.repository.SmartNoteRepo
import yassine.app.smart_note.utils.Resource

class HomeViewModel(private val repository: SmartNoteRepo) : ViewModel() {

    private val _notesState = MutableStateFlow<Resource<List<Note>>>(Resource.Loading())
    val notesState: StateFlow<Resource<List<Note>>> = _notesState.asStateFlow()

    private val _favoriteState = MutableStateFlow<Resource<Boolean>>(Resource.Idle())
    val favoriteState: StateFlow<Resource<Boolean>> = _favoriteState.asStateFlow()

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
        _notesState.value = Resource.Loading()
        viewModelScope.launch {
            try {
                val results = repository.searchNotes(query)
                _notesState.value = Resource.Success(results)
            } catch (e: Exception) {
                _notesState.value = Resource.Error(e.message ?: "Erreur de recherche")
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            try {
                val result = repository.deleteNote(noteId)
                if (result) {
                    loadNotes() // Recharger la liste
                } else {
                    _favoriteState.value = Resource.Error("Erreur lors de la suppression")
                }
            } catch (e: Exception) {
                _favoriteState.value = Resource.Error(e.message ?: "Erreur de suppression")
            }
        }
    }

    fun toggleFavorite(noteId: String, currentStatus: Boolean) {
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