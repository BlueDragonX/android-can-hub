package org.techylines.can_hub.device

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.techylines.can_hub.DeviceNotSupportedError
import org.techylines.can_hub.TAG
import java.util.concurrent.ConcurrentHashMap

abstract class DeviceManager(deviceFactories: Collection<DeviceFactory>) {
    private val factories = mutableMapOf<DeviceType, DeviceFactory>()
    private val deviceMap = ConcurrentHashMap<Long, Device>()

    init {
        for (factory in deviceFactories) {
            factories[factory.type] = factory
        }
    }

    // State change callbacks.
    var onDeviceStateChange: ((Device)->Unit)? = null
    var onPortStateChange: ((Port)->Unit)? = null

    // The full set of configured devices.
    val devices: Collection<Device>
        get() = deviceMap.values

    // Register a device factory to create devices for its configured type. If a factory already
    // exists for that type it is be replaced.
    fun register(factory: DeviceFactory) {
        factories[factory.type] = factory
    }

    // Configure a device. This adds a new device if it does not already exist. If a new device is
    // configured for auto-connect then an attempt is made to connect it. If an existing device was
    // connected it is disconnected before reconfiguration. An attempt is made to reconnect the
    // device in this case. Connect failure does not result in this method returning a failure.
    // Returns the configured device or an error on configuration failure.
    fun configure(config: DeviceConfig): Result<Device> =  runBlocking {
        // Create the configured device.
        val factory = factories[config.type] ?:
            return@runBlocking Result.failure(DeviceNotSupportedError("device type ${config.type} not supported"))
        val result = factory.create(config)
        if (result.isFailure) {
            return@runBlocking result
        }
        val device = result.getOrNull()!!

        // Configure the device's callbacks.
        deviceMap[config.id] = device
        device.onDeviceStateChange = {
            onDeviceStateChange?.invoke(it)
        }
        device.onPortStateChange = {
            onPortStateChange?.invoke(it)
        }

        // Auto-connect the device if configured.
        launch(Dispatchers.IO) {
            if (config.autoConnect) {
                device.connect()?.let {
                    Log.w(TAG, "device \"${config.name}\" failed to auto-connect: $it")
                }
            }
        }

        Result.success(device)
    }

    // Retrieve a configured device from the manager using its ID.
    fun get(id: Long): Device? {
        return deviceMap[id]
    }

    // Remove a device from the manager. The disconnects the device if it exists. This method is
    // idempotent and always succeeds.
    fun remove(id: Long) = runBlocking {
        val device = deviceMap.remove(id)
        device?.let {
            if (it.state == Device.State.CONNECTED) {
                launch(Dispatchers.IO) {
                    it.disconnect()
                }
            }
        }
    }
}