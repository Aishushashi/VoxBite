package com.voxbite.app.model

data class UserIntent(
    val action: String,
    val app: String,
    val items: List<OrderItem> = emptyList(),
    val destination: String? = null,
    val rawText: String = "",
    val errorMessage: String? = null,
    val budget: Int? = null,       // ← new: e.g. 150 for "under ₹150"
    val category: String? = null   // ← new: e.g. "light", "spicy"
) {
    companion object {
        fun error(message: String) = UserIntent(
            action = "error",
            app = "none",
            errorMessage = message
        )
    }
}

data class OrderItem(
    val name: String,
    val quantity: Int = 1,
    val size: String? = null
)