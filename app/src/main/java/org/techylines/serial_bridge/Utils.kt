package org.techylines.serial_bridge

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
fun Collection<Byte>.toHexString() = joinToString("") { "%02x".format(it) }

val Int.b: Byte get() = toByte()
val Byte.s: String get() = toString(16)
