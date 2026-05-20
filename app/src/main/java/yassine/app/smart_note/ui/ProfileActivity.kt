package yassine.app.smart_note.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yassine.app.smart_note.databinding.ActivityProfileBinding
import yassine.app.smart_note.repository.SmartNoteRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var repository: SmartNoteRepository
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SmartNoteRepository.getInstance(applicationContext)
        sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setupUserInfo()
        loadNotesCount()
        setupBackButton()
        setupLogoutButton()
        setupNotificationsToggle()
    }

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null) {
            goToLoginAndClearTask()
        }
    }

    private fun setupUserInfo() {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName?.ifBlank { null }
            ?: user?.email?.substringBefore("@")
            ?: "User"
        val initials = name.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")

        binding.tvInitials.text = initials.ifBlank { "U" }
        binding.tvDisplayName.text = name
        binding.tvEmail.text = user?.email ?: "—"

        // Set joined date
        val creationTime = user?.metadata?.creationTimestamp ?: 0L
        if (creationTime > 0) {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            binding.tvJoined.text = sdf.format(Date(creationTime))
        } else {
            binding.tvJoined.text = "—"
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadNotesCount() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val notes = withContext(Dispatchers.IO) {
                    repository.getAllNotes()
                }
                binding.tvNotesCount.text = notes.size.toString()
            } catch (e: Exception) {
                binding.tvNotesCount.text = "0"
            }
        }
    }

    private fun setupNotificationsToggle() {
        // Load saved state
        val isEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        binding.switchNotifications.isChecked = isEnabled

        // Listen for changes
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("notifications_enabled", isChecked).apply()
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            goToLoginAndClearTask()
        }
    }

    private fun goToLoginAndClearTask() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
