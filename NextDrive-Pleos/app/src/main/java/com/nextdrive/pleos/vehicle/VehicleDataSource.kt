package com.nextdrive.pleos.vehicle

/** Replace at the composition boundary once the official SDK has been verified. */
interface VehicleDataSource {
    /** Results may arrive asynchronously; each reading keeps its own receive time. */
    fun readStatus(onResult: (VehicleStatus) -> Unit)

    /** Release connections/listeners when the screen stops. */
    fun close()
}
