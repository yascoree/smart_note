package yassine.app.smart_note.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.utils.Constants

class SmartNoteRepo(private val context: Context) {

    private val sharedPref: SharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    private val supabaseRepo = SupabaseNoteRepository()

    // ==================== AUTHENTIFICATION ====================

    suspend fun login(email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            // Pour l'instant, login local
            // Plus tard: Supabase Auth
            val isValid = email.contains("@") && password.length >= 4
            if (isValid) {
                sharedPref.edit().apply {
                    putBoolean(Constants.KEY_IS_LOGGED_IN, true)
                    putString(Constants.KEY_USER_EMAIL, email)
                    putString(Constants.KEY_USER_NAME, email.split("@").first())
                    apply()
                }
            }
            isValid
        }
    }

    fun isLoggedIn(): Boolean = sharedPref.getBoolean(Constants.KEY_IS_LOGGED_IN, false)
    fun getUserName(): String = sharedPref.getString(Constants.KEY_USER_NAME, "Utilisateur") ?: "Utilisateur"

    // ==================== NOTES (SUPABASE) ====================

    suspend fun getAllNotes(): List<Note> {
        return supabaseRepo.getAllNotes()
    }

    suspend fun getFavoriteNotes(): List<Note> {
        return supabaseRepo.getFavoriteNotes()
    }

    suspend fun searchNotes(query: String): List<Note> {
        return supabaseRepo.searchNotes(query)
    }

    suspend fun addNote(title: String, content: String, color: String): Note? {
        val note = Note(
            title = title.ifEmpty { "Sans titre" },
            content = content,
            color = color,
            isFavorite = false
        )
        return supabaseRepo.addNote(note)
    }

    suspend fun updateNote(note: Note): Boolean {
        val updates = mapOf(
            "title" to note.title,
            "content" to note.content,
            "color" to note.color,
            "is_favorite" to note.isFavorite
        )
        return supabaseRepo.updateNote(note.id, updates)
    }

    suspend fun deleteNote(noteId: String): Boolean {
        return supabaseRepo.deleteNote(noteId)
    }

    suspend fun toggleFavorite(noteId: String, isFavorite: Boolean): Boolean {
        return supabaseRepo.toggleFavorite(noteId, isFavorite)
    }

    fun observeNotes(): Flow<List<Note>> {
        return supabaseRepo.observeNotes()
    }
}