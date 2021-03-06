package org.techylines.can_hub

import org.techylines.can_hub.frame.Frame
import org.techylines.can_hub.frame.FrameStream

// A stream that periodically sends a heartbeat frame on the bus.
class HeartbeatStream(val frame: Frame, private val periodMs: Long) : FrameStream {
    private var closed = false
    private var lastSend: Long = 0

    init {
        lastSend = System.currentTimeMillis()
    }

    override fun read(): Result<Frame?> {
        if (System.currentTimeMillis() - lastSend > periodMs) {
            return Result.success(frame)
        }
        return Result.success(null)
    }

    // This is a noop as the stream does not process frames.
    override fun write(frame: Frame): Error? {
        return null
    }

    // Stops ending heartbeat frames.
    override fun close(): Error? {
        closed = true
        return null
    }

    override fun isClosed(): Boolean {
        return closed
    }
}