package org.techylines.can_hub.serial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.techylines.can_hub.DeviceNotConfiguredError
import org.techylines.can_hub.DeviceNotSupportedError
import org.techylines.can_hub.TAG
import org.techylines.can_hub.errorOrNull
import org.techylines.can_hub.frame.FrameStream
import java.io.InputStream
import java.io.OutputStream

// Manages connected USB serial devices.
class UsbSerialManager(private val usbManager: UsbManager) {
    private val deviceMap = mutableMapOf<Int, UsbSerialDevice>()
    private val deviceNameMap = mutableMapOf<String, Int>()

    val devices: List<UsbSerialDevice>
        get() = deviceMap.values.toList()

    // Get a USB serial device by its ID. Return null if the device is not
    // configured in the manager.
    fun getDevice(id: Int): UsbSerialDevice? {
        return deviceMap[id]
    }

    // Get the USB serial device associated with the given USB device. Return null if the
    // device is not configured in the manager.
    fun getDevice(usbDevice: UsbDevice): UsbSerialDevice? {
        return getDevice(UsbSerial.getId(usbDevice))
    }

    // Configure (or reconfigure) a USB device in the manager. This will not change the operating
    // configuration of a connected device. It will need to be reconnected to pick up the new
    // config. Return an error if the device is not supported.
    fun configure(usbDevice: UsbDevice, serialConfig: SerialConfig, autoConnect: Boolean, protocol: String): Result<UsbSerialDevice> = runCatching {
        val id = UsbSerial.getId(usbDevice)
        val config = UsbSerialConfig(usbDevice, serialConfig, autoConnect,  protocol)

        deviceMap[id]?.let {
            it.reconfigure(config)
            it
        } ?: run {
            val serialPort = UsbSerial.getSerialPort(usbDevice) ?:
            throw DeviceNotSupportedError("device ${usbDevice.deviceName} not supported")
            val serialDevice = UsbSerialDevice(config, usbManager)
            serialDevice.attach(serialPort)
            deviceMap[id] = serialDevice
            deviceNameMap[usbDevice.deviceName] = serialDevice.id
            serialDevice
        }
    }

    // Remove a USB serial device from the manager. The device will be disconnected if
    // necessary. Returns an error on disconnect failure. This is idempotent - return null if the
    // device does not exist.
    fun remove(id: Int) {
        deviceMap[id]?.let {
            it.disconnect()
            deviceMap.remove(id)
        }
    }

    fun remove(usbDevice: UsbDevice) {
        remove(UsbSerial.getId(usbDevice))
    }

    // Connect a configured device. Returns an error on failure or if the device is not configured.
    fun connect(usbDevice: UsbDevice): Result<FrameStream> {
        val id = UsbSerial.getId(usbDevice)
        return deviceMap[id]?.connect()
            ?: throw DeviceNotConfiguredError(usbDevice)
    }

    // Disconnect a device.
    fun disconnect(usbDevice: UsbDevice) {
        deviceMap[UsbSerial.getId(usbDevice)]?.disconnect()
    }

    // Attach a device. Return an error if the device is not configured.
    fun attach(usbDevice: UsbDevice): Result<UsbSerialDevice> = runCatching {
        val id = UsbSerial.getId(usbDevice)
        deviceMap[id]?.let {
            val serialDevice = it
            UsbSerial.getSerialPort(usbDevice)?.let {
                serialDevice.attach(it)
                deviceNameMap[usbDevice.deviceName] = serialDevice.id
                serialDevice
            } ?: throw DeviceNotSupportedError("device ${usbDevice.deviceName} not supported")
        } ?: throw DeviceNotConfiguredError(usbDevice)
    }

    // Detach an attached or connected device. Disconnects the device if necessary. This is
    // idempotent - detaching an already detached device will return null.
    fun detach(usbDevice: UsbDevice) {
        deviceNameMap.remove(usbDevice.deviceName)?.let {
            deviceMap[it]?.detach()
        }
    }

    // Load configured USB serial device from a config file.
    @ExperimentalSerializationApi
    fun load(file: InputStream): Error? = runCatching {
        val usbSerialConfigList = Json.decodeFromStream<List<UsbSerialConfig>>(file)
        for (config in usbSerialConfigList) {
            deviceMap[UsbSerial.getId(config.usbDeviceId)] = UsbSerialDevice(config, usbManager)
        }
        for (usbDevice in usbManager.deviceList.values) {
            deviceMap[UsbSerial.getId(usbDevice)]?.let {
                val result = attach(usbDevice)
                if (result.isFailure) {
                    Log.e(TAG, "failed to attach previously configured device ${usbDevice.deviceName}")
                    Log.e(TAG, "  error=\"${result.exceptionOrNull()}\"")
                    Log.e(TAG, "  manufacturer_name=${usbDevice.manufacturerName}")
                    Log.e(TAG, "  product_name=${usbDevice.productName}")
                    Log.e(TAG, "  vendor_id=${usbDevice.vendorId}")
                    Log.e(TAG, "  product_id=${usbDevice.productId}")
                    Log.e(TAG, "  serial_number=${usbDevice.serialNumber}")
                }
            }
        }
    }.errorOrNull()

    // Save configured USB serial devices to a config file.
    @ExperimentalSerializationApi
    fun save(file: OutputStream): Error? = runCatching {
        val usbSerialConfigList = deviceMap.values.map{it.config}
        Json.encodeToStream(usbSerialConfigList, file)
    }.errorOrNull()
}
