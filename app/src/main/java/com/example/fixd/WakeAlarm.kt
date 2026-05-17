package com.example.fixd

data class WakeAlarm(
    val id: String = "",
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: String = KIND_STANDARD,
    val triggerAtMillis: Long = 0L,
    val sleepDurationHours: Float = 0f
) {
    fun isSleepDurationAlarm(): Boolean = kind == KIND_SLEEP_DURATION

    companion object {
        const val KIND_STANDARD = "standard"
        const val KIND_SLEEP_DURATION = "sleep_duration"
        const val SLEEP_DURATION_ALARM_ID = "sleep_duration_alarm"
    }
}
