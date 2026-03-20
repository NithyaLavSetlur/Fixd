package com.example.fixd

enum class ProblemArea(
    val menuItemId: Int,
    val titleRes: Int,
    val subtitleRes: Int,
    val iconRes: Int
) {
    WAKE_UP(R.id.tab_wake_up, R.string.problem_wake_up, R.string.problem_wake_up_subtitle, R.drawable.ic_tab_wake_up),
    SLEEP_SCHEDULE(
        R.id.tab_sleep_schedule,
        R.string.problem_sleep_schedule,
        R.string.problem_sleep_schedule_subtitle,
        R.drawable.ic_tab_sleep_schedule
    ),
    TIME_MANAGEMENT(
        R.id.tab_time_management,
        R.string.problem_time_management,
        R.string.problem_time_management_subtitle,
        R.drawable.ic_tab_time_management
    ),
    TRANSPORT(
        R.id.tab_transport,
        R.string.problem_transport,
        R.string.problem_transport_subtitle,
        R.drawable.ic_tab_transport
    ),
    PLACEHOLDER(
        R.id.tab_placeholder,
        R.string.problem_placeholder,
        R.string.problem_placeholder_subtitle,
        R.drawable.ic_tab_placeholder
    );

    companion object {
        fun fromName(name: String): ProblemArea? = entries.firstOrNull { it.name == name }
        fun fromMenuItemId(itemId: Int): ProblemArea? = entries.firstOrNull { it.menuItemId == itemId }
    }
}
