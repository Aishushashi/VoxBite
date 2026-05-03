package com.voxbite.app.model

data class OrderItem(val name: String, val quantity: Int = 1)

data class UserIntent(
    val action: String,
    val app: String,
    val items: List<OrderItem>,
    val destination: String?,
    val budget: Int?,
    val category: String?,
    val confirmationMessage: String,
    val detectedLanguage: String,
    val isCorrection: Boolean,
    val chainData: ChainData? = null
) {
    data class ChainData(
        val foodItems: List<OrderItem>,
        val foodApp: String,
        val destination: String,
        val cabApp: String
    )

    companion object {
        fun error(message: String) = UserIntent(
            action              = "error",
            app                 = "",
            items               = emptyList(),
            destination         = null,
            budget              = null,
            category            = null,
            confirmationMessage = message,
            detectedLanguage    = "en-IN",
            isCorrection        = false,
            chainData           = null
        )
    }
}