package com.voxbite.app.model

data class UserIntent(
    val action: String,
    val app: String,
    val items: List<OrderItem> = emptyList(),
    val destination: String? = null,
    val rawText: String = ""
)

data class OrderItem(
    val name: String,
    val quantity: Int = 1,
    val size: String? = null
)