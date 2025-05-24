package com.example.chat_app

package com.example.chat_app

package com.example.chat_app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.widget.Toast
import com.example.chat_app.databinding.ActivityLoginBinding
import com.example.chat_app.network.RetrofitClient
import com.example.chat_app.network.requests.AuthRequest
import com.example.chat_app.network.responses.AuthResponse // Ensure AuthResponse is imported
import com.example.chat_app.util.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etLoginUsername.text.toString().trim()
            val password = binding.etLoginPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoadingState(true) // Start loading

            val authRequest = AuthRequest(username, password)
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val response = withContext(Dispatchers.IO) { 
                        RetrofitClient.instance.loginUser(authRequest) 
                    }
                    if (response.isSuccessful && response.body()?.user_id != null) {
                        val authData = response.body()!!
                        UserSessionManager.saveUser(this@LoginActivity, authData.user_id!!, authData.username ?: username)
                        Toast.makeText(this@LoginActivity, "Login Successful. Welcome ${authData.username ?: username}!", Toast.LENGTH_LONG).show()
                        
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish() 
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown login error"
                        Log.e("LoginActivity", "Error: $errorBody, Code: ${response.code()}")
                        val responseMessage = try {
                             RetrofitClient.instance.instance.getConverterFactories().get(AuthResponse::class.java, arrayOfNulls(0))
                                ?.responseBodyConverter(AuthResponse::class.java, arrayOfNulls(0), null)
                                ?.convert(response.errorBody()!!)?.message
                        } catch (e: Exception) { null }
                        Toast.makeText(this@LoginActivity, "Login failed: ${responseMessage ?: errorBody}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Exception: ${e.message}", e)
                    Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    setLoadingState(false) // Stop loading
                }
            }
        }

        binding.tvGoToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.pbLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.etLoginUsername.isEnabled = !isLoading
        binding.etLoginPassword.isEnabled = !isLoading
        binding.tvGoToRegister.isEnabled = !isLoading // Optional: disable this too
    }
}
