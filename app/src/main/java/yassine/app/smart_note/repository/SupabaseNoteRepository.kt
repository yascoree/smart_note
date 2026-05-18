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
    suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
        try {
            val response = notesTable.select {
                order("updated_at", Order.DESCENDING)
            }
            response.decodeList<Note>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Récupérer les favoris
    suspend fun getFavoriteNotes(): List<Note> = withContext(Dispatchers.IO) {
        try {
            val response = notesTable.select {
                filter { eq("is_favorite", true) }
                order("updated_at", Order.DESCENDING)
            }
            response.decodeList<Note>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Rechercher des notes
    suspend fun searchNotes(query: String): List<Note> = withContext(Dispatchers.IO) {
        try {
            val response = notesTable.select {
                filter {
                    or {
                        ilike("title", "%$query%")
                        ilike("content", "%$query%")
                    }
                }
                order("updated_at", Order.DESCENDING)
            }
            response.decodeList<Note>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Ajouter une note
    suspend fun addNote(note: Note): Note? = withContext(Dispatchers.IO) {
        try {
            val response = notesTable.insert(note) {
                select()
            }
            response.decodeList<Note>().firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Mettre à jour une note
    suspend fun updateNote(noteId: String, updates: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            notesTable.update(updates) {
                filter { eq("id", noteId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Supprimer une note
    suspend fun deleteNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            notesTable.delete {
                filter { eq("id", noteId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Changer le statut favori
    suspend fun toggleFavorite(noteId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            notesTable.update(
                mapOf("is_favorite" to isFavorite)
            ) {
                filter { eq("id", noteId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Flow pour les changements en temps réel
    fun observeNotes(): Flow<List<Note>> = flow {
        while (true) {
            emit(getAllNotes())
            kotlinx.coroutines.delay(2000) // Polling toutes les 2 secondes
        }
    }
}
