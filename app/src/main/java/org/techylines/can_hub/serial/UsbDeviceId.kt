package org.techylines.can_hub.serial

import android.hardware.usb.UsbDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

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