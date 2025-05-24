package com.example.chat_app

package com.example.chat_app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chat_app.databinding.ActivityMainBinding
import com.example.chat_app.network.RetrofitClient
import com.example.chat_app.network.responses.UserResponse
import com.example.chat_app.ui.adapter.UserAdapter
import com.example.chat_app.ui.adapter.UserDisplay
import com.example.chat_app.util.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userAdapter: UserAdapter
    private var currentUserId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = UserSessionManager.getUserId(this)
        if (currentUserId == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        val currentUsername = UserSessionManager.getUsername(this)
        binding.tvMainTitle.text = "Welcome, ${currentUsername ?: "User"}!"

        setupRecyclerView()
        setupLogoutButton()
        fetchUsers()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(emptyList()) { user ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("RECIPIENT_ID", user.id)
                putExtra("RECIPIENT_USERNAME", user.username)
            }
            startActivity(intent)
        }
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            UserSessionManager.clearSession(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun fetchUsers() {
        binding.pbUserListLoading.visibility = View.VISIBLE // Start loading
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getUsers()
                }
                if (response.isSuccessful) {
                    val usersFromApi = response.body() ?: emptyList()
                    val displayUsers = usersFromApi
                        .filter { it.id != currentUserId } 
                        .map { userResponse -> 
                            UserDisplay(id = userResponse.id, username = userResponse.username) 
                        }
                    userAdapter.updateUsers(displayUsers)
                } else {
                    Log.e("MainActivity", "Error fetching users: ${response.errorBody()?.string()}")
                    Toast.makeText(this@MainActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception fetching users: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.pbUserListLoading.visibility = View.GONE // Stop loading
            }
        }
    }
}
