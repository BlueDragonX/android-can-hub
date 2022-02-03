package org.techylines.can_hub.device

// Device factories are used to create devices for a given device type.
abstract class DeviceFactory(val type: DeviceType) {

    // Create a device using the given configuration. Return an error on failure.
    abstract fun create(config: DeviceConfig): Result<Device>
}