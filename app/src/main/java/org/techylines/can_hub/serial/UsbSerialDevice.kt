package org.techylines.can_hub.serial

import android.hardware.usb.UsbManager
import android.os.Bundle
import com.hoho.android.usbserial.driver.UsbSerialPort
import org.techylines.can_hub.DeviceConnectedError
import org.techylines.can_hub.DeviceNotAttachedError
import org.techylines.can_hub.DeviceOpenError
import org.techylines.can_hub.ProtocolError
import org.techylines.can_hub.frame.FrameStream

class UsbSerialDevice(config: UsbSerialConfig, private val usbManager: UsbManager) {
    val metadata = Bundle()

    init {
        metadata.putParcelable(META_USB_CONFIG, config)
    }

    val id: Int
        get() = UsbSerial.getId(config.usbDeviceId)

    val config: UsbSerialConfig
        get() = metadata.getParcelable(META_USB_CONFIG)!!

    // The USB serial port. Only set when the device is attached.
    private var serialPort: UsbSerialPort? = null
    // The byte stream. Only set after the device is connected. May still be set after disonnect.
    private var stream: UsbSerialStream? = null

    internal fun attach(serialPort: UsbSerialPort) {
        this.serialPort = serialPort
    }

    internal fun detach() {
        disconnect()
        serialPort = null
    }

    fun connect(): Result<FrameStream> = runCatching {
        serialPort?.let {
            val protocol = config.protocol ?: throw ProtocolError("protocol ${config.protocolName} not found")

            if (it.isOpen) {
                throw DeviceConnectedError(it.device)
            }
            val con = usbManager.openDevice(it.device)
                ?: throw DeviceOpenError(it.device)

            it.open(con)
            configureSerialPort(config.serialConfig)
            val byteStream = UsbSerialStream(serialPort!!)
            stream = byteStream
            protocol.encodeStream(byteStream)
        } ?: throw DeviceNotAttachedError()
    }

    fun disconnect() {
        stream?.let {
            it.close()
            stream = null
        }
    }

    internal fun reconfigure(config: UsbSerialConfig) {
        metadata.putParcelable(META_USB_CONFIG, config)
        configureSerialPort(config.serialConfig)
    }

    private fun configureSerialPort(serialConfig: SerialConfig) {
        serialPort?.let {
            it.setParameters(
                serialConfig.baudRate,
                serialConfig.dataBits.value,
                serialConfig.stopBits.value,
                serialConfig.parity.value,
            )
            it.dtr = serialConfig.dtr
            it.rts = serialConfig.rts
        }
    }
}