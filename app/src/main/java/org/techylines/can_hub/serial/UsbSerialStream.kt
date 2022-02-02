package org.techylines.can_hub.serial

import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import org.techylines.can_hub.*
import org.techylines.can_hub.frame.ByteReaderIterator
import org.techylines.can_hub.frame.ByteStream
import java.io.IOException

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
            Log.w(TAG, "error when closing USB serial port ${serialPort.device.deviceName}: $ex")
        }
    }.errorOrNull()

    override fun isClosed(): Boolean {
        return !serialPort.isOpen
    }

    override fun iterator(): Iterator<Byte> = iter
}