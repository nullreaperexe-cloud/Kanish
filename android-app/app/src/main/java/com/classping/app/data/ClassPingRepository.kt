package com.classping.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ClassPingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    init { runCatching { db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build()).build() } }

    suspend fun ensureAnonymousUser(): String {
        val existing = auth.currentUser
        return existing?.uid ?: auth.signInAnonymously().await().user?.uid ?: error("Could not start a private ClassPing session")
    }

    fun observeEvents(profile: UserPreferences): Flow<Pair<List<SchoolEvent>, Boolean>> = callbackFlow {
        var registration: ListenerRegistration? = null
        try {
            ensureAnonymousUser()
            registration = db.collection("events").whereEqualTo("published", true)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                    if (error != null) { close(error); return@addSnapshotListener }
                    val items = snap?.documents.orEmpty().mapNotNull { runCatching { it.toSchoolEvent() }.getOrNull() }
                        .filter { it.status.equals("active", true) }
                        .filter { it.className.isBlank() || it.className.equals(profile.className, true) }
                        .filter { it.section.isBlank() || it.section.equals(profile.section, true) }
                        .sortedWith(compareBy(nullsLast()) { it.eventAt })
                    trySend(items to (snap?.metadata?.isFromCache == true))
                }
        } catch (t: Throwable) { close(t) }
        awaitClose { registration?.remove() }
    }

    fun observeConfig(): Flow<AppConfig> = callbackFlow {
        val registration = db.document("app_config/main").addSnapshotListener { d, _ ->
            trySend(AppConfig(
                appName=d?.getString("appName") ?: "ClassPing",
                defaultReminderHoursBefore=(d?.getLong("defaultReminderHoursBefore") ?: 24).toInt(),
                maintenanceMode=d?.getBoolean("maintenanceMode") ?: false,
                minSupportedVersion=d?.getString("minSupportedVersion") ?: "1.0.0",
                submissionAlertsEnabled=d?.getBoolean("submissionAlertsEnabled") ?: true,
                testAlertsEnabled=d?.getBoolean("testAlertsEnabled") ?: true
            ))
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveProfile(p: UserPreferences) {
        val uid = ensureAnonymousUser()
        db.collection("users").document(uid).set(mapOf(
            "name" to p.name, "className" to p.className, "section" to p.section, "theme" to p.theme.name.lowercase(),
            "notificationsEnabled" to p.notificationsEnabled, "testAlerts" to p.testAlerts,
            "submissionAlerts" to p.submissionAlerts, "reminderHoursBefore" to p.reminderHours,
            "reminderTone" to p.tone.name.lowercase(), "onboardingComplete" to p.onboardingComplete,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        ), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun saveFcmToken(context: android.content.Context, token: String? = null) {
        runCatching {
            val uid = ensureAnonymousUser(); val actual = token ?: FirebaseMessaging.getInstance().token.await()
            val id = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "device"
            db.collection("users").document(uid).collection("devices").document(id).set(mapOf(
                "fcmToken" to actual, "platform" to "android", "notificationsEnabled" to true,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ), com.google.firebase.firestore.SetOptions.merge()).await()
        }.onFailure { Log.w("ClassPing", "FCM token save deferred", it) }
    }
}
