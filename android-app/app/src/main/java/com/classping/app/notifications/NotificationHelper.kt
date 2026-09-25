package com.classping.app.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.classping.app.MainActivity
import com.classping.app.data.EventType
import com.classping.app.data.ReminderTone
import com.classping.app.R

object NotificationHelper {
    const val CHANNEL = "school_reminders"
    fun createChannels(c: Context) {
        val channel = NotificationChannel(CHANNEL, "School reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tests, submissions and important school reminders"; enableVibration(true)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun show(c: Context, id: String, name: String, type: EventType, subject: String, title: String, topic: String, tone: ReminderTone) {
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val content = when (tone) {
            ReminderTone.FUNNY -> if (type == EventType.NOTEBOOK_SUBMISSION) "Oye $name, $subject notebook kal submit karni hai. Abhi bag mein rakh le 😂" else "Oye $name, padhle pagal 😭 kal $subject ka ${topic.ifBlank { title }} hai."
            ReminderTone.NORMAL -> "Reminder: $subject ${topic.ifBlank { title }} is coming up."
            ReminderTone.SIMPLE -> "$subject · ${topic.ifBlank { title }}"
        }
        val open = PendingIntent.getActivity(c, 0, android.content.Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("ClassPing 🔔")
            .setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content)).setAutoCancel(true).setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(c).notify(id.hashCode() and 0x7fffffff, n)
    }
}
