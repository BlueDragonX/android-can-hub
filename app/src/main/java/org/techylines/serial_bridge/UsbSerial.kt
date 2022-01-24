package org.techylines.serial_bridge

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.*

private object UsbSerial {
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
    fun getId(usbConfig: UsbSerialConfig) = getId(usbConfig.vendorId, usbConfig.productId, usbConfig.serialNumber ?: "")

    // Get a serial port for a USB device. Return null if the USB device is not supported.
    fun getSerialPort(usbDevice: UsbDevice): UsbSerialPort? {
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
    }
}

// Holds USB serial device configuration for connected and disconnected devices.
data class UsbSerialConfig(
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,	// The device serial number if available.
    val serialConfig: SerialConfig,
    val autoConnect: Boolean,
    val protocolName: String,	// The name of the protocol to use with this device.
) {
    // Return the actual protocol associated
    val protocol: FrameProtocol?
        get() = FrameProtocolManager.default.getProtocol(protocolName)

    constructor(usbDevice: UsbDevice, serialConfig: SerialConfig, autoConnect: Boolean, protocolName: String) :
            this(usbDevice.vendorId, usbDevice.productId, usbDevice.serialNumber, serialConfig, autoConnect, protocolName)
}

class UsbSerialDevice(config: UsbSerialConfig) {
    var config: UsbSerialConfig
        private set

    init {
        this.config = config
    }

    enum class State {
        ATTACHED,	// device is attached but not connected; may not be configured
        DETACHED,	// device is detached and has a configuration
        CONNECTED,  // device is connected
    }

    val id: Int
        get() = UsbSerial.getId(config)
    private var serialPort: UsbSerialPort? = null

    val state: State
        get() {
            return when (serialPort?.isOpen) {
                true -> State.CONNECTED
                false -> State.ATTACHED
                null -> State.DETACHED
            }
        }

    internal fun attach(serialPort: UsbSerialPort) {
        this.serialPort = serialPort
    }

    internal fun detach() {
        disconnect()
        serialPort = null
    }

    internal fun connect(usbManager: UsbManager): Result<FrameStream> = runCatching {
        serialPort?.let {
            val protocol = config.protocol ?: throw ProtocolError("protocol ${config.protocolName} not found", )

            if (it.isOpen) {
                throw DeviceConnectedError(it.device)
            }
            val con = usbManager.openDevice(it.device)
                ?: throw DeviceOpenError(it.device)

            it.open(con)
            configureSerialPort()
            protocol.encodeStream(UsbSerialStream(serialPort!!))
        } ?: throw DeviceNotAttachedError()
    }

    internal fun disconnect() {
        serialPort?.close()
    }

    internal fun reconfigure(config: UsbSerialConfig) {
        this.config = config
        configureSerialPort()
    }

    private fun configureSerialPort() {
        serialPort?.let {
            it.setParameters(
                config.serialConfig.baudRate,
                config.serialConfig.dataBits.value,
                config.serialConfig.stopBits.value,
                config.serialConfig.parity.value,
            )
            it.dtr = config.serialConfig.dtr
            it.rts = config.serialConfig.rts
        }
    }
}

// Wraps a UsbSerialPort in a ByteStream interface.
class UsbSerialStream(val serialPort: UsbSerialPort) : ByteStream {
    override val readBufferSize: Int
        get() = serialPort.readEndpoint.maxPacketSize
    private val iter = ByteReaderIterator(this)

    override fun read(bytes: ByteArray): Result<Int> = runCatching {
        serialPort.read(bytes, 0)
    }

    override fun write(bytes: ByteArray): Result<Int> = runCatching {
        serialPort.write(bytes, 0)
        bytes.size
    }

    override fun close(): Error? = runCatching{
        if (serialPort.isOpen) {
            serialPort.close()
        }
    }.errorOrNull()

    override fun isClosed(): Boolean {
        return !serialPort.isOpen
    }

    override fun iterator(): Iterator<Byte> = iter
}

class UsbSerialManager(private val usbManager: UsbManager) {
    private val deviceMap = mutableMapOf<Int, UsbSerialDevice>()

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
                throw DeviceNotSupportedError(usbDevice)
            val serialDevice = UsbSerialDevice(config)
            serialDevice.attach(serialPort)
            deviceMap[id] = serialDevice
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
        return deviceMap[id]?.connect(usbManager) ?:
            throw DeviceNotConfiguredError(usbDevice)
    }

    // Disconnect a device.
    fun disconnect(usbDevice: UsbDevice) {
        deviceMap[UsbSerial.getId(usbDevice)]?.disconnect()
    }

    // Attach a device. Return an error if the device is not configured. Attached devices are
    // connected automatically if they have been configured.
    fun attach(usbDevice: UsbDevice): Error? {
        val id = UsbSerial.getId(usbDevice)
        deviceMap[id]?.let {
            val device = it
            UsbSerial.getSerialPort(usbDevice)?.let {
                device.attach(it)
            } ?: return DeviceNotSupportedError(usbDevice)
        } ?: return DeviceNotConfiguredError(usbDevice)
        return null
    }

    // Detach an attached or connected device. Disconnects the device if necessary. This is
    // idempotent - detaching an already detached device will return null.
    fun detach(usbDevice: UsbDevice) {
        deviceMap[UsbSerial.getId(usbDevice)]?.detach()
    }
}