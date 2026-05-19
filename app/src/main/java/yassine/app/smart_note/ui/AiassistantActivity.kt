package yassine.app.smart_note.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null
    private var isRecording = false
    private val RECORD_AUDIO_REQUEST = 101
    private var originalMicTint: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiassistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()

        addWelcomeMessage()
        syncNotesWithBackend()
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

        // Hold-to-record behavior
        originalMicTint = binding.btnMic.backgroundTintList
        binding.btnMic.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST)
                    } else {
                        binding.btnMic.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, yassine.app.smart_note.R.color.accent_purple))
                        startRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    binding.btnMic.backgroundTintList = originalMicTint
                    true
                }
                else -> false
            }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, "Permission audio requise pour enregistrer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    stopRecording()
                    Toast.makeText(this@AiAssistantActivity, "Couldn't hear that, try again", Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        binding.etMessage.setText(text)
                        sendMessage(text)
                    } else {
                        Toast.makeText(this@AiAssistantActivity, "Couldn't hear that, try again", Toast.LENGTH_SHORT).show()
                    }
                    stopRecording()
                }

                override fun onPartialResults(partialResults: Bundle) {
                    val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.etMessage.setText(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(recognitionIntent)
            isRecording = true
            try {
                binding.btnMic.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, yassine.app.smart_note.R.color.accent_purple))
            } catch (e: Exception) {
                binding.btnMic.alpha = 0.9f
            }
        } else {
            Toast.makeText(this, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecording = false
        try {
            binding.btnMic.backgroundTintList = originalMicTint
        } catch (e: Exception) {
            binding.btnMic.alpha = 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
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
}
