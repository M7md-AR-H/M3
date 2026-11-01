package com.m3.app.feature.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.m3.app.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Alarms, the notification's "+250 ml", and the system events after which alarms must be set again.
 * Not exported: only the system and M³'s own pending intents can reach it.
 */
class ReminderReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun engine(): ReminderEngine

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val engine = dependencies.engine()
        val result = goAsync()
        dependencies.scope().launch {
            try {
                when (intent.action) {
                    ACTION_DUE -> engine.onDue(dueTime(intent) ?: LocalDateTime.MIN)
                    ACTION_REST -> engine.onRestOver()
                    ACTION_WATER -> engine.addGlass()
                    else -> engine.reschedule()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show for it; the next start of M³ sets the alarm again.
            } finally {
                result.finish()
            }
        }
    }

    private fun dueTime(intent: Intent): LocalDateTime? = try {
        intent.getStringExtra(EXTRA_DUE)?.let(LocalDateTime::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        const val ACTION_DUE = "com.m3.app.action.REMINDER_DUE"
        const val ACTION_REST = "com.m3.app.action.REST_OVER"
        const val ACTION_WATER = "com.m3.app.action.ADD_WATER"
        const val EXTRA_DUE = "due"
    }
}
