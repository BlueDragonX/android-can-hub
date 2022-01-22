package org.techylines.serial_bridge

import java.io.Closeable

// General stream interface.
abstract class ByteStream : Closeable {
    // Read bytes from the stream.
    abstract fun read(byte: ByteArray): Int

    // Write bytes to the stream.
    abstract fun write(bytes: ByteArray)

    // Construct a read buffer of an ideal size.
    open fun makeReadBuffer(): ByteArray {
        return ByteArray(4096)
    }

    // Return true if the stream is open.
    abstract fun isOpen(): Boolean
}

/*
class FrameReceiver(val stream: ByteStream) {
    val readThread: Thread? = null

    // Start the frame receiver. Sends frames to the provided callback when received. This spins up
    // a dedicated thread to handle reads.
    fun start(cb: (Frame)->Unit) {
        readThread = thread {
            val readBuffer = stream.makeReadBuffer()
            val readSize: Int = 0

            while {
                read = stream.read(readBuffer)
            }

            with (readSize) {
                1 ->
            }
        }
    }

    // Instructs the receiver to stop reading. Returns immediately. If a read is in progress it is
    // allowed to finish. Stop will return before such a read completes.
    fun stop() {

    }

    fun running(): Boolean {
        readThread?.let {
            return readThread.isAlive
        }
        return false
    }
}
 */