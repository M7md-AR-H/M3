package com.m3.app.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.m3.app.MainActivity
import com.m3.app.R
import com.m3.core.model.ReminderKind
import com.m3.core.model.ReminderMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts reminders. They stay local (no bridging to other devices, no system-suggested actions) and show only
 * "M³" on a locked screen, because the shade can be read without M³'s own unlock.
 */
@Singleton
class ReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            manager.areNotificationsEnabled()

    fun post(message: ReminderMessage) {
        if (!canPost()) return
        createChannels()
        val builder = NotificationCompat.Builder(context, channelOf(message.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockedVersion(message.kind))
            .setContentIntent(open(message))
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setAllowSystemGeneratedContextualActions(false)
            .setTimeoutAfter(lifetimeMillis(message.kind))
        if (message.offersWater) builder.addAction(0, WATER_ACTION, waterIntent())
        try {
            manager.notify(notificationId(message.kind), builder.build())
        } catch (_: SecurityException) {
            // The permission went away between the check and here: nothing to show, nothing to do.
        }
    }

    fun cancel(kind: ReminderKind) = manager.cancel(notificationId(kind))

    private fun lockedVersion(kind: ReminderKind) = NotificationCompat.Builder(context, channelOf(kind))
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("M³")
        .setContentText("Tap to open")
        .build()

    private fun open(message: ReminderMessage): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, message.target.name)
        return PendingIntent.getActivity(
            context,
            message.kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun waterIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        WATER_REQUEST,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_WATER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** One channel per subject, so Android's own settings can silence water and keep workouts. */
    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_WORKOUT, "Workouts", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_MEALS, "Meals", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_WATER, "Water", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_DAILY, "Daily log", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_REST, "Rest timer", NotificationManager.IMPORTANCE_HIGH),
        )
        channels.forEach { it.lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE }
        manager.createNotificationChannels(channels)
    }

    private fun channelOf(kind: ReminderKind) = when (kind) {
        ReminderKind.WORKOUT -> CHANNEL_WORKOUT
        ReminderKind.BREAKFAST, ReminderKind.LUNCH, ReminderKind.DINNER -> CHANNEL_MEALS
        ReminderKind.WATER -> CHANNEL_WATER
        ReminderKind.REST -> CHANNEL_REST
        ReminderKind.MORNING, ReminderKind.EVENING, ReminderKind.LIGHTS_OUT, ReminderKind.WEEK_REVIEW -> CHANNEL_DAILY
    }

    /** A reminder that has gone stale removes itself, so the shade doesn't fill up over a busy day. */
    private fun lifetimeMillis(kind: ReminderKind): Long = when (kind) {
        ReminderKind.REST -> MINUTE * 5
        ReminderKind.WATER -> HOUR * 2
        ReminderKind.BREAKFAST, ReminderKind.LUNCH, ReminderKind.DINNER -> HOUR * 3
        else -> HOUR * 8
    }

    private fun notificationId(kind: ReminderKind) = FIRST_ID + kind.ordinal

    private companion object {
        const val FIRST_ID = 100
        const val WATER_REQUEST = 3
        const val WATER_ACTION = "+250 ml"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val CHANNEL_WORKOUT = "workouts"
        const val CHANNEL_MEALS = "meals"
        const val CHANNEL_WATER = "water"
        const val CHANNEL_DAILY = "daily"
        const val CHANNEL_REST = "rest"
    }
}
