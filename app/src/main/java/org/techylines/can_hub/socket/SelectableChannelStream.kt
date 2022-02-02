package org.techylines.can_hub.socket

import org.techylines.can_hub.Error
import org.techylines.can_hub.StreamError
import org.techylines.can_hub.frame.ByteReaderIterator
import org.techylines.can_hub.frame.ByteStream
import org.techylines.can_hub.toError
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

// Wrap a ByteStream around a ByteChannel for use with the various SocketChannels.
internal class SelectableChannelStream(private val channel: ByteChannel) : ByteStream {
    private val iter = ByteReaderIterator(this)

    override fun read(bytes: ByteArray): Result<Int> = runCatching {
        if (isClosed()) {
            throw StreamError("stream is closed")
        }
        val buffer = ByteBuffer.wrap(bytes)
        try {
            val n = channel.read(buffer)
            when {
                n < 0 -> 0
                else -> n
            }
        } catch (ex: IOException) {
            close()
            0
        }
    }

    override fun write(bytes: ByteArray): Result<Int> = runCatching {
        if (isClosed()) {
            throw StreamError("stream is closed")
        }
        val buffer = ByteBuffer.wrap(bytes)
        try {
            channel.write(buffer)
        } catch (ex: IOException) {
            close()
            0
        }
    }

    override fun close(): Error? = runCatching {
        if (!isClosed()) {
            channel.close()
        }
    }.exceptionOrNull()?.toError()

    override fun isClosed(): Boolean = !channel.isOpen

    override fun iterator(): Iterator<Byte> {
        return iter
    }
}
