package com.example.chat_app.ui.adapter // Create this sub-package

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_app.databinding.ItemUserBinding // ViewBinding for item_user.xml
// import com.example.chat_app.network.responses.UserResponse // Will be created in next step, for now a placeholder

// Placeholder data class for user display, actual one will come from network response
data class UserDisplay(val id: Int, val username: String)

class UserAdapter(
    private var users: List<UserDisplay>,
    private val onItemClicked: (UserDisplay) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<UserDisplay>) {
        this.users = newUsers
        notifyDataSetChanged() // In a real app, use DiffUtil for better performance
    }

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: UserDisplay) {
            binding.tvUsername.text = user.username
            binding.root.setOnClickListener {
                onItemClicked(user)
            }
        }
    }
}
