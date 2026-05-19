package yassine.app.smart_note.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import yassine.app.smart_note.databinding.ActivityAddNoteBinding
import yassine.app.smart_note.models.NoteType
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Resource
import yassine.app.smart_note.viewmodel.AddNoteViewModel
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddNoteBinding
    private var selectedNoteType = NoteType.PERSONAL
    private var isEdit = false
    private var editingNoteId: String? = null

    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    private val viewModel: AddNoteViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AddNoteViewModel(SmartNoteRepository.getInstance(applicationContext)) as T
            }
        }
    }

    // Image capture / selection
    private var cameraImageUri: Uri? = null
    private var pendingCameraLaunch = false

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                processImageForText(cameraImageUri!!)
            } else {
                Toast.makeText(this, "Couldn't capture image", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                processImageForText(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()

        // Check if launched for editing
        isEdit = intent.getBooleanExtra("is_edit", false)
        if (isEdit) {
            editingNoteId = intent.getStringExtra("note_id")
            binding.etTitle.setText(intent.getStringExtra("note_title") ?: "")
            binding.etContent.setText(intent.getStringExtra("note_content") ?: "")
            intent.getStringExtra("note_type")?.let { type ->
                selectedNoteType = NoteType.values().find { it.value == type }
                    ?: NoteType.PERSONAL
            }
            binding.tvSave.text = "Update"
        }

        setupObservers()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.tvSave.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val content = binding.etContent.text.toString().trim()

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(this, "Please enter some content", Toast.LENGTH_SHORT).show()
            } else {
                if (isEdit && editingNoteId != null) {
                    val note = yassine.app.smart_note.models.Note(
                        id = editingNoteId!!,
                        title = title,
                        content = content,
                        noteType = selectedNoteType.value
                    )
                    viewModel.updateNote(note)
                } else {
                    viewModel.saveNote(title, content, selectedNoteType.value)
                }
            }
        }


        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAskAi.setOnClickListener {
            val noteTitle = binding.etTitle.text.toString().trim()
            val noteText = binding.etContent.text.toString().trim()

            if (noteText.isEmpty()) {
                Toast.makeText(this, "Note vide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, AiAssistantActivity::class.java).apply {
                putExtra("note_content", noteText)
                putExtra("note_title", noteTitle)
            }

            startActivity(intent)
        }

        binding.btnImage.setOnClickListener {
            showImagePickerDialog()
        }

        binding.btnMic.setOnClickListener {
            if (isListening) {
                stopSpeechRecognition()
            } else {
                startVoiceInput()
            }
        }
        binding.btnNoteType.setOnClickListener {
            showNoteTypeDialog()
        }
    }
    private fun showNoteTypeDialog() {
        val types = NoteType.values()

        val names = types.map { it.value }.toTypedArray()

        val currentIndex = types.indexOf(selectedNoteType)

        AlertDialog.Builder(this)
            .setTitle("Select Note Type")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                selectedNoteType = types[which]
                Toast.makeText(this, "Type: ${selectedNoteType.value}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }


    private fun setupObservers() {
        viewModel.saveState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.tvSave.isEnabled = false
                }

                is Resource.Success -> {
                    binding.tvSave.isEnabled = true
                    Toast.makeText(this, "Note saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }

                is Resource.Error -> {
                    binding.tvSave.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }

                is Resource.Idle -> {
                    binding.tvSave.isEnabled = true
                }
            }
        }

        viewModel.updateState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.tvSave.isEnabled = false
                }

                is Resource.Success -> {
                    binding.tvSave.isEnabled = true
                    Toast.makeText(this, "Note updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }

                is Resource.Error -> {
                    binding.tvSave.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }

                is Resource.Idle -> {
                    binding.tvSave.isEnabled = true
                }
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCameraWithPermission()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            pendingCameraLaunch = true
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        try {
            val file = createImageFile()
            cameraImageUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            takePictureLauncher.launch(cameraImageUri)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val storageDir = File(cacheDir, "images")
        if (!storageDir.exists()) storageDir.mkdirs()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(storageDir, "IMG_${timeStamp}.jpg")
    }

    private fun processImageForText(uri: Uri) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromFilePath(this, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extracted = visionText.text.trim()
                    if (extracted.isNotEmpty()) {
                        val current = binding.etContent.text.toString()
                        binding.etContent.setText(if (current.isBlank()) extracted else "$current\n$extracted")
                    } else {
                        Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to extract text", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (pendingCameraLaunch) {
                        pendingCameraLaunch = false
                        launchCamera()
                    }
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }

            RECORD_AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSpeechRecognition()
                } else {
                    Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(
                    this@AddNoteActivity,
                    "Error: Couldn't hear that, try again",
                    Toast.LENGTH_SHORT
                ).show()
                stopSpeechRecognition()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    val current = binding.etContent.text.toString()
                    binding.etContent.setText(if (current.isBlank()) recognizedText else "$current $recognizedText")
                }
                stopSpeechRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice input is not available on this device", Toast.LENGTH_SHORT)
                .show()
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

    private fun startSpeechRecognition() {
        if (isListening) return

        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something...")
        speechRecognizer.startListening(intent)
    }

    private fun stopSpeechRecognition() {
        if (isListening) {
            speechRecognizer.stopListening()
            speechRecognizer.cancel()
        }
        isListening = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 201
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 202
    }
}