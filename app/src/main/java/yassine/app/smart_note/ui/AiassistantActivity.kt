package yassine.app.smart_note.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import yassine.app.smart_note.adapters.ChatAdapter
import yassine.app.smart_note.databinding.ActivityAiassistantBinding
import yassine.app.smart_note.models.ChatMessage
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Constants

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiassistantBinding
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private val repository by lazy { SmartNoteRepository.getInstance(applicationContext) }

    private var noteContent: String? = null
    private var noteTitle: String? = null

    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        val recognizedText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()

        if (!recognizedText.isNullOrBlank()) {
            binding.etMessage.setText(recognizedText)
            binding.etMessage.setSelection(binding.etMessage.text.length)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiassistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteContent = intent.getStringExtra("note_content")
        noteTitle = intent.getStringExtra("note_title")

        setupRecyclerView()
        setupClickListeners()

        addWelcomeMessage()
        syncNotesWithBackend()
        handleIncomingNoteContext()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@AiAssistantActivity)
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val question = binding.etMessage.text.toString().trim()
            if (question.isNotEmpty()) {
                sendMessage(question)
            }
        }

        binding.btnMic.setOnClickListener {
            startVoiceInput()
        }

        // Quick action chips
        binding.chipSummarize.setOnClickListener {
            binding.etMessage.setText("Génère une note sur ")
            binding.etMessage.requestFocus()
        }

        binding.chipImproveWriting.setOnClickListener {
            binding.etMessage.setText("Résume ce texte: ")
            binding.etMessage.requestFocus()
        }

        binding.chipFindIdeas.setOnClickListener {
            binding.etMessage.setText("Donne des idées pour ")
            binding.etMessage.requestFocus()
        }

        binding.chipTranslate.setOnClickListener {
            binding.etMessage.setText("Traduis ce texte: ")
            binding.etMessage.requestFocus()
        }

        binding.chipMakeList.setOnClickListener {
            binding.etMessage.setText("Crée une to-do list pour ")
            binding.etMessage.requestFocus()
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle pour dicter ton message")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        val canHandleSpeech = intent.resolveActivity(packageManager)
        if (canHandleSpeech == null) {
            Toast.makeText(
                this,
                "Aucune app de dictée n'est installée. Installe Google Speech Services ou utilise le clavier.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        speechRecognitionLauncher.launch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(Constants.WELCOME_MESSAGE, isUser = false)
        messages.add(welcomeMessage)
        chatAdapter.updateMessages(messages)
    }

    private fun syncNotesWithBackend() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val notes = repository.getAllNotes()
                val syncedCount = repository.syncNotesToBackend(notes)
                if (syncedCount > 0) {
                    android.util.Log.d("AiAssistantActivity", "Synced $syncedCount note(s) to backend")
                }
            } catch (e: Exception) {
                android.util.Log.e("AiAssistantActivity", "Failed to sync notes to backend", e)
            }
        }
    }

    private fun sendMessage(question: String) {
        // Ajouter la question de l'utilisateur
        val userMessage = ChatMessage(question, isUser = true)
        messages.add(userMessage)
        chatAdapter.updateMessages(messages)
        binding.etMessage.text.clear()
        binding.rvChat.scrollToPosition(messages.size - 1)

        binding.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = repository.sendAIMessage(question)
                val answer = response.answer ?: "Désolé, je n'ai pas compris."
                withContext(Dispatchers.Main) {
                    binding.btnSend.isEnabled = true
                    val aiMessage = ChatMessage(answer, isUser = false)
                    messages.add(aiMessage)
                    chatAdapter.updateMessages(messages)
                    binding.rvChat.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSend.isEnabled = true
                    Toast.makeText(this@AiAssistantActivity, e.message ?: "Erreur IA", Toast.LENGTH_LONG).show()
                    val errorMessage = ChatMessage("⚠️ Erreur: ${e.message ?: "Erreur IA"}", isUser = false)
                    messages.add(errorMessage)
                    chatAdapter.updateMessages(messages)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun handleIncomingNoteContext() {
        if (!noteContent.isNullOrBlank()) {

            val prompt = """
                Aide-moi avec cette note :
                
                Titre: ${noteTitle ?: "Sans titre"}
                
                Contenu:
                $noteContent
                
                Donne :
                - résumé
                - amélioration
                - idées
                """.trimIndent()

            sendMessage(prompt)
        }
    }
}
