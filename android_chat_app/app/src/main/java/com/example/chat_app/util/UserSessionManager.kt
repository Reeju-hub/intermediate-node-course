package com.example.chat_app.util // Create this sub-package

import android.content.Context
import android.content.SharedPreferences

object UserSessionManager {
    private const val PREF_NAME = "ChatAppSession"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username" // To store username

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveUser(context: Context, userId: Int, username: String) {
        val editor = getPreferences(context).edit()
        editor.putInt(KEY_USER_ID, userId)
        editor.putString(KEY_USERNAME, username)
        editor.apply()
    }

    fun getUserId(context: Context): Int? {
        val id = getPreferences(context).getInt(KEY_USER_ID, -1)
        return if (id == -1) null else id
    }
    
    fun getUsername(context: Context): String? {
        return getPreferences(context).getString(KEY_USERNAME, null)
    }

    fun clearSession(context: Context) {
        val editor = getPreferences(context).edit()
        editor.clear()
        editor.apply()
    }
}
