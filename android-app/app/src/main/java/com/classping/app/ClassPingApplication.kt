package com.classping.app

import android.app.Application
import androidx.work.*
import com.classping.app.notifications.NotificationHelper
import com.classping.app.workers.SyncWorker
import java.util.concurrent.TimeUnit

class ClassPingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        )
    }
}
