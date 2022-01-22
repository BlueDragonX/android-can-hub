package org.techylines.serial_bridge

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Parcelable
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.parcelize.Parcelize
import java.io.IOException
import java.nio.ByteBuffer

// Configuration for USB serial devices.
@Parcelize
class UsbSerialConfig(
    var baudRate: Int = 115200,
    var dataBits: DataBits = DataBits.DATABITS_8,
    var stopBits: StopBits = StopBits.STOPBITS_1,
    var parity: Parity = Parity.PARITY_NONE,
    var dtr: Boolean = false,
    var rts: Boolean = false): Parcelable {

    enum class DataBits(val value: Int) {
        DATABITS_5(5),
        DATABITS_6(6),
        DATABITS_7(7),
        DATABITS_8(8),
    }

    enum class Parity(val value: Int) {
        PARITY_NONE(0),
        PARITY_ODD(1),
        PARITY_EVEN(2),
        PARITY_MARK(3),
        PARITY_SPACE(4),
    }

    enum class StopBits(val value: Int) {
        STOPBITS_1(1),
        STOPBITS_1_5(3),
        STOPBITS_2(2),
    }
}

// USB serial device. Allows reading and writing to a serial device.
class UsbSerialDevice(internal val port: UsbSerialPort, val config: UsbSerialConfig, val manager: UsbSerialManager) :
    ByteStream() {

    override fun getName(): String {
        return port.device.deviceName
    }

    // Return the USB device that backs this serial device.
    fun getDevice(): UsbDevice {
        return port.device
    }

    // Return the driver used to ope the serial device.
    fun getDriver(): UsbSerialDriver {
        return port.driver
    }

    // Read bytes from the device.
    override fun read(bytes: ByteArray): Result<Int> = runCatching {
        port.read(bytes, 0)
    }

    // Write bytes to the device.
    override fun write(bytes: ByteArray): Throwable? = runCatching {
        port.write(bytes, 0)
    }.exceptionOrNull()

    override fun makeReadBuffer(): ByteArray {
        return ByteArray(port.getReadEndpoint().getMaxPacketSize());
    }

    // Return true if the device is open.
    override fun isOpen(): Boolean {
        return port.isOpen
    }

    // Close the device.
    override fun close() {
        if (port.isOpen) {
            port.close()
        }
        manager.serialDevices[port.device.deviceName]?.let {
            manager.serialDevices.remove(port.device.deviceName)
        }
    }
}

// Manages USB serial devices.
class UsbSerialManager(val usbManager: UsbManager) {
    internal val serialDevices: MutableMap<String, UsbSerialDevice> = mutableMapOf()
    private var prober: UsbSerialProber? = null

    init {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        probeTable.addProduct(0x03EB, 0x802B, CdcAcmSerialDriver::class.java) // SAME51
        prober = UsbSerialProber(probeTable)
    }

    // Open a USB connection. Returns an error if the device was already open or on failure.
    fun open(device: UsbDevice, config: UsbSerialConfig): Result<UsbSerialDevice> = runCatching {
        val driver = prober?.probeDevice(device) ?:
            throw IOException("USB device ${device.deviceName} not supported by serial driver")
        val port = driver.ports[0] ?:
            throw IOException("USB device ${device.deviceName} has no ports")
        val con = usbManager.openDevice(device) ?:
            throw IOException("failed to open USB device ${device.deviceName}")
        port.open(con)
        port.setParameters(config.baudRate, config.dataBits.value, config.stopBits.value, config.parity.value)
        port.dtr = config.dtr
        port.rts = config.rts
        val serialDevice = UsbSerialDevice(port, config, this)
        serialDevices[device.deviceName] = serialDevice
        serialDevice
    }

    // Close a device.
    fun close(device: UsbDevice): Boolean {
        serialDevices[device.deviceName]?.let {
            // This will remove the device from the manager as well.
            it.close()
            return true
        }
        return false
    }

    // Return a list of open devices.
    fun openDevices() : Collection<UsbSerialDevice> {
        return serialDevices.values
    }
}