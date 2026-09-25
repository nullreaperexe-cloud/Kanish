package com.classping.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.classping.app.data.*
import com.classping.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val prefs = PreferencesStore(applicationContext).preferences.first()
        val events = ClassPingRepository().observeEvents(prefs).first().first
        ReminderScheduler(applicationContext).reconcile(events, prefs)
        ClassPingRepository().saveFcmToken(applicationContext)
        Result.success()
    } catch (_: Throwable) { Result.retry() }
    companion object { const val NAME="classping_periodic_sync"; const val BOOT_NAME="classping_boot_sync"; const val PUSH_NAME="classping_push_sync" }
}
