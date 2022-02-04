package org.techylines.can_hub.serial

import android.hardware.usb.UsbDevice
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.techylines.can_hub.TAG
import java.util.*

internal object UsbSerial {
    private val prober: UsbSerialProber

    init {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        // Add other supported USB devices here.
        probeTable.addProduct(0x03EB, 0x802B, CdcAcmSerialDriver::class.java) // SAME51
        prober = UsbSerialProber(probeTable)
    }

    // Consistently generate a USB serial device ID from various sources.
    fun getId(vendorId: Int, productId: Int, serialNumber: String) = Objects.hash(vendorId, productId, serialNumber)
    fun getId(usbDevice: UsbDevice) = getId(usbDevice.vendorId, usbDevice.productId, usbDevice.serialNumber ?: "")
    fun getId(usbDeviceId: UsbDeviceId) = getId(usbDeviceId.vendorId, usbDeviceId.productId, usbDeviceId.serialNumber ?: "")

    // Get a serial port for a USB device. Return null if the USB device is not supported.
    fun getSerialPort(usbDevice: UsbDevice): UsbSerialPort? {
        try {
            val driver = prober.probeDevice(usbDevice)
            if (driver == null) {
                Log.w(TAG, "USB device ${usbDevice.deviceName} not supported by serial driver")
                return null
            }
            val port = driver.ports[0]
            if (port == null) {
                Log.w(TAG, "USB device ${usbDevice.deviceName} has no ports")
                return null
            }
            return port
        } catch (ex: Throwable) {
            return null
        }
    }

    // Return true if the USB device is a supported serial device.
    fun isSupported(usbDevice: UsbDevice): Boolean {
        try {
            prober.probeDevice(usbDevice)?.let {
                return it.ports.size > 0
            }
        } catch (ex: Throwable) {}
        return false
    }
}