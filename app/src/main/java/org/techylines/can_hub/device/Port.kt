package org.techylines.can_hub.device

import org.techylines.can_hub.frame.FrameStream

abstract class Port : FrameStream {
    // The device that owns this port.
    abstract val device: Device

    // The name of the port. This is unique among all ports on the device.
    abstract val name: String
}