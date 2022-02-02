package org.techylines.can_hub.frame

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import org.techylines.can_hub.Error
import org.techylines.can_hub.TAG
import java.util.concurrent.ConcurrentHashMap

class FrameBus(private val scope: CoroutineScope) : Closer {
    private val frames = Channel<Event>()
    private val streams = Channel<FrameStream>(1)
    private val nodes = ConcurrentHashMap<Long, Node>()
    private var id: Long = 0

    private data class Event (
        val sourceId: Long,
        val frame: Frame,
    )

    private class Node(scope: CoroutineScope, val id: Long, val stream: FrameStream, val frames: Channel<Event>, writeBufferSize: Int = 32) {
        private val writeBuffer = Channel<Event>(writeBufferSize)
        private val readJob: Job
        private val writeJob: Job

        init {
            readJob = scope.launch {
                internalReadLoop()
            }
            writeJob = scope.launch {
                internalWriteLoop()
            }
        }

        private suspend fun internalReadLoop() = coroutineScope {
            try {
                while (!stream.isClosed()) {
                    internalRead()
                    yield()
                }
            } finally {
                stream.close()
                writeJob.cancel()
            }
        }

        private suspend fun internalRead() = coroutineScope {
            val result = stream.read()
            if (result.isSuccess) {
                result.getOrNull()?.let {
                    frames.send(Event(id, it))
                }
            } else {
                Log.w(TAG, "frame read failed node_id=${id}: ${result.exceptionOrNull()?.message}")
            }
        }

        private suspend fun internalWriteLoop() = coroutineScope {
            for (event in writeBuffer) {
                stream.write(event.frame)?.let {
                    Log.w(TAG, "frame write failed node_id=${id}: ${it.message}")
                }
                yield()
            }
        }

        suspend fun write(event: Event) = coroutineScope {
            if (!stream.isClosed()) {
                writeBuffer.send(event)
            }
        }

        fun close() {
            stream.close()
            readJob.cancel()
            writeJob.cancel()
        }

        fun isClosed(): Boolean {
            return stream.isClosed()
        }
    }

    init {
        scope.launch {
            var cont = true
            while (cont) {
                select<Unit> {
                    frames.onReceiveCatching {
                        if (it.isSuccess) {
                            onEvent(it.getOrNull()!!)
                        } else {
                            cont = false
                        }
                    }
                    streams.onReceiveCatching {
                        if (it.isSuccess) {
                            onStream(it.getOrNull()!!)
                        } else {
                            cont = false
                        }
                    }
                }
            }
        }
    }

    private suspend fun onEvent(event: Event) = coroutineScope {
        Log.v(TAG, "receive ${event.frame}")
        for (node in nodes) {
            if (node.value.isClosed()) {
                nodes.remove(node.key)
            } else if (event.sourceId != node.value.id) {
                node.value.write(event)
            }
        }
    }

    private suspend fun onStream(stream: FrameStream) = coroutineScope {
        nodes[id] = Node(scope, id, stream, frames)
        id++
    }

    // Add a stream to the bus. It is removed when closed.
    @ExperimentalCoroutinesApi
    suspend fun add(stream: FrameStream) = coroutineScope {
        if (!frames.isClosedForSend) {
            streams.send(stream)
        }
    }

    // Stop the bus and close all the streams added to it. The bus may not be reused after a call
    // to close.
    override fun close(): Error? {
        for (node in nodes.values) {
            node.close()
        }
        frames.close()
        return null
    }

    // Return true if the bus is closed.
    override fun isClosed(): Boolean {
        return frames.isClosedForSend
    }
}