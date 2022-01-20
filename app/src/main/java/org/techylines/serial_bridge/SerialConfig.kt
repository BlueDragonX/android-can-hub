package org.techylines.serial_bridge

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class SerialConfig(
    var baudRate: Int = 115200,
    var databits: DataBits = DataBits.DATABITS_8,
    var parity: Parity = Parity.PARITY_NONE,
    var dtr: Boolean = false,
    var rts: Boolean = false): Parcelable {

    enum class DataBits(val databits: Int) {
        DATABITS_5(5),
        DATABITS_6(6),
        DATABITS_7(7),
        DATABITS_8(8),
    }

    enum class Parity(val parityId: Int) {
        PARITY_NONE(0),
        PARITY_ODD(1),
        PARITY_EVEN(2),
        PARITY_MARK(3),
        PARITY_SPACE(4),
    }

    enum class StopBits(val stopbitsId: Int) {
        STOPBITS_1(1),
        STOPBITS_1_5(3),
        STOPBITS_2(2),
    }
}