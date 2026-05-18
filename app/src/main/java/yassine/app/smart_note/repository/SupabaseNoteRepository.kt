package yassine.app.smart_note.repository

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.supabase.SupabaseManager

class SupabaseNoteRepository {

    private val supabase = SupabaseManager.getInstance()
    private val notesTable = supabase.postgrest["notes"]

    // Récupérer toutes les notes
    suspend fun getAllNotes(userId: String): List<Note> = withContext(Dispatchers.IO) {
        val response = notesTable.select {
            filter { eq("user_id", userId) }
            order("updated_at", Order.DESCENDING)
        }
        response.decodeList<Note>()
    }

    // Récupérer les favoris
    suspend fun getFavoriteNotes(userId: String): List<Note> = withContext(Dispatchers.IO) {
        val response = notesTable.select {
            filter {
                eq("user_id", userId)
                eq("is_favorite", true)
            }
            order("updated_at", Order.DESCENDING)
        }
        response.decodeList<Note>()
    }

    // Rechercher des notes
    suspend fun searchNotes(userId: String, query: String): List<Note> = withContext(Dispatchers.IO) {
        val response = notesTable.select {
            filter {
                eq("user_id", userId)
                or {
                    ilike("title", "%$query%")
                    ilike("content", "%$query%")
                }
            }
            order("updated_at", Order.DESCENDING)
        }
        response.decodeList<Note>()
    }

    // Ajouter une note
    suspend fun addNote(note: Note): Note = withContext(Dispatchers.IO) {
        val response = notesTable.insert(note) {
            select()
        }
        response.decodeList<Note>().first()
    }

    // Mettre à jour une note
    suspend fun updateNote(userId: String, noteId: String, updates: Map<String, Any>): Note = withContext(Dispatchers.IO) {
        val response = notesTable.update(updates) {
            select()
            filter {
                eq("id", noteId)
                eq("user_id", userId)
            }
        }
        response.decodeList<Note>().first()
    }

    // Supprimer une note
    suspend fun deleteNote(userId: String, noteId: String): Boolean = withContext(Dispatchers.IO) {
        notesTable.delete {
            filter {
                eq("id", noteId)
                eq("user_id", userId)
            }
        }
        true
    }

    // Changer le statut favori
    suspend fun toggleFavorite(userId: String, noteId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        notesTable.update(
            mapOf("is_favorite" to isFavorite)
        ) {
            filter {
                eq("id", noteId)
                eq("user_id", userId)
            }
        }
        true
    }

    // Flow pour les changements en temps réel
    fun observeNotes(userId: String): Flow<List<Note>> = flow {
        while (true) {
            emit(getAllNotes(userId))
            kotlinx.coroutines.delay(2000) // Polling toutes les 2 secondes
        }
    }
}
