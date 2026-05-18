package yassine.app.smart_note.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import yassine.app.smart_note.databinding.ActivitySignupBinding
import yassine.app.smart_note.firebase.FirebaseAuthService
import yassine.app.smart_note.repository.FirebaseAuthRepository
import yassine.app.smart_note.utils.Resource
import yassine.app.smart_note.viewmodel.AuthViewModel

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var authService: FirebaseAuthService
    private lateinit var authRepository: FirebaseAuthRepository
    private val viewModel: AuthViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(authRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authService = FirebaseAuthService(this)
        authRepository = FirebaseAuthRepository(authService)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.signupState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.btnSignup.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSignup.isEnabled = true
                    if (resource.data != null) {
                        Toast.makeText(this, "Inscription réussie!", Toast.LENGTH_SHORT).show()
                        navigateToHome()
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSignup.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSignup.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
            } else if (password.length < 6) {
                Toast.makeText(this, "Mot de passe trop court (min 6 caractères)", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.signup(email, password)
            }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}