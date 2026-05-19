package yassine.app.smart_note.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.AskResponse
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Resource

class AiViewModel(private val repository: SmartNoteRepository) : ViewModel() {

    private val _responseState = MutableLiveData<Resource<AskResponse>>()
    val responseState: LiveData<Resource<AskResponse>> = _responseState

    fun sendMessage(question: String) {
        _responseState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val response = repository.sendAIMessage(question)
                Log.d("AiViewModel", "Server response answer=${response.answer}, context=${response.context_used}")

                if (!response.answer.isNullOrBlank()) {
                    _responseState.value = Resource.Success(response)
                } else {
                    Log.e("AiViewModel", "Empty answer field from server: $response")
                    _responseState.value = Resource.Error("L'IA a renvoyé une réponse vide.")
                }
            } catch (e: Exception) {
                Log.e("AiViewModel", "Communication error", e)
                _responseState.value = Resource.Error(e.message ?: e.toString())
            }
        }
    }
}
