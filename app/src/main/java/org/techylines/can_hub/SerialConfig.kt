package org.techylines.can_hub

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

// Configuration for serial connections.
@Parcelize
@Serializable
data class SerialConfig(
    var baudRate: Int = 115200,
    var dataBits: DataBits = DataBits.DATABITS_8,
    var stopBits: StopBits = StopBits.STOPBITS_1,
    var parity: Parity = Parity.PARITY_NONE,
    var dtr: Boolean = false,
    var rts: Boolean = false): Parcelable {

    enum class DataBits(val value: Int) {
        DATABITS_5(5),
        DATABITS_6(6),
        DATABITS_7(7),
        DATABITS_8(8),
    }

    enum class Parity(val value: Int) {
        PARITY_NONE(0),
        PARITY_ODD(1),
        PARITY_EVEN(2),
        PARITY_MARK(3),
        PARITY_SPACE(4),
    }

    enum class StopBits(val value: Int) {
        STOPBITS_1(1),
        STOPBITS_1_5(3),
        STOPBITS_2(2),
    }
}
