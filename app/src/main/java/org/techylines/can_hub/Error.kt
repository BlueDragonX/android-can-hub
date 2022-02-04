package org.techylines.can_hub

import android.hardware.usb.UsbDevice

// Indicates that an error occurred on an attached USB device.
open class DeviceError(message: String, val device: UsbDevice? = null) : Error(message)

// USB device could not be opened.
class DeviceOpenError(device: UsbDevice) : DeviceError("unable to open device", device)

// USB device is not attached.
class DeviceNotAttachedError() : DeviceError("device not attached")

// USB device is already connected.
class DeviceConnectedError(device: UsbDevice) : DeviceError("device already connected", device)

// A USB device is not yet configured.
class DeviceNotConfiguredError(device: UsbDevice) : DeviceError("device not configured", device)

// A USB device is not supported by the serial driver.
class DeviceNotSupportedError(message: String) : DeviceError(message)

// Parse error when parsing of a byte stream fails.
class ParseError(message: String) : Error(message)

// Error reading or writing to a stream.
class StreamError(message: String) : Error(message)

// Protocol was not found.
class ProtocolError(message: String) : Error(message)

// Used to wrap non-Error throwables that were not haneld.
class UnhandledError(cause: Throwable) : Error("unhandled error: ${cause}", cause)

// Upcast a Throwable to an error or wrap it in an UnhandledError.
fun Throwable.toError(): Error {
    return when(this) {
        is Error -> this
        else -> UnhandledError(this)
    }
}

// Like exceptionOrNull() but always returns an Error.
fun <T> Result<T>.errorOrNull(): Error? = this.exceptionOrNull()?.toError()