package yassine.app.smart_note.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.User
import yassine.app.smart_note.repository.FirebaseAuthRepository
import yassine.app.smart_note.utils.Resource

class AuthViewModel(
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<Resource<User>>(Resource.Idle())
    val loginState: LiveData<Resource<User>> = _loginState

    private val _signupState = MutableLiveData<Resource<User>>(Resource.Idle())
    val signupState: LiveData<Resource<User>> = _signupState

    private val _logoutState = MutableLiveData<Resource<Boolean>>(Resource.Idle())
    val logoutState: LiveData<Resource<Boolean>> = _logoutState

    private val _authState = MutableLiveData<AuthUiState>(AuthUiState.Unauthenticated)
    val authState: LiveData<AuthUiState> = _authState

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _authState.value = when (state) {
                    is yassine.app.smart_note.repository.AuthState.Unauthenticated -> AuthUiState.Unauthenticated
                    is yassine.app.smart_note.repository.AuthState.Loading -> AuthUiState.Loading
                    is yassine.app.smart_note.repository.AuthState.Authenticated -> AuthUiState.Authenticated(state.user)
                    is yassine.app.smart_note.repository.AuthState.Error -> AuthUiState.Error(state.message)
                }
            }
        }
    }

    fun login(email: String, password: String) {
        _loginState.value = Resource.Loading()
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                _loginState.value = Resource.Success(result.getOrNull())
            } else {
                _loginState.value = Resource.Error(result.exceptionOrNull()?.message ?: "Erreur de connexion")
            }
        }
    }

    fun signup(email: String, password: String) {
        _signupState.value = Resource.Loading()
        viewModelScope.launch {
            val result = authRepository.signup(email, password)
            if (result.isSuccess) {
                _signupState.value = Resource.Success(result.getOrNull())
            } else {
                _signupState.value = Resource.Error(result.exceptionOrNull()?.message ?: "Erreur d'inscription")
            }
        }
    }

    fun logout() {
        _logoutState.value = Resource.Loading()
        authRepository.signOut()
        _logoutState.value = Resource.Success(true)
    }

    fun getGoogleSignInIntent() = authRepository.getGoogleSignInIntent()

    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return authRepository.signInWithGoogle(idToken)
    }
}

sealed class AuthUiState {
    object Unauthenticated : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
