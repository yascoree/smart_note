package yassine.app.smart_note.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import yassine.app.smart_note.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUserInfo()
        setupBackButton()
        setupLogoutButton()
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
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            finish()
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
