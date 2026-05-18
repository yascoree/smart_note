package yassine.app.smart_note

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import yassine.app.smart_note.databinding.ActivityMainBinding
import yassine.app.smart_note.repository.SmartNoteRepository
import yassine.app.smart_note.ui.HomeActivity
import yassine.app.smart_note.ui.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val repository = SmartNoteRepository.getInstance(applicationContext)

        // Show splash screen briefly then navigate
        Handler(Looper.getMainLooper()).postDelayed({
            if (repository.isLoggedIn()) {
                // User is signed in, go to Home
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                // No user is signed in, go to Login
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 1200)
    }
}
