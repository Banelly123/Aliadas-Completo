package com.aliadas.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aliadas.MainActivity
import com.aliadas.databinding.ActivityLoginBinding
import com.aliadas.network.LoginRequest
import com.aliadas.network.RegisterRequest
import com.aliadas.network.RetrofitClient
import com.aliadas.utils.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in, go to main
        if (SessionManager.isLoggedIn(this)) {
            startMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnAction.setOnClickListener { if (isLoginMode) doLogin() else doRegister() }
        binding.tvToggleMode.setOnClickListener { toggleMode() }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        if (isLoginMode) {
            binding.tilName.visibility = View.GONE
            binding.btnAction.text = "Iniciar sesión"
            binding.tvToggleMode.text = "¿No tienes cuenta? Regístrate"
            binding.tvTitle.text = "Bienvenida de vuelta"
        } else {
            binding.tilName.visibility = View.VISIBLE
            binding.btnAction.text = "Crear cuenta"
            binding.tvToggleMode.text = "¿Ya tienes cuenta? Inicia sesión"
            binding.tvTitle.text = "Únete a Aliadas"
        }
    }

    private fun doLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            showError("Completa todos los campos")
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = RetrofitClient.api.login(LoginRequest(email, password))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    SessionManager.saveSession(this@LoginActivity, body.token, body.userId, body.name, body.avatarIcon)
                    startMain()
                } else {
                    showError("Credenciales incorrectas")
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun doRegister() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (name.isEmpty() || email.isEmpty() || password.length < 6) {
            showError("Nombre, correo y contraseña mínimo 6 caracteres")
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = RetrofitClient.api.register(RegisterRequest(name, email, password))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    SessionManager.saveSession(this@LoginActivity, body.token, body.userId, body.name, body.avatarIcon)
                    startMain()
                } else {
                    showError("Este correo ya está registrado")
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnAction.isEnabled = !loading
    }
}
