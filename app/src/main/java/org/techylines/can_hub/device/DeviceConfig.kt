package org.techylines.can_hub.device

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceConfig(
    // A unique ID to identify the device.
    val id: Long,
    // The display name of the device. Not necessarily unique.
    val name: String,
    // The type of the device.
    val type: DeviceType,
    // The line protocol of the device.
    val protocol: String,
    // Whether or not the device should connect automatically at app start.
    val autoConnect: Boolean,
    // Extra configuration.
    val extras: Bundle,
) : Parcelable