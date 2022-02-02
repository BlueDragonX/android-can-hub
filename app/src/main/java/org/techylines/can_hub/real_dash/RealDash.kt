package org.techylines.can_hub.real_dash

import org.techylines.can_hub.decodeHex
import org.techylines.can_hub.frame.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RealDash {
    fun encode44Frame(frame: Frame): ByteArray {
        val cs = RealDash44Checksum()
        cs.update(frame)
        val buffer = ByteBuffer.allocate(9 + frame.data.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("44332211".decodeHex())
        buffer.putInt(frame.id.toInt())
        buffer.put(frame.data)
        buffer.put(cs.value.toByte())
        return buffer.array()
    }

    fun encode66Frame(frame: Frame): ByteArray {
        val cs = RealDash66Checksum()
        cs.update(frame)
        val buffer = ByteBuffer.allocate(12 + frame.data.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("663322".decodeHex())
        buffer.put((frame.data.size / 4 + 15).toByte())
        buffer.putInt(frame.id.toInt())
        buffer.put(frame.data)
        buffer.putInt(cs.value.toInt())
        return buffer.array()
    }

}