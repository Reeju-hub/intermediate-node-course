package com.example.chat_app

package com.example.chat_app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.widget.Toast
import com.example.chat_app.databinding.ActivityRegisterBinding
import com.example.chat_app.network.RetrofitClient
import com.example.chat_app.network.requests.AuthRequest
import com.example.chat_app.network.responses.AuthResponse // Ensure AuthResponse is imported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val username = binding.etRegisterUsername.text.toString().trim()
            val password = binding.etRegisterPassword.text.toString()
            val confirmPassword = binding.etRegisterConfirmPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoadingState(true) // Start loading

            val authRequest = AuthRequest(username, password)
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.instance.registerUser(authRequest)
                    }
                    if (response.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, response.body()?.message ?: "Registration Successful", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown registration error"
                        Log.e("RegisterActivity", "Error: $errorBody, Code: ${response.code()}")
                        val responseMessage = try {
                            RetrofitClient.instance.instance.getConverterFactories().get(AuthResponse::class.java, arrayOfNulls(0))
                                ?.responseBodyConverter(AuthResponse::class.java, arrayOfNulls(0), null)
                                ?.convert(response.errorBody()!!)?.message
                        } catch (e: Exception) { null }
                        Toast.makeText(this@RegisterActivity, "Registration failed: ${responseMessage ?: errorBody}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("RegisterActivity", "Exception: ${e.message}", e)
                    Toast.makeText(this@RegisterActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    setLoadingState(false) // Stop loading
                }
            }
        }

        binding.tvGoToLogin.setOnClickListener {
            finish() 
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.pbLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        binding.etRegisterUsername.isEnabled = !isLoading
        binding.etRegisterPassword.isEnabled = !isLoading
        binding.etRegisterConfirmPassword.isEnabled = !isLoading
        binding.tvGoToLogin.isEnabled = !isLoading // Optional: disable this too
    }
}
