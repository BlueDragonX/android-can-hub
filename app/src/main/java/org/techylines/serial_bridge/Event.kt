package org.techylines.serial_bridge

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

data class FrameBroadcast constructor (val deviceName: String, val frame: Frame)

// Base class for all classes that sends and receives events on the bus.
abstract class EventNode : Closeable {
    // Return the name of the node. This should be unique.
    abstract fun getName(): String

    // Listen for events from the node. The node sends events to the bus by calling onEvent.
    abstract fun listen(onEvent: (FrameBroadcast)->Unit): Error?

    // Send an event to the node.
    abstract fun send(event: FrameBroadcast): Throwable?

    // Subscription method for the event bus implementation.
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun subscribe(event: FrameBroadcast) {
        //TODO: Do something with this error.
        send(event)
    }
}

class EventManager(val eventBus: EventBus = EventBus.getDefault()) {
    private val nodes = mutableMapOf<String, EventNode>()

    // Register a node. Return an error if there is already a node with that name or the call to
    // receive fails.
    fun register(node: EventNode): Error? {
        if (nodes.containsKey(node.getName())) {
            return Error("event node ${node.getName()} exists")
        }
        nodes[node.getName()] = node
        return node.listen {
            eventBus.post(it)
        }
    }

    fun unregister(node: EventNode): Boolean {
        nodes[node.getName()]?.let {
            it.close()
            nodes.remove(node.getName())
            return true
        }
        return false
    }

    fun unregister(nodeName: String): Boolean {
        nodes[nodeName]?.let {
            return unregister(it)
        }
        return false
    }
}