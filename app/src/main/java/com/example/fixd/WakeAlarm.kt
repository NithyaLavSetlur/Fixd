package com.example.fixd

data class WakeAlarm(
    val id: String = "",
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
