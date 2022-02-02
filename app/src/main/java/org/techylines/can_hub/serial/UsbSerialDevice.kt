package org.techylines.can_hub.serial

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import org.techylines.can_hub.*

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
            val protocol = config.protocol ?: throw ProtocolError("protocol ${config.protocolName} not found")

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