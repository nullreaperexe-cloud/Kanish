package com.classping.app.notifications

import android.content.*
import androidx.work.*
import com.classping.app.data.EventType
import com.classping.app.data.ReminderTone
import com.classping.app.workers.SyncWorker

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = NotificationHelper.show(
        c, i.getStringExtra("id") ?: return, i.getStringExtra("name") ?: "Student",
        runCatching { EventType.valueOf(i.getStringExtra("type") ?: "OTHER") }.getOrDefault(EventType.OTHER),
        i.getStringExtra("subject") ?: "School", i.getStringExtra("title") ?: "Reminder", i.getStringExtra("topic") ?: "",
        runCatching { ReminderTone.valueOf(i.getStringExtra("tone") ?: "NORMAL") }.getOrDefault(ReminderTone.NORMAL)
    )
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        WorkManager.getInstance(c).enqueueUniqueWork(SyncWorker.BOOT_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().build())
    }
}
