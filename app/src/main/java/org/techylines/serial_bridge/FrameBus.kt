package org.techylines.serial_bridge

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

class FrameBus(private val scope: CoroutineScope) : Closer {
    private val frames = Channel<Event>()
    private val streams = Channel<FrameStream>(1)
    private val nodes = mutableMapOf<Long, Node>()
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
                Log.v(TAG, "node start read loop for id=${id}")
                internalReadLoop()
            }
            writeJob = scope.launch {
                Log.v(TAG, "node start write loop for id=${id}")
                internalWriteLoop()
            }
        }

        private suspend fun internalReadLoop() = coroutineScope {
            try {
                while (!stream.isClosed()) {
                    internalRead()
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
                } ?: yield()
            } else {
                Log.w(TAG, "frame read failed id=${id}: ${result.exceptionOrNull()?.message}")
            }
        }

        private suspend fun internalWriteLoop() = coroutineScope {
            for (event in writeBuffer) {
                Log.v(TAG, "write frame dest_id=${id} source_id=${event.sourceId} frame=${event.frame}")
                stream.write(event.frame)?.let {
                    Log.w(TAG, "frame write failed: ${it.message}")
                }
            }
        }

        suspend fun write(event: Event) = coroutineScope {
            Log.v(TAG, "node write queued dest_id=${id} source_id=${event.sourceId} frame=${event.frame}")
            if (!stream.isClosed()) {
                writeBuffer.send(event)
            }
        }

        fun close() {
            Log.v(TAG, "node close id=${id}")
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
            Log.v(TAG, "start bus")
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
        Log.v(TAG, "read event source_id=${event.sourceId} frame=${event.frame}")
        for (node in nodes) {
            if (node.value.isClosed()) {
                Log.v(TAG, "node closed, removing it id=${node.value.id}")
                nodes.remove(node.key)
            } else if (event.sourceId != node.value.id) {
                node.value.write(event)
            } else {
                Log.v(TAG, "node is source node, skipping id=${node.value.id} source_id=${event.sourceId}")
            }
        }
    }

    private suspend fun onStream(stream: FrameStream) = coroutineScope {
        Log.v(TAG, "add stream id=${id}")
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
        Log.v(TAG, "stopping the bus")
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