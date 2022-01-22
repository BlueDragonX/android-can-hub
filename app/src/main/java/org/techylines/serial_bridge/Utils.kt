package org.techylines.serial_bridge

import java.nio.ByteBuffer

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }