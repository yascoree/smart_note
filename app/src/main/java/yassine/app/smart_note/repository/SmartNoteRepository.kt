package yassine.app.smart_note.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yassine.app.smart_note.api.RetrofitInstance
import yassine.app.smart_note.models.AskRequest
import yassine.app.smart_note.models.AskResponse
import yassine.app.smart_note.models.Note
import yassine.app.smart_note.utils.Constants
import java.util.UUID

class SmartNoteRepository private constructor(context: Context) {

    private val sharedPref: SharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_NOTES_JSON = "notes_json"

        @Volatile
        private var INSTANCE: SmartNoteRepository? = null

        fun getInstance(context: Context): SmartNoteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartNoteRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ==================== AUTHENTIFICATION ====================

    suspend fun login(email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            // Pour le développement - à remplacer par Firebase/API plus tard
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

    suspend fun signup(email: String, password: String, fullName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val resolvedName = fullName.ifBlank { email.substringBefore("@").ifBlank { "Utilisateur" } }
            val isValid = email.contains("@") && password.length >= 4
            if (isValid) {
                sharedPref.edit().apply {
                    putBoolean(Constants.KEY_IS_LOGGED_IN, true)
                    putString(Constants.KEY_USER_EMAIL, email)
                    putString(Constants.KEY_USER_NAME, resolvedName)
                    apply()
                }
            }
            isValid
        }
    }

    fun logout() {
        sharedPref.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return sharedPref.getBoolean(Constants.KEY_IS_LOGGED_IN, false)
    }

    fun getUserName(): String {
        return sharedPref.getString(Constants.KEY_USER_NAME, "Utilisateur") ?: "Utilisateur"
    }

    fun getUserEmail(): String {
        return sharedPref.getString(Constants.KEY_USER_EMAIL, "") ?: ""
    }

    // ==================== NOTES (Local Storage) ====================

    private val notesList = loadNotesFromStorage()

    init {
        // Ajouter quelques notes de démo
        if (notesList.isEmpty()) {
            notesList.addAll(
                listOf(
                    Note(
                        id = UUID.randomUUID().toString(),
                        title = "Bienvenue sur Smart Note",
                        content = "Bienvenue dans votre application de notes intelligente! Appuyez sur + pour créer votre première note.",
                        color = "#FFFFFF",
                        isFavorite = true
                    ),
                    Note(
                        id = UUID.randomUUID().toString(),
                        title = "Assistant IA",
                        content = "Utilisez l'assistant IA pour générer, résumer ou améliorer vos notes.",
                        color = "#BBDEFB",
                        isFavorite = false
                    ),
                    Note(
                        id = UUID.randomUUID().toString(),
                        title = "Conseil d'utilisation",
                        content = "Vous pouvez rechercher vos notes, les organiser par favoris, et les personnaliser avec différentes couleurs.",
                        color = "#C8E6C9",
                        isFavorite = false
                    )
                )
            )
            saveNotesToStorage()
        }
    }

    private fun loadNotesFromStorage(): MutableList<Note> {
        val json = sharedPref.getString(KEY_NOTES_JSON, null) ?: return mutableListOf()
        return try {
            val listType = object : TypeToken<List<Note>>() {}.type
            (gson.fromJson<List<Note>>(json, listType) ?: emptyList()).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveNotesToStorage() {
        val json = gson.toJson(notesList)
        sharedPref.edit().putString(KEY_NOTES_JSON, json).apply()
    }

    fun getAllNotes(): List<Note> = notesList.toList()

    fun getFavoriteNotes(): List<Note> = notesList.filter { it.isFavorite }

    fun searchNotes(query: String): List<Note> {
        return if (query.isBlank()) {
            notesList.toList()
        } else {
            notesList.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }
    }

    suspend fun addNote(title: String, content: String, color: String): Note {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = title.ifEmpty { "Sans titre" },
                content = content,
                color = color,
                createdAt = now,
                updatedAt = now
            )
            notesList.add(0, note)
            saveNotesToStorage()
            note
        }
    }

    suspend fun updateNote(note: Note): Note {
        return withContext(Dispatchers.IO) {
            val index = notesList.indexOfFirst { it.id == note.id }
            if (index != -1) {
                val updatedNote = note.copy(updatedAt = System.currentTimeMillis())
                notesList[index] = updatedNote
                saveNotesToStorage()
                updatedNote
            } else {
                note
            }
        }
    }

    suspend fun deleteNote(noteId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val removed = notesList.removeAll { it.id == noteId }
            if (removed) {
                saveNotesToStorage()
            }
            removed
        }
    }

    suspend fun toggleFavorite(noteId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val index = notesList.indexOfFirst { it.id == noteId }
            if (index != -1) {
                val newStatus = !notesList[index].isFavorite
                notesList[index] = notesList[index].copy(isFavorite = newStatus, updatedAt = System.currentTimeMillis())
                saveNotesToStorage()
                newStatus
            } else {
                false
            }
        }
    }

    // ==================== AI ASSISTANT ====================

    suspend fun sendAIMessage(question: String): AskResponse {
        return withContext(Dispatchers.IO) {
            if (shouldAnswerFromLocalNotes(question)) {
                return@withContext AskResponse(getNotesBasedResponse(question))
            }

            try {
                val contextualQuestion = buildQuestionWithNotes(question)
                val request = AskRequest(contextualQuestion)
                val response = RetrofitInstance.api.sendMessage(request)
                response
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback response when backend is not available
                AskResponse(getFallbackResponse(question))
            }
        }
    }

    private fun buildQuestionWithNotes(question: String): String {
        if (notesList.isEmpty()) return question

        val contextBlock = notesList
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

    private fun getNotesBasedResponse(question: String): String {
        if (notesList.isEmpty()) {
            return "Vous n'avez pas encore ajouté de notes. Ajoutez une note puis demandez-moi un résumé."
        }

        val lower = question.lowercase()
        val sortedNotes = notesList.sortedByDescending { it.updatedAt }

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

    private fun getFallbackResponse(question: String): String {
        val lower = question.lowercase()
        if (lower.contains("mes notes") || lower.contains("my notes")) {
            return getNotesBasedResponse(question)
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
