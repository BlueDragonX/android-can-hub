package org.techylines.serial_bridge

// Standard error container.
open class Error(open val message: String) {
    override fun toString(): String {
        return message
    }
}

// Return result used to indicate success or failure.
sealed class Result<T>(open val value: T?, open val error: Error?) {
    fun ok(): Boolean {
        return error != null
    }

    // Return result for successful operations. Contains the return value.
    data class Success<T>(override val value: T?) : Result<T>(value, null)

    // Return result for failed operations. Contains an error.
    data class Failure<T>(override val error: Error?) : Result<T>(null, error) {
        constructor(message: String) : this(Error(message))
    }
}