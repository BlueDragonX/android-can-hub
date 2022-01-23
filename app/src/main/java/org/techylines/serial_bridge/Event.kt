package org.techylines.serial_bridge

import android.util.Log
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.RuntimeException
import kotlin.concurrent.thread

data class FrameBroadcast constructor (val deviceName: String, val frame: Frame)

// Base class for all classes that sends and receives events on the bus.
interface EventNode : Closer {
    // Return the name of the node. This should be unique among nodes.
    val name: String

    // Listen for events from the node. The node sends events to the bus by calling onEvent.
    // Return IllegalStateException if already called.
    fun listen(onEvent: (FrameBroadcast)->Unit): Throwable?

    // Send an event to the node.
    fun send(event: FrameBroadcast): Throwable?

    // Subscription method for the event bus implementation.
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun subscribe(event: FrameBroadcast) {
        //TODO: Do something with this error.
        send(event)
    }
}

class StreamNode(override val name: String, private val stream: FrameStream) : EventNode {
    private var readThread: Thread? = null

    override fun listen(onEvent: (FrameBroadcast) -> Unit): Throwable? {
        if (readThread?.isAlive == true) {
            return RuntimeException("stream already listening")
        }
        readThread = thread {
            while (!stream.isClosed()) {
                val result = stream.read()
                result.exceptionOrNull()?.let {
                    Log.w(TAG, "frame read failed: $it")
                }
                result.getOrNull()?.let {
                    onEvent(FrameBroadcast(name, it))
                }
            }
        }
        return null
    }

    override fun send(event: FrameBroadcast): Throwable? {
        return stream.write(event.frame)
    }

    // Close the underlying stream and instruct the receiver thread to shut down. Does not block.
    // An in progress read may prevent the thread from closing. Use join() to ensure the thread is
    // stopped.
    override fun close(): Throwable? {
        return stream.close()
    }

    // Returns true if the thread is still alive.
    override fun isClosed(): Boolean {
        return readThread?.isAlive != true
    }

    // Join the underlying thread. Wait at most millis for the thread to join before returning. A
    // value of 0 will wait infinitely. Return true if the thread is stopped.
    fun join(millis: Long = 0): Boolean {
        close()
        readThread?.let {
            it.join(millis)
            return it.isAlive
        }
        return true
    }
}