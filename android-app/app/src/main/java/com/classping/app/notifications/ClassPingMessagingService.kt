package com.classping.app.notifications

import com.classping.app.data.ClassPingRepository
import com.classping.app.workers.SyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.work.*

class ClassPingMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { Thread { kotlinx.coroutines.runBlocking { ClassPingRepository().saveFcmToken(applicationContext, token) } }.start() }
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["action"] == "events_updated") WorkManager.getInstance(this).enqueueUniqueWork(
            SyncWorker.PUSH_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().build()
        )
    }
}
