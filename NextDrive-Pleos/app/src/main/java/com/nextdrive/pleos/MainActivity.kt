package com.nextdrive.pleos

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import com.nextdrive.pleos.vehicle.ReadSupport
import com.nextdrive.pleos.vehicle.ReadingState
import com.nextdrive.pleos.vehicle.UnavailableVehicleDataSource
import com.nextdrive.pleos.vehicle.VehicleDataOrigin
import com.nextdrive.pleos.vehicle.VehicleDataSource
import com.nextdrive.pleos.vehicle.VehicleReading
import com.nextdrive.pleos.vehicle.VehicleSignal
import com.nextdrive.pleos.vehicle.VehicleStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Reusable vehicle-status screen; no service-specific features or vehicle control. */
class MainActivity : Activity() {
    private var source: VehicleDataSource? = null
    private var requestGeneration = 0
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val automotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        findViewById<TextView>(R.id.platform_status).text = getString(
            if (automotive) R.string.platform_automotive else R.string.platform_not_automotive
        )
    }

    override fun onStart() {
        super.onStart()
        val generation = ++requestGeneration
        // No SDK adapter is selected until dependencies and API signatures are verified.
        source = UnavailableVehicleDataSource().also { dataSource ->
            dataSource.readStatus { status ->
                runOnUiThread {
                    if (generation == requestGeneration) render(status)
                }
            }
        }
    }

    override fun onStop() {
        ++requestGeneration // Ignore late callbacks from a previous screen session.
        source?.close()
        source = null
        super.onStop()
    }

    private fun render(status: VehicleStatus) {
        val originLabel = when (status.origin) {
            VehicleDataOrigin.NOT_CONNECTED -> R.string.origin_not_connected
            VehicleDataOrigin.PLEOS_EMULATOR -> R.string.origin_emulator
            VehicleDataOrigin.PLEOS_VEHICLE -> R.string.origin_vehicle
            VehicleDataOrigin.MOCK -> R.string.origin_mock
        }
        findViewById<TextView>(R.id.data_origin).setText(originLabel)
        val rowIds = mapOf(
            VehicleSignal.FRONT_LEFT_DOOR to R.id.door_status,
            VehicleSignal.FUEL to R.id.fuel_status,
            VehicleSignal.ODOMETER to R.id.odometer_status,
        )
        rowIds.forEach { (signal, viewId) ->
            val reading = status.readings.firstOrNull { it.signal == signal }
                ?: VehicleReading(signal, ReadSupport.NOT_CHECKED, ReadingState.WAITING)
            findViewById<TextView>(viewId).text = formatReading(reading)
        }
    }

    private fun formatReading(reading: VehicleReading): String {
        val support = getString(when (reading.support) {
            ReadSupport.NOT_CHECKED -> R.string.support_not_checked
            ReadSupport.READABLE -> R.string.support_readable
            ReadSupport.UNSUPPORTED -> R.string.support_unavailable
            ReadSupport.PERMISSION_DENIED -> R.string.support_permission_denied
        })
        val value = when (reading.state) {
            ReadingState.AVAILABLE -> reading.displayValue ?: getString(R.string.value_unavailable)
            ReadingState.DISCONNECTED -> getString(R.string.value_disconnected)
            ReadingState.WAITING -> getString(R.string.value_waiting)
            ReadingState.FAILED -> getString(R.string.value_failed)
            ReadingState.STALE -> getString(
                R.string.value_stale, reading.displayValue ?: getString(R.string.value_unavailable)
            )
        }
        val time = reading.receivedAt?.let(timeFormatter::format)
            ?: getString(R.string.time_not_received)
        return getString(R.string.reading_format, support, value, time)
    }
}
