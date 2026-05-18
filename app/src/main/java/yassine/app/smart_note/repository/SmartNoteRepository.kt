package yassine.app.smart_note.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import yassine.app.smart_note.api.RetrofitInstance
import yassine.app.smart_note.models.AskRequest
import yassine.app.smart_note.models.AskResponse
import yassine.app.smart_note.models.Note
import java.util.UUID

class SmartNoteRepository private constructor(context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val supabaseRepo = SupabaseNoteRepository()

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

    // ==================== NOTES (SUPABASE) ====================

    suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
        supabaseRepo.getAllNotes(requireUserId())
    }

    suspend fun getFavoriteNotes(): List<Note> = withContext(Dispatchers.IO) {
        supabaseRepo.getFavoriteNotes(requireUserId())
    }

    suspend fun searchNotes(query: String): List<Note> = withContext(Dispatchers.IO) {
        supabaseRepo.searchNotes(requireUserId(), query)
    }

    suspend fun addNote(title: String, content: String, color: String): Note = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            userId = requireUserId(),
            title = title.ifEmpty { "Sans titre" },
            content = content,
            color = color,
            isFavorite = false,
            createdAt = now,
            updatedAt = now
        )
        supabaseRepo.addNote(note)
    }

    suspend fun updateNote(note: Note): Note = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "title" to note.title,
            "content" to note.content,
            "color" to note.color,
            "is_favorite" to note.isFavorite,
            "updated_at" to now
        )
        supabaseRepo.updateNote(requireUserId(), note.id, updates)
    }

    suspend fun deleteNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        supabaseRepo.deleteNote(requireUserId(), noteId)
    }

    suspend fun toggleFavorite(noteId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        supabaseRepo.toggleFavorite(requireUserId(), noteId, isFavorite)
    }

    fun observeNotes(): Flow<List<Note>> {
        return supabaseRepo.observeNotes(requireUserId())
    }

    // ==================== AI ASSISTANT ====================

    suspend fun sendAIMessage(question: String): AskResponse = withContext(Dispatchers.IO) {
        val notes = getAllNotes()
        if (shouldAnswerFromLocalNotes(question)) {
            return@withContext AskResponse(getNotesBasedResponse(question, notes))
        }

        try {
            val contextualQuestion = buildQuestionWithNotes(question, notes)
            val request = AskRequest(contextualQuestion)
            RetrofitInstance.api.sendMessage(request)
        } catch (e: Exception) {
            e.printStackTrace()
            AskResponse(getFallbackResponse(question, notes))
        }
    }

    private fun buildQuestionWithNotes(question: String, notes: List<Note>): String {
        if (notes.isEmpty()) return question

        val contextBlock = notes
            .take(20)
            .joinToString("\n") { note ->
                val compactContent = note.content.replace("\n", " ").trim()
                "- ${note.title.ifBlank { "Sans titre" }}: ${compactContent.take(250)}"
            }

        return buildString {
            appendLine("Question utilisateur:")
            appendLine(question)
            appendLine()
            appendLine("Contexte notes utilisateur (réponds en te basant sur ces notes):")
            append(contextBlock)
        }
    }

    private fun shouldAnswerFromLocalNotes(question: String): Boolean {
        val lower = question.lowercase()
        return lower.contains("mes notes") ||
            lower.contains("my notes") ||
            lower.contains("quelles notes") ||
            lower.contains("what notes") ||
            lower.contains("résume mes notes") ||
            lower.contains("resume mes notes") ||
            lower.contains("summarize my notes") ||
            (lower.contains("summary") && lower.contains("notes"))
    }

    private fun getNotesBasedResponse(question: String, notes: List<Note>): String {
        if (notes.isEmpty()) {
            return "Vous n'avez pas encore ajouté de notes. Ajoutez une note puis demandez-moi un résumé."
        }

        val lower = question.lowercase()
        val sortedNotes = notes.sortedByDescending { it.updatedAt }

        if (lower.contains("résume") || lower.contains("resume") || lower.contains("summary") || lower.contains("summar")) {
            val favorites = sortedNotes.count { it.isFavorite }
            val lines = sortedNotes.take(8).joinToString("\n") { note ->
                val preview = note.content.replace("\n", " ").trim().take(120)
                "• ${note.title.ifBlank { "Sans titre" }}: ${if (preview.isBlank()) "(sans contenu)" else preview}"
            }
            return "Voici le résumé de vos notes:\n\nTotal: ${sortedNotes.size} note(s)\nFavoris: $favorites\n\n$lines"
        }

        val list = sortedNotes.take(12).joinToString("\n") { note ->
            "• ${note.title.ifBlank { "Sans titre" }}"
        }
        return "Voici vos notes récentes:\n\n$list"
    }

    private fun getFallbackResponse(question: String, notes: List<Note>): String {
        val lower = question.lowercase()
        if (lower.contains("mes notes") || lower.contains("my notes")) {
            return getNotesBasedResponse(question, notes)
        }

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
}
