package com.nextdrive.pleos.vehicle

import java.time.Instant

/** App-owned types; these are not Pleos SDK APIs. */
enum class VehicleSignal { FRONT_LEFT_DOOR, FUEL, ODOMETER }

enum class ReadSupport { NOT_CHECKED, READABLE, UNSUPPORTED, PERMISSION_DENIED }

enum class ReadingState { DISCONNECTED, WAITING, AVAILABLE, FAILED, STALE }

data class VehicleReading(
    val signal: VehicleSignal,
    val support: ReadSupport,
    val state: ReadingState,
    val displayValue: String? = null,
    val receivedAt: Instant? = null,
)

enum class VehicleDataOrigin { NOT_CONNECTED, PLEOS_EMULATOR, PLEOS_VEHICLE, MOCK }

data class VehicleStatus(
    val origin: VehicleDataOrigin,
    val readings: List<VehicleReading>,
)
