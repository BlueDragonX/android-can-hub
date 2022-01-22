package org.techylines.serial_bridge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// Holds a CAN frame. Data bytes are in network order.
class Frame(
    var id: Long = 0,
    var data: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        return other != null &&
                other is Frame &&
                this.id == other.id &&
                Arrays.equals(this.data, other.data)
    }

    override fun toString(): String {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(id.toInt())
        return "${buffer.array().toHexString()}${data.toHexString()}"
    }
}