package yassine.app.smart_note.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import yassine.app.smart_note.R
import yassine.app.smart_note.databinding.ActivityAddNoteBinding
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.utils.Resource
import yassine.app.smart_note.viewmodel.AddNoteViewModel

class AddNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddNoteBinding
    private var selectedColor = "#FFFFFF"
    private val viewModel: AddNoteViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AddNoteViewModel(SmartNoteRepository.getInstance(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupObservers()
        setupColorSelection()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "New Note"
    }

    private fun setupColorSelection() {
        val colorListener = View.OnClickListener { view ->
            resetColorBorders()
            selectedColor = when (view.id) {
                R.id.color_white -> "#FFFFFF"
                R.id.color_red -> "#FFCDD2"
                R.id.color_blue -> "#BBDEFB"
                R.id.color_green -> "#C8E6C9"
                R.id.color_yellow -> "#FFF9C4"
                R.id.color_purple -> "#E1BEE7"
                else -> "#FFFFFF"
            }
            view.setBackgroundResource(R.drawable.color_selected_border)
        }

        binding.colorWhite.setOnClickListener(colorListener)
        binding.colorRed.setOnClickListener(colorListener)
        binding.colorBlue.setOnClickListener(colorListener)
        binding.colorGreen.setOnClickListener(colorListener)
        binding.colorYellow.setOnClickListener(colorListener)
        binding.colorPurple.setOnClickListener(colorListener)
    }

    private fun resetColorBorders() {
        binding.colorWhite.setBackgroundResource(R.drawable.color_circle)
        binding.colorRed.setBackgroundResource(R.drawable.color_circle)
        binding.colorBlue.setBackgroundResource(R.drawable.color_circle)
        binding.colorGreen.setBackgroundResource(R.drawable.color_circle)
        binding.colorYellow.setBackgroundResource(R.drawable.color_circle)
        binding.colorPurple.setBackgroundResource(R.drawable.color_circle)
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val content = binding.etContent.text.toString().trim()

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(this, "Please enter some content", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.saveNote(title, content, selectedColor)
            }
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        viewModel.saveState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnSave.isEnabled = false
                }
                is Resource.Success -> {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this, "Note saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }

            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
