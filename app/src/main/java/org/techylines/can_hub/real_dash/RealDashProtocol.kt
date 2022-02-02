package org.techylines.can_hub.real_dash

import org.techylines.can_hub.frame.ByteStream
import org.techylines.can_hub.frame.FrameProtocol
import org.techylines.can_hub.frame.FrameStream

class RealDashProtocol : FrameProtocol {
    override val name: String
        get() = "RealDash"
    override val description: String
        get() = "Communicate with an the RealDash software dashboard."

    override fun encodeStream(byteStream: ByteStream): FrameStream {
        return RealDashStream(byteStream)
    }
}