package yassine.app.smart_note.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import yassine.app.smart_note.api.RetrofitInstance
import yassine.app.smart_note.models.BackendNoteRequest
import yassine.app.smart_note.models.AskRequest
import yassine.app.smart_note.models.AskResponse
import yassine.app.smart_note.models.Note

class SmartNoteRepository private constructor(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val sharedPreferences = context.getSharedPreferences("SmartNotePrefs", Context.MODE_PRIVATE)
    private val syncedBackendNotesKey = "synced_backend_notes"

    companion object {
        @Volatile
        private var INSTANCE: SmartNoteRepository? = null

        fun getInstance(context: Context): SmartNoteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartNoteRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun requireUserId(): String {
        val user = auth.currentUser ?: throw IllegalStateException("Utilisateur non connecté")
        return user.uid
    }

    // ==================== AUTHENTIFICATION ====================

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getUserName(): String {
        val user = auth.currentUser
        return user?.displayName ?: user?.email?.substringBefore("@") ?: "Utilisateur"
    }

    fun getUserEmail(): String {
        return auth.currentUser?.email ?: ""
    }

    // ==================== NOTES (FIREBASE REALTIME DB) ====================

    suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        val snapshot = database.child("users").child(userId).child("notes").get().await()
        snapshot.children.mapNotNull { it.getValue(Note::class.java) }
    }

    suspend fun getFavoriteNotes(): List<Note> = withContext(Dispatchers.IO) {
        getAllNotes().filter { it.isFavorite }
    }

    suspend fun searchNotes(query: String): List<Note> = withContext(Dispatchers.IO) {
        val lower = query.lowercase()
        getAllNotes().filter { note ->
            note.title.lowercase().contains(lower) || note.content.lowercase().contains(lower)
        }
    }

    suspend fun addNote(title: String, content: String, noteType: String = "Personal"): Note = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        val noteId = database.push().key ?: System.currentTimeMillis().toString()
        val now = System.currentTimeMillis()
        val note = Note(
            id = noteId,
            userId = userId,
            title = title.ifEmpty { "Sans titre" },
            content = content,
            isFavorite = false,
            noteType = noteType,
            createdAt = now,
            updatedAt = now
        )
        database.child("users").child(userId).child("notes").child(noteId).setValue(note).await()
        note
    }

    suspend fun updateNote(note: Note): Note = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "title" to note.title,
            "content" to note.content,
            "noteType" to note.noteType,
            "isFavorite" to note.isFavorite,
            "updatedAt" to now
        )
        database.child("users").child(userId).child("notes").child(note.id).updateChildren(updates).await()
        note.copy(updatedAt = now)
    }

    suspend fun deleteNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        database.child("users").child(userId).child("notes").child(noteId).removeValue().await()
        true
    }

    suspend fun toggleFavorite(noteId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        database.child("users").child(userId).child("notes").child(noteId).child("isFavorite").setValue(isFavorite).await()
        true
    }

    // ==================== AI ASSISTANT ====================

    suspend fun syncNotesToBackend(notes: List<Note>): Int = withContext(Dispatchers.IO) {
        val syncedIds = getSyncedBackendNoteIds().toMutableSet()
        var syncedCount = 0

        notes.forEach { note ->
            val syncKey = note.syncKey()
            if (syncedIds.contains(syncKey)) {
                return@forEach
            }

            val textToIndex = note.toBackendText()
            if (textToIndex.isBlank()) {
                return@forEach
            }

            try {
                RetrofitInstance.api.pushNote(BackendNoteRequest(text = textToIndex))
                syncedIds.add(syncKey)
                syncedCount++
            } catch (e: Exception) {
                android.util.Log.e("SmartNoteRepository", "Failed syncing note ${note.id}", e)
            }
        }

        saveSyncedBackendNoteIds(syncedIds)
        syncedCount
    }

    suspend fun sendAIMessage(question: String): AskResponse = withContext(Dispatchers.IO) {
        try {
            val request = AskRequest(question = question)
            android.util.Log.d("SmartNoteRepository", "Sending AI request to /ask")
            android.util.Log.d("SmartNoteRepository", "AI question payload: $question")
            RetrofitInstance.api.sendMessage(request)
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("SmartNoteRepository", "AI request failed", e)
            AskResponse(answer = getFallbackResponse(question))
        }
    }

    private fun getFallbackResponse(question: String): String {
        return when {
            question.contains("note", ignoreCase = true) &&
                    (question.contains("génère", ignoreCase = true) || question.contains("genere", ignoreCase = true)) -> {
                "📝 **Note générée:**\n\nVoici une note basée sur votre demande:\n\n---\n${question.replace("génère", "").replace("genere", "").replace("note", "").trim()}\n\n---\n\n💡 Souhaitez-vous sauvegarder cette note?"
            }
            question.contains("résumé", ignoreCase = true) ||
                    question.contains("resume", ignoreCase = true) -> {
                "📄 **Résumé:**\n\n• Premier point important\n• Deuxième point clé\n• Troisième élément essentiel\n\n---\n\nVoulez-vous que je développe un point particulier?"
            }
            question.contains("améliore", ignoreCase = true) ||
                    question.contains("amelior", ignoreCase = true) -> {
                "✏️ **Texte amélioré:**\n\nVersion optimisée avec une meilleure grammaire, un vocabulaire plus riche et une structure plus claire.\n\n**Améliorations:**\n• Correction grammaticale\n• Enrichissement lexical\n• Meilleure fluidité"
            }
            question.contains("todo", ignoreCase = true) ||
                    question.contains("liste", ignoreCase = true) -> {
                "✅ **To-Do List générée:**\n\n□ **Priorité haute:** Tâche principale\n□ **Priorité moyenne:** Tâches secondaires\n□ **Priorité basse:** Points optionnels\n\n---\n\nCommencez par les priorités hautes!"
            }
            else -> {
                "🤖 **Assistant IA - Smart Note**\n\nJe peux vous aider avec:\n\n📝 **Générer une note**\n   \"Génère une note sur le développement mobile\"\n\n📄 **Résumer un texte**\n   \"Résume ce texte: [votre texte]\"\n\n✏️ **Améliorer l'écriture**\n   \"Améliore ce texte: [votre texte]\"\n\n✅ **Créer une to-do list**\n   \"Crée une liste pour mon projet\"\n\nComment puis-je vous aider aujourd'hui?"
            }
        }
    }

    private fun getSyncedBackendNoteIds(): Set<String> {
        return sharedPreferences.getStringSet(syncedBackendNotesKey, emptySet()) ?: emptySet()
    }

    private fun saveSyncedBackendNoteIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(syncedBackendNotesKey, ids).apply()
    }

    private fun Note.syncKey(): String = "$id:$updatedAt"

    private fun Note.toBackendText(): String {
        val titlePart = title.trim()
        val contentPart = content.trim()
        return listOf(titlePart, contentPart).filter { it.isNotBlank() }.joinToString("\n")
    }
}