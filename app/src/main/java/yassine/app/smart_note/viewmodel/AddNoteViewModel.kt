package yassine.app.smart_note.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Resource

class AddNoteViewModel(private val repository: SmartNoteRepository) : ViewModel() {

    private val _saveState = MutableLiveData<Resource<Note>>()
    val saveState: LiveData<Resource<Note>> = _saveState

    private val _updateState = MutableLiveData<Resource<Note>>()
    val updateState: LiveData<Resource<Note>> = _updateState

    fun saveNote(title: String, content: String, noteType: String = "Personal") {
        _saveState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val note = repository.addNote(title, content, noteType)
                _saveState.value = Resource.Success(note)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "Erreur lors de la sauvegarde")
            }
        }
    }

    fun updateNote(note: Note) {
        _updateState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val updatedNote = repository.updateNote(note)
                _updateState.value = Resource.Success(updatedNote)
            } catch (e: Exception) {
                _updateState.value = Resource.Error(e.message ?: "Erreur lors de la mise à jour")
            }
        }
    }
}