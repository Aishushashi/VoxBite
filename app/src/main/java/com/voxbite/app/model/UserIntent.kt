package com.voxbite.app.model

data class OrderItem(
    val name: String,
    val quantity: Int = 1,
    val size: String? = null
)

data class UserIntent(
    val action: String,
    val app: String = "unknown",
    val items: List<OrderItem> = emptyList(),
    val destination: String? = null,
    val rawText: String = "",
    val budget: Int? = null,
    val category: String? = null,
    val isCorrection: Boolean = false,
    val detectedLanguage: String = "en-IN",      // NEW
    val confirmationMessage: String = ""          // NEW
) {
    companion object {
        fun error(reason: String = "Something went wrong.") = UserIntent(
            action = "error",
            confirmationMessage = reason,
            detectedLanguage = "en-IN"
        )
    }
}