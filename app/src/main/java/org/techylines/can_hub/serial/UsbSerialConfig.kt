package org.techylines.can_hub.serial

import android.hardware.usb.UsbDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.techylines.can_hub.FrameProtocol
import org.techylines.can_hub.FrameProtocolManager
import org.techylines.can_hub.SerialConfig

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