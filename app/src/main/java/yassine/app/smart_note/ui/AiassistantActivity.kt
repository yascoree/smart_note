package yassine.app.smart_note.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import yassine.app.smart_note.R
import yassine.app.smart_note.adapters.ChatAdapter
import yassine.app.smart_note.databinding.ActivityAiassistantBinding
import yassine.app.smart_note.models.ChatMessage
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Constants

class AiAssistantActivity : AppCompatActivity() {

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 301
        private const val AI_REPLY_CHANNEL_ID = "ai_reply_channel"
        private const val AI_REPLY_NOTIFICATION_ID = 1001
    }

    private lateinit var binding: ActivityAiassistantBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private val messages = mutableListOf<ChatMessage>()
    private val repository by lazy { SmartNoteRepository.getInstance(applicationContext) }

    private var noteContent: String? = null
    private var noteTitle: String? = null
    private var isListening = false

    private val speechIntentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull() ?: return@registerForActivityResult
                val current = binding.etMessage.text.toString()
                binding.etMessage.setText(if (current.isBlank()) spoken else "$current $spoken")
                binding.etMessage.setSelection(binding.etMessage.text.length)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiassistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()
        // Replace click behavior with touch (hold-to-speak)
        binding.btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startSpeechRecognition()
                    } else {
                        requestPermissions(
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            RECORD_AUDIO_PERMISSION_REQUEST
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopSpeechRecognition()
                    true
                }
                else -> false
            }
        }

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

        // mic button handled via touch (hold-to-speak) in onCreate

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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "La dictée vocale n'est pas disponible sur cet appareil",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST
            )
            return
        }

        startSpeechRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpeechRecognizer() {
        // Prepare the listening intent
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle pour dicter ton message")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Toast.makeText(
                    this@AiAssistantActivity,
                    "Impossible d'entendre la saisie vocale, réessaie",
                    Toast.LENGTH_SHORT
                ).show()
                stopSpeechRecognition()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    val current = binding.etMessage.text.toString()
                    binding.etMessage.setText(
                        if (current.isBlank()) recognizedText else "$current $recognizedText"
                    )
                    binding.etMessage.setSelection(binding.etMessage.text.length)
                }
                stopSpeechRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun launchExternalSpeechUi() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try {
            speechIntentLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No speech input available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSpeechRecognition() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            launchExternalSpeechUi()
            return
        }

        isListening = true
        try {
            speechRecognizer.startListening(speechRecognizerIntent)
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "startListening failed", e)
            isListening = false
            launchExternalSpeechUi()
        }
    }

    private fun stopSpeechRecognition() {
        if (isListening) {
            speechRecognizer.stopListening()
            speechRecognizer.cancel()
        }
        isListening = false
    }

    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(Constants.WELCOME_MESSAGE, isUser = false)
        messages.add(welcomeMessage)
        chatAdapter.addMessage(welcomeMessage)
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
        val userMessage = ChatMessage(question, isUser = true)
        messages.add(userMessage)
        chatAdapter.addMessage(userMessage)

        val typingMessage = ChatMessage(text = "", isUser = false, isTyping = true)
        messages.add(typingMessage)
        chatAdapter.addMessage(typingMessage)
        binding.etMessage.text.clear()
        binding.rvChat.scrollToPosition(messages.size - 1)

        binding.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestNotes = repository.getAllNotes()
                val syncedNow = repository.syncNotesToBackend(latestNotes)
                android.util.Log.d(
                    "AiAssistantActivity",
                    "Before /ask synced $syncedNow note(s), total Firebase notes=${latestNotes.size}"
                )

                val response = repository.sendAIMessage(question)
                val answer = response.answer ?: "Désolé, je n'ai pas compris."
                val contextUsed = response.context_used.orEmpty()
                val answerWithContext = if (contextUsed.isEmpty()) {
                    answer
                } else {
                    "$answer\n\n---\nContext used (${contextUsed.size}):\n${contextUsed.joinToString("\n- ", prefix = "- ")}"
                }

                android.util.Log.d(
                    "AiAssistantActivity",
                    "AI response received. context_used_count=${contextUsed.size}"
                )

                withContext(Dispatchers.Main) {
                    binding.btnSend.isEnabled = true
                    messages.removeAll { it.isTyping }
                    chatAdapter.removeTypingMessage()
                    val aiMessage = ChatMessage(answerWithContext, isUser = false)
                    messages.add(aiMessage)
                    chatAdapter.addMessage(aiMessage)
                    chatAdapter.notifyDataSetChanged()
                    binding.rvChat.scrollToPosition(messages.size - 1)
                    showAiReplyNotification(answer)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSend.isEnabled = true
                    messages.removeAll { it.isTyping }
                    chatAdapter.removeTypingMessage()
                    Toast.makeText(this@AiAssistantActivity, e.message ?: "Erreur IA", Toast.LENGTH_LONG).show()
                    val errorMessage = ChatMessage("⚠️ Erreur: ${e.message ?: "Erreur IA"}", isUser = false)
                    messages.add(errorMessage)
                    chatAdapter.addMessage(errorMessage)
                    chatAdapter.notifyDataSetChanged()
                    binding.rvChat.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AI_REPLY_CHANNEL_ID,
                "AI replies",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when the AI replies to your message"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showAiReplyNotification(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openIntent = Intent(this, AiAssistantActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.logo_smart_note)

        val notification = NotificationCompat.Builder(this, AI_REPLY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo)
            .setLargeIcon(logoBitmap)
            .setContentTitle("AI reply received")
            .setContentText(message.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(AI_REPLY_NOTIFICATION_ID, notification)
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
