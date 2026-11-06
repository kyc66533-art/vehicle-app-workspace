package com.nextdrive.pleos.vehicle

/** Explicitly disconnected. This does not simulate a vehicle or attempt SDK calls. */
class UnavailableVehicleDataSource : VehicleDataSource {
    override fun readStatus(onResult: (VehicleStatus) -> Unit) {
        onResult(
            VehicleStatus(
                origin = VehicleDataOrigin.NOT_CONNECTED,
                readings = VehicleSignal.entries.map { signal ->
                    VehicleReading(
                        signal = signal,
                        support = ReadSupport.NOT_CHECKED,
                        state = ReadingState.DISCONNECTED,
                    )
                },
            )
        )
    }

    override fun close() = Unit
}
