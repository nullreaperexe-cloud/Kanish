package com.classping.app.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classping.app.data.*
import java.time.Instant

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun reconcile(events: List<SchoolEvent>, prefs: UserPreferences) {
        if (!prefs.notificationsEnabled) { events.forEach(::cancel); return }
        events.forEach { event ->
            val allowed = when (event.type) { EventType.TEST, EventType.EXAM -> prefs.testAlerts; EventType.NOTEBOOK_SUBMISSION -> prefs.submissionAlerts; else -> true }
            if (allowed) schedule(event, prefs) else cancel(event)
        }
    }
    fun schedule(event: SchoolEvent, prefs: UserPreferences) {
        val at = event.eventAt?.minusSeconds(prefs.reminderHours * 3600L) ?: return
        if (at <= Instant.now()) return
        val pending = pendingIntent(event, prefs, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
    }
    fun cancel(event: SchoolEvent) = alarms.cancel(pendingIntent(event, UserPreferences(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return)
    private fun pendingIntent(e: SchoolEvent, p: UserPreferences, flags: Int): PendingIntent? {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.classping.app.REMIND.${e.id}"; putExtra("id", e.id); putExtra("name", p.name)
            putExtra("type", e.type.name); putExtra("subject", e.subject); putExtra("title", e.title); putExtra("topic", e.topic); putExtra("tone", p.tone.name)
        }
        return PendingIntent.getBroadcast(context, e.id.hashCode() and 0x7fffffff, i, flags)
    }
}
