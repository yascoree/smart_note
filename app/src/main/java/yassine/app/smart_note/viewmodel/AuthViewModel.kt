package yassine.app.smart_note.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yassine.app.smart_note.models.User
import yassine.app.smart_note.repository.FirebaseAuthRepository
import yassine.app.smart_note.utils.Resource

class AuthViewModel(
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<Resource<User>>(Resource.Idle())
    val loginState: StateFlow<Resource<User>> = _loginState.asStateFlow()

    private val _signupState = MutableStateFlow<Resource<User>>(Resource.Idle())
    val signupState: StateFlow<Resource<User>> = _signupState.asStateFlow()

    private val _logoutState = MutableStateFlow<Resource<Boolean>>(Resource.Idle())
    val logoutState: StateFlow<Resource<Boolean>> = _logoutState.asStateFlow()

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Unauthenticated)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

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