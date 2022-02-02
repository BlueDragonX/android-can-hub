package org.techylines.can_hub.socket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.techylines.can_hub.*
import java.net.SocketAddress
import java.nio.channels.ServerSocketChannel

// Base class for all socket based servers. Listens for connections and returns ByteStreams for
// each new connection. Closing a SocketServer stops listening. SocketServers should not be reused
// after close.
class SocketServer(private val scope: CoroutineScope) : Closer {
    private var impl: SocketServerImpl? = null

    enum class Protocol { TCP, UDP }

    // Listen for client connections on the given local address. Calls onConnect with a connected
    // ByteStream to communicate with new clients. The ByteStream is closed by the server when the client disconnects.
    fun listen(address: SocketAddress, protocol: Protocol, onConnect: (ByteStream) -> Unit): Error? {
        return when (protocol) {
            Protocol.TCP -> {
                impl = TcpServerImpl(scope)
                impl?.listen(address, onConnect)
            }
            else -> {
                //TODO: Implement UDP.
                ProtocolError("socket protocol $protocol not yet supported")
            }
        }
    }

    // Close the server. Stops listening for new connection. Existing connections are not closed.
    override fun close(): Error? {
        return impl?.close()
    }

    override fun isClosed(): Boolean = impl?.isClosed() != true

    // Base class for all socket server implementations.
    private interface SocketServerImpl : Closer {
        fun listen(address: SocketAddress, onConnect: (ByteStream) -> Unit): Error?
    }

    // TCP socket server implementation.
    private class TcpServerImpl(private val scope: CoroutineScope) : SocketServerImpl {
        private val server = ServerSocketChannel.open()

        override fun listen(address: SocketAddress, onConnect: (ByteStream) -> Unit): Error? = runCatching {
            server.bind(address)
            scope.launch(Dispatchers.IO) {
                while(!isClosed()) {
                    try {
                        val socketChannel = server.accept()
                        onConnect(SelectableChannelStream(socketChannel))
                        yield()
                    } catch (ex: Exception) {
                        Log.w(TAG, "accept failed on ${server.localAddress}")
                    }
                }
            }
        }.exceptionOrNull()?.toError()

        override fun close(): Error? = runCatching {
            if (!isClosed()) {
                server.close()
            }
        }.exceptionOrNull()?.toError()

        override fun isClosed(): Boolean = !server.isOpen
    }

    // TODO: Build a UDP socket server implementation.
}
