package yassine.app.smart_note.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.AskResponse
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Resource
import android.util.Log

class AiViewModel(private val repository: SmartNoteRepository) : ViewModel() {

    private val _responseState = MutableLiveData<Resource<AskResponse>>()
    val responseState: LiveData<Resource<AskResponse>> = _responseState

    fun sendMessage(question: String) {
        _responseState.value = Resource.Loading()
        viewModelScope.launch {
            try {
                val response = repository.sendAIMessage(question)
                Log.d("AiViewModel", "Server response: ${response.response}")

                if (!response.response.isNullOrBlank()) {
                    _responseState.value = Resource.Success(response)
                } else {
                    Log.e("AiViewModel", "Empty response field from server")
                    _responseState.value = Resource.Error("L'IA a renvoyé une réponse vide.")
                }
            } catch (e: Exception) {
                Log.e("AiViewModel", "Communication error", e)
                _responseState.value = Resource.Error(e.message ?: "Erreur de communication avec l'IA")
            }
        }
    }
}
