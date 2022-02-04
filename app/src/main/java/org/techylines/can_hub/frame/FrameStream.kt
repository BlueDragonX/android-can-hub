package org.techylines.can_hub.frame

// Interface for objects that read frames from a stream. These are typically implemented as
// wrappers around a ByteReader. Closes any underlying readers when closed.
interface FrameReader : Closer {
    // Read a frame. An error is returned on failure. Reads may be retried on error so long as
    // isClosed() returns false. Parse errors are not returned but may be logged or surfaced in a
    // side channel.
    fun read(): Result<Frame?>
}

// Interface for objects that write frames to a stream. These are typically implemented as
// wrappers around a ByteWriter. Closes any underlying writers when closed.
interface FrameWriter : Closer {
    // Write a frame. An error is returned on failure. Returns true if the frame was written or
    // false if it was discarded. Unsupported frames should be discarded.
    fun write(frame: Frame): Error?
}

// Interface for objects that read and write frames to a stream.
interface FrameStream : FrameReader, FrameWriter
