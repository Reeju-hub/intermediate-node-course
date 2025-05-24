package com.example.chat_app.network.responses

data class AuthResponse(
    val message: String,
    val user_id: Int? = null, // Nullable if not always present (e.g. registration success)
    val username: String? = null // Include username from backend on login success
)
