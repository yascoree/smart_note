package yassine.app.smart_note.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.content.Context
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yassine.app.smart_note.models.User
import yassine.app.smart_note.R

class FirebaseAuthService(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        setupGoogleSignIn()
        checkCurrentUser()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _authState.value = AuthState.Authenticated(currentUser.toUser())
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user?.toUser()
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Échec de connexion"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user?.toUser()
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Échec d'inscription"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGoogleSignInIntent() = googleSignInClient.signInIntent

    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user?.toUser()
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Échec de connexion Google"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    private fun FirebaseUser.toUser(): User {
        return User(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName,
            photoUrl = this.photoUrl?.toString()
        )
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}
