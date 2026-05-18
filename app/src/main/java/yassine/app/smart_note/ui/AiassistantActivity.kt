package yassine.app.smart_note.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import yassine.app.smart_note.adapters.ChatAdapter
import yassine.app.smart_note.databinding.ActivityAiassistantBinding
import yassine.app.smart_note.models.ChatMessage
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Constants
import yassine.app.smart_note.utils.Resource
import yassine.app.smart_note.viewmodel.AiViewModel

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiassistantBinding
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private val viewModel: AiViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiViewModel(SmartNoteRepository.getInstance(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiassistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        addWelcomeMessage()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Assistant"
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@AiAssistantActivity)
            adapter = chatAdapter
        }
    }

    private fun setupObservers() {
        viewModel.responseState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.btnSend.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSend.isEnabled = true

                    val aiMessage = ChatMessage(resource.data?.response ?: "Désolé, je n'ai pas compris.", isUser = false)
                    messages.add(aiMessage)
                    chatAdapter.updateMessages(messages)
                    binding.rvChat.scrollToPosition(messages.size - 1)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSend.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()

                    val errorMessage = ChatMessage("⚠️ Erreur: ${resource.message}", isUser = false)
                    messages.add(errorMessage)
                    chatAdapter.updateMessages(messages)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val question = binding.etMessage.text.toString().trim()
            if (question.isNotEmpty()) {
                sendMessage(question)
            }
        }

        binding.btnClearChat.setOnClickListener {
            messages.clear()
            addWelcomeMessage()
            chatAdapter.updateMessages(messages)
            Toast.makeText(this, "Chat effacé", Toast.LENGTH_SHORT).show()
        }

        // Quick action chips
        binding.chipGenerateNote.setOnClickListener {
            binding.etMessage.setText("Génère une note sur ")
            binding.etMessage.requestFocus()
        }

        binding.chipSummarize.setOnClickListener {
            binding.etMessage.setText("Résume ce texte: ")
            binding.etMessage.requestFocus()
        }

        binding.chipImproveWriting.setOnClickListener {
            binding.etMessage.setText("Améliore ce texte: ")
            binding.etMessage.requestFocus()
        }

        binding.chipTodoList.setOnClickListener {
            binding.etMessage.setText("Crée une to-do list pour ")
            binding.etMessage.requestFocus()
        }
    }

    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(Constants.WELCOME_MESSAGE, isUser = false)
        messages.add(welcomeMessage)
        chatAdapter.updateMessages(messages)
    }

    private fun sendMessage(question: String) {
        // Ajouter la question de l'utilisateur
        val userMessage = ChatMessage(question, isUser = true)
        messages.add(userMessage)
        chatAdapter.updateMessages(messages)
        binding.etMessage.text.clear()
        binding.rvChat.scrollToPosition(messages.size - 1)

        // Envoyer à l'API
        viewModel.sendMessage(question)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
