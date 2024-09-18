package com.ncorti.myonnaise

data class ImuData(
    val orientation: FloatArray,
    val accelerometer: FloatArray,
    val gyroscope: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImuData

        if (!orientation.contentEquals(other.orientation)) return false
        if (!accelerometer.contentEquals(other.accelerometer)) return false
        if (!gyroscope.contentEquals(other.gyroscope)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = orientation.contentHashCode()
        result = 31 * result + accelerometer.contentHashCode()
        result = 31 * result + gyroscope.contentHashCode()
        return result
    }
}