package com.classping.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

enum class EventType { TEST, NOTEBOOK_SUBMISSION, HOMEWORK, ASSIGNMENT, PROJECT, EXAM, PRACTICAL, IMPORTANT, OTHER }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class ReminderTone { FUNNY, NORMAL, SIMPLE }

data class SchoolEvent(
    val id: String,
    val type: EventType = EventType.OTHER,
    val subject: String = "School",
    val title: String = "School reminder",
    val topic: String = "",
    val eventAt: Instant? = null,
    val dateLabel: String = "Date to be confirmed",
    val priority: String = "normal",
    val needsReview: Boolean = false,
    val status: String = "active",
    val className: String = "",
    val section: String = ""
)

fun DocumentSnapshot.toSchoolEvent(): SchoolEvent {
    fun text(key: String, fallback: String = "") = getString(key)?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
    val rawType = text("type", "other").lowercase()
    val type = EventType.entries.firstOrNull { it.name.lowercase() == rawType } ?: EventType.OTHER
    val timestamp = getTimestamp("eventDate") ?: (get("eventDate") as? Timestamp)
    return SchoolEvent(
        id = id, type = type, subject = text("subject", "School"), title = text("title", "School reminder"),
        topic = text("topic"), eventAt = timestamp?.toDate()?.toInstant(), dateLabel = text("dateLabel", "Date to be confirmed"),
        priority = text("priority", "normal"), needsReview = getBoolean("needsReview") ?: false,
        status = text("status", "active"), className = text("className"), section = text("section")
    )
}

data class UserPreferences(
    val name: String = "Student", val className: String = "8", val section: String = "A",
    val theme: ThemeMode = ThemeMode.SYSTEM, val notificationsEnabled: Boolean = true,
    val testAlerts: Boolean = true, val submissionAlerts: Boolean = true,
    val reminderHours: Int = 24, val tone: ReminderTone = ReminderTone.FUNNY,
    val onboardingComplete: Boolean = false
)

data class AppConfig(
    val appName: String = "ClassPing", val defaultReminderHoursBefore: Int = 24,
    val maintenanceMode: Boolean = false, val minSupportedVersion: String = "1.0.0",
    val submissionAlertsEnabled: Boolean = true, val testAlertsEnabled: Boolean = true
)

data class AppState(
    val loading: Boolean = true, val user: UserPreferences = UserPreferences(),
    val events: List<SchoolEvent> = emptyList(), val config: AppConfig = AppConfig(),
    val error: String? = null, val offline: Boolean = false
)
