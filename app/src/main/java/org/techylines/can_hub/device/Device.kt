package org.techylines.can_hub.device

import android.os.Bundle

// Base class for all devices.
abstract class Device {
    // Device specific metadata.
    abstract val metadata: Bundle

    enum class State {
        CONNECTED,      // Device is available and operating.
        DISCONNECTED,   // Device is available but not operating.
        UNAVAILABLE,    // Device is not available for use.
    }

    // The device configuration.
    abstract val config: DeviceConfig

    // State of the device.
    abstract val state: State

    // The device's connected ports.
    abstract val ports: Collection<Port>

    // Set callback to invoke when the device changes state.
    abstract var onDeviceStateChange: ((Device)->Unit)?

    // Set callback to invoke when a port on the device changes state.
    abstract var onPortStateChange: ((Port)->Unit)?

    // Connect the device.
    abstract suspend fun connect(): Error?

    // Disconnect the device. This call is idempotent.
    abstract suspend fun disconnect()

}