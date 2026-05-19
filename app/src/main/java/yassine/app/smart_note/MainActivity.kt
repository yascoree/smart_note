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

        supportActionBar?.hide()

        Handler(Looper.getMainLooper()).postDelayed({
            val destination = if (SmartNoteRepository.getInstance(this).isLoggedIn()) {
                HomeActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(Intent(this, destination))
            finish()
        }, 1500)
    }
}
