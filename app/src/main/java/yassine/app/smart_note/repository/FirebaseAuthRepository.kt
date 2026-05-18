package yassine.app.smart_note.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import yassine.app.smart_note.firebase.FirebaseAuthService
import yassine.app.smart_note.models.User

class FirebaseAuthRepository(private val authService: FirebaseAuthService) {

    val authState: Flow<AuthState> = authService.authState

    suspend fun login(email: String, password: String): Result<User> {
        return authService.signInWithEmail(email, password)
    }

    suspend fun signup(email: String, password: String): Result<User> {
        return authService.signUpWithEmail(email, password)
    }

    fun signOut() {
        authService.signOut()
    }

    fun getGoogleSignInIntent() = authService.getGoogleSignInIntent()

    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return authService.signInWithGoogle(idToken)
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}