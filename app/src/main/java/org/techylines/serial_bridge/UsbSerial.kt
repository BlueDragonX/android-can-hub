package org.techylines.serial_bridge

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Parcelable
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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
    fun getId(usbDeviceId: UsbDeviceId) = getId(usbDeviceId.vendorId, usbDeviceId.productId, usbDeviceId.serialNumber ?: "")

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

// Holds USB device identification.
@Parcelize
@Serializable
data class UsbDeviceId (
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
) : Parcelable {
    constructor(device: UsbDevice) : this(device.vendorId, device.productId, device.serialNumber)
}

// Holds USB serial device configuration for connected and disconnected devices.
@Parcelize
@Serializable
data class UsbSerialConfig(
    // Device identification.
    val usbDeviceId: UsbDeviceId,
    // Serial connection config.
    var serialConfig: SerialConfig,
    // Stream and connectivity config.
    val autoConnect: Boolean,
    val protocolName: String,	// The name of the protocol to use with this device.
) : Parcelable {
    // Return the actual protocol associated
    val protocol: FrameProtocol?
        get() = FrameProtocolManager.default.getProtocol(protocolName)

    constructor(usbDevice: UsbDevice, serialConfig: SerialConfig, autoConnect: Boolean, protocolName: String) :
            this(UsbDeviceId(usbDevice), serialConfig, autoConnect, protocolName)
}

// Wraps a UsbSerialPort in a ByteStream interface.
class UsbSerialStream(val serialPort: UsbSerialPort) : ByteStream {
    override val readBufferSize: Int
        get() = serialPort.readEndpoint.maxPacketSize
    private val iter = ByteReaderIterator(this)

    override fun read(bytes: ByteArray): Result<Int> = runCatching {
        try {
            serialPort.read(bytes, 0)
        } catch (ex: IOException) {
            // An IO error typically indicates that the device has been detached. Close the port.
            serialPort.close()
            throw ex
        }
    }

    override fun write(bytes: ByteArray): Result<Int> = runCatching {
        serialPort.write(bytes, 0)
        bytes.size
    }

    override fun close(): Error? = runCatching{
        try {
            with (serialPort) {
                if (this.isOpen) {
                    this.close()
                }
            }
        } catch (ex: IOException) {
            // Ignore IO errors when closing the port.
            Log.w(TAG, "error when closing USB serial port ${serialPort.device.deviceName}: ${ex}")
        }
    }.errorOrNull()

    override fun isClosed(): Boolean {
        return !serialPort.isOpen
    }

    override fun iterator(): Iterator<Byte> = iter
}

class UsbSerialDevice(config: UsbSerialConfig) {
    var config: UsbSerialConfig
        private set
    // The byte stream. Only set when the device is connected.
    var stream: UsbSerialStream? = null
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
        get() = UsbSerial.getId(config.usbDeviceId)
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
            val byteStream = UsbSerialStream(serialPort!!)
            stream = byteStream
            protocol.encodeStream(byteStream)
        } ?: throw DeviceNotAttachedError()
    }

    internal fun disconnect() {
        stream?.let {
            it.close()
            stream = null
        }
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
                throw DeviceNotSupportedError(usbDevice)
            val serialDevice = UsbSerialDevice(config)
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
        return deviceMap[id]?.connect(usbManager) ?:
            throw DeviceNotConfiguredError(usbDevice)
    }

    // Disconnect a device.
    fun disconnect(usbDevice: UsbDevice) {
        deviceMap[UsbSerial.getId(usbDevice)]?.disconnect()
    }

    // Attach a device. Return an error if the device is not configured. Attached devices are
    // connected automatically if they have been configured.
    fun attach(usbDevice: UsbDevice): Result<UsbSerialDevice> = runCatching {
        Log.v(TAG, "manager: attach device ${usbDevice.deviceName}")
        val id = UsbSerial.getId(usbDevice)
        deviceMap[id]?.let {
            val serialDevice = it
            UsbSerial.getSerialPort(usbDevice)?.let {
                serialDevice.attach(it)
                deviceNameMap[usbDevice.deviceName] = serialDevice.id
                serialDevice
            } ?: throw DeviceNotSupportedError(usbDevice)
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
            deviceMap[UsbSerial.getId(config.usbDeviceId)] = UsbSerialDevice(config)
        }
        for (usbDevice in usbManager.deviceList.values) {
            deviceMap[UsbSerial.getId(usbDevice)]?.let {
                attach(usbDevice)?.let {
                    Log.e(TAG, "failed to attach previously configured device ${usbDevice.deviceName}")
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