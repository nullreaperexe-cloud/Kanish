package com.classping.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("classping_preferences")

class PreferencesStore(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("name"); val className = stringPreferencesKey("class_name"); val section = stringPreferencesKey("section")
        val theme = stringPreferencesKey("theme"); val enabled = booleanPreferencesKey("notifications_enabled"); val tests = booleanPreferencesKey("test_alerts")
        val submissions = booleanPreferencesKey("submission_alerts"); val hours = intPreferencesKey("reminder_hours"); val tone = stringPreferencesKey("tone")
        val complete = booleanPreferencesKey("onboarding_complete")
    }
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            name = p[K.name] ?: "Student", className = p[K.className] ?: "8", section = p[K.section] ?: "A",
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            notificationsEnabled = p[K.enabled] ?: true, testAlerts = p[K.tests] ?: true,
            submissionAlerts = p[K.submissions] ?: true, reminderHours = p[K.hours] ?: 24,
            tone = runCatching { ReminderTone.valueOf(p[K.tone] ?: "FUNNY") }.getOrDefault(ReminderTone.FUNNY),
            onboardingComplete = p[K.complete] ?: false
        )
    }
    suspend fun save(v: UserPreferences) = context.dataStore.edit { p ->
        p[K.name]=v.name; p[K.className]=v.className; p[K.section]=v.section; p[K.theme]=v.theme.name
        p[K.enabled]=v.notificationsEnabled; p[K.tests]=v.testAlerts; p[K.submissions]=v.submissionAlerts
        p[K.hours]=v.reminderHours; p[K.tone]=v.tone.name; p[K.complete]=v.onboardingComplete
    }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}
