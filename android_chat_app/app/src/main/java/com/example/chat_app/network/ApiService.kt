package com.example.chat_app.network

import com.example.chat_app.network.requests.AuthRequest
import com.example.chat_app.network.responses.AuthResponse
import com.example.chat_app.network.responses.UserResponse // Import UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET // Import GET
import retrofit2.http.POST

interface ApiService {
    @POST("/register")
    suspend fun registerUser(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/login")
    suspend fun loginUser(@Body request: AuthRequest): Response<AuthResponse>

    @GET("/users") // New endpoint
    suspend fun getUsers(): Response<List<UserResponse>>
}
