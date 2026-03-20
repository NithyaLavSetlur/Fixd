package com.example.fixd

data class WakeSubmission(
    val alarmId: String = "",
    val type: String = "",
    val text: String = "",
    val imagePath: String = "",
    val verdict: String = "",
    val feedback: String = "",
    val alarmHour: Int = 0,
    val alarmMinute: Int = 0,
    val triggeredAt: Long = 0L,
    val completedAt: Long = 0L,
    val responseDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
