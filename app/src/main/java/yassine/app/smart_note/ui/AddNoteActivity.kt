package yassine.app.smart_note.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
    private var selectedColor = "#12121A"
    private var isEdit = false
    private var editingNoteId: String? = null

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

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            processImageForText(cameraImageUri!!)
        } else {
            Toast.makeText(this, "Couldn't capture image", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            processImageForText(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if launched for editing
        isEdit = intent.getBooleanExtra("is_edit", false)
        if (isEdit) {
            editingNoteId = intent.getStringExtra("note_id")
            binding.etTitle.setText(intent.getStringExtra("note_title") ?: "")
            binding.etContent.setText(intent.getStringExtra("note_content") ?: "")
            selectedColor = intent.getStringExtra("note_color") ?: selectedColor
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
                        color = selectedColor
                    )
                    viewModel.updateNote(note)
                } else {
                    viewModel.saveNote(title, content, selectedColor)
                }
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAskAi.setOnClickListener {
            // Logic for AI help
        }

        binding.btnImage.setOnClickListener {
            showImagePickerDialog()
        }
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
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> launchCameraWithPermission()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingCameraLaunch) {
                    pendingCameraLaunch = false
                    launchCamera()
                }
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 201
    }
}
