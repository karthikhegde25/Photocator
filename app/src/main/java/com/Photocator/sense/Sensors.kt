package com.Photocator.sense

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

/** Magnetic field strength (µT) and smoothed compass heading (degrees, 0 = magnetic north). */
class SensorHub(ctx: Context) {
    private val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _magnetic = MutableStateFlow<Double?>(null)
    private val _heading = MutableStateFlow<Double?>(null)
    val magnetic: StateFlow<Double?> get() = _magnetic
    val heading: StateFlow<Double?> get() = _heading

    private var running = false
    private var lastMagAt = 0L
    private var lastHeadAt = 0L
    private var smoothX = 0.0
    private var smoothY = 0.0
    private var haveSmooth = false
    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orient = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(e: SensorEvent) {
            val now = System.currentTimeMillis()
            when (e.sensor.type) {
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    if (now - lastMagAt < 1000) return
                    lastMagAt = now
                    val x = e.values[0].toDouble(); val y = e.values[1].toDouble(); val z = e.values[2].toDouble()
                    _magnetic.value = sqrt(x * x + y * y + z * z)
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rot, e.values)
                    val flat = abs(rot[8]) > 0.75f
                    val m = if (flat) rot else {
                        SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                        remapped
                    }
                    SensorManager.getOrientation(m, orient)
                    val az = Math.toRadians(((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0))
                    // circular low-pass: each sample moves the average 25% of the way
                    val sx = cos(az); val sy = sin(az)
                    if (!haveSmooth) { smoothX = sx; smoothY = sy; haveSmooth = true }
                    else { smoothX += (sx - smoothX) * 0.25; smoothY += (sy - smoothY) * 0.25 }
                    if (now - lastHeadAt < 500) return
                    lastHeadAt = now
                    val deg = (Math.toDegrees(atan2(smoothY, smoothX)) + 360.0) % 360.0
                    _heading.value = deg
                }
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        haveSmooth = false
        mgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { mgr.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        mgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { mgr.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        if (!running) return
        running = false
        mgr.unregisterListener(listener)
    }
}

/** Phone tilt with hysteresis: +1 top points right, -1 top points left, 0 portrait. */
class TiltTracker(private val ctx: Context) {
    private val _tilt = MutableStateFlow(0)
    val tilt: StateFlow<Int> get() = _tilt

    private val listener = object : OrientationEventListener(ctx) {
        override fun onOrientationChanged(angle: Int) {
            if (angle == ORIENTATION_UNKNOWN) return
            _tilt.value = when (angle) {
                in 60..120 -> 1
                in 240..300 -> -1
                in 0..29, in 331..359, in 150..210 -> 0
                else -> _tilt.value
            }
        }
    }

    fun start() { if (listener.canDetectOrientation()) listener.enable() }
    fun stop() { listener.disable() }
}
