package org.techylines.serial_bridge

interface FrameProtocol {
    // The short name of the protocol. Must be unique.
    val name: String

    // A more detailed description of the protocol.
    val description: String

    // Use the protocol to encode a byte stream to a frame stream.
    fun encodeStream(byteStream: ByteStream) : FrameStream
}

// Manages supported protocols. Protocols are identified by name. The manager can look up protocols
// by name, listed supported protocols, or encode streams directly. New protocols can be
// registered with the manager to extend support for additional stream encodings.
class FrameProtocolManager private constructor() {
    private val protocolMap = sortedMapOf<String, FrameProtocol>()

    // Return a list of available protocols. The list is sorted by protocol name.
    val protocols: List<FrameProtocol>
        get() = protocolMap.values.toList()

    companion object {
        // Default singleton protocol manager.
        val default = FrameProtocolManager()
        init {
            default.register(RealDashProtocol())
        }
    }

    // Return the named protocol or null if no such protocol is registered.
    fun getProtocol(name: String): FrameProtocol? {
        return protocolMap[name]
    }

    // Register a new protocol with the manager. The protocol's name should be unique.
    fun register(protocol: FrameProtocol) {
        protocolMap[protocol.name] = protocol
    }

    // Use the named protocol to encode the given byte stream. Return the frame stream or a null if
    // the protocol does not exist.
    fun encodeStream(name: String, byteStream: ByteStream) : FrameStream? {
        protocolMap[name]?.let {
            return it.encodeStream(byteStream)
        }
        return null
    }
}