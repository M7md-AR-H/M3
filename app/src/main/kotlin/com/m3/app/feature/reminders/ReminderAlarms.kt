package com.m3.app.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The two alarms M³ ever holds: the next reminder and the end of the current rest. Each replaces its own last one. */
@Singleton
class ReminderAlarms @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** [due] travels with the alarm as local time, so a clock change on the way can't turn it into another reminder. */
    fun setNext(due: LocalDateTime) {
        val intent = receiverIntent(ReminderReceiver.ACTION_DUE).putExtra(ReminderReceiver.EXTRA_DUE, due.toString())
        set(due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending(intent, NEXT_REQUEST))
    }

    fun cancelNext() = alarms.cancel(pending(receiverIntent(ReminderReceiver.ACTION_DUE), NEXT_REQUEST))

    fun setRest(atMillis: Long) = set(atMillis, pending(receiverIntent(ReminderReceiver.ACTION_REST), REST_REQUEST))

    fun cancelRest() = alarms.cancel(pending(receiverIntent(ReminderReceiver.ACTION_REST), REST_REQUEST))

    /** Fires through Doze. Exact where allowed; a late reminder beats none if the system ever takes that away. */
    private fun set(atMillis: Long, operation: PendingIntent) {
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun receiverIntent(action: String) = Intent(context, ReminderReceiver::class.java).setAction(action)

    private fun pending(intent: Intent, request: Int) =
        PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val NEXT_REQUEST = 1
        const val REST_REQUEST = 2
    }
}
