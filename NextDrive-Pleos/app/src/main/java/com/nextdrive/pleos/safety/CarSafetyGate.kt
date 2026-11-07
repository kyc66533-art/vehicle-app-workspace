package com.nextdrive.pleos.safety

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

enum class EditingState { UNKNOWN, ALLOWED, RESTRICTED }

/** Uses platform UX restrictions only. No guessed gear/speed values or manual safety override. */
class CarSafetyGate(private val context: Context, private val changed: (EditingState) -> Unit) {
    var state = EditingState.UNKNOWN
        private set
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var generation = 0
    private var connectionGeneration = 0

    fun start() {
        val currentGeneration = ++generation
        publish(EditingState.UNKNOWN)
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) || Build.VERSION.SDK_INT < 29) return
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (currentGeneration != generation) return@createCar
                val connection = ++connectionGeneration
                if (!ready) { manager = null; publish(EditingState.UNKNOWN); return@createCar }
                try {
                    manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    manager?.registerListener { restrictions ->
                        if (currentGeneration == generation && connection == connectionGeneration) apply(restrictions)
                    }
                    val restrictions = manager?.currentCarUxRestrictions
                    if (restrictions == null) publish(EditingState.UNKNOWN) else apply(restrictions)
                } catch (_: Exception) { publish(EditingState.UNKNOWN) }
            }
        } catch (_: Exception) { publish(EditingState.UNKNOWN) }
        catch (_: LinkageError) { publish(EditingState.UNKNOWN) }
    }
    fun stop() {
        ++generation
        ++connectionGeneration
        try { manager?.unregisterListener() } catch (_: Exception) { }
        try { car?.disconnect() } catch (_: Exception) { }
        manager = null
        car = null
        state = EditingState.UNKNOWN
    }
    private fun apply(restrictions: CarUxRestrictions) {
        publish(if (!restrictions.isRequiresDistractionOptimization &&
            restrictions.activeRestrictions == CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
            EditingState.ALLOWED else EditingState.RESTRICTED)
    }
    private fun publish(next: EditingState) { state = next; changed(next) }
}
