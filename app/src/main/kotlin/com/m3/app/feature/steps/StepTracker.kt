package com.m3.app.feature.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.m3.app.di.ApplicationScope
import com.m3.core.data.StepRepository
import com.m3.core.model.StepReading
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Reads the phone's step counter while M³ is in the foreground. Android doesn't send this sensor's events to
 * background apps, but the counter runs on regardless, so the next reading catches up.
 */
@Singleton
class StepTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val steps: StepRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val hasSensor: Boolean = sensor != null

    private val _permitted = MutableStateFlow(isPermitted())
    val permitted: StateFlow<Boolean> = _permitted.asStateFlow()

    private var job: Job? = null

    /** Called when the app comes to the front, and after the owner answers the permission prompt. */
    @Synchronized
    fun start() {
        _permitted.value = isPermitted()
        val counter = sensor
        if (counter == null || !_permitted.value || job?.isActive == true) return
        job = scope.launch {
            // A write per step would be wasteful; conflate keeps only the newest value between writes.
            readings(counter).conflate().collect { value ->
                try {
                    steps.record(StepReading(value, System.currentTimeMillis()))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Nothing is lost: the sensor keeps counting, so the next reading includes these steps.
                }
                delay(WRITE_EVERY_MILLIS)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun readings(counter: Sensor): Flow<Long> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors?.registerListener(listener, counter, SensorManager.SENSOR_DELAY_NORMAL)
        // Without this the first value could wait for the next step.
        sensors?.flush(listener)
        awaitClose { sensors?.unregisterListener(listener) }
    }

    private fun isPermitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val WRITE_EVERY_MILLIS = 5_000L
    }
}
