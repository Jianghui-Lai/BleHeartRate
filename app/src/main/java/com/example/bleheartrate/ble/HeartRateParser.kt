package com.example.bleheartrate.ble

/**
 * Parses the Heart Rate Measurement characteristic (UUID 0x2A37).
 *
 * Byte 0 is a flags field. Bit 0 tells us the format of the heart-rate value:
 *  - 0: UINT8, value in byte 1
 *  - 1: UINT16 (little-endian), value in bytes 1 and 2
 *
 * Pure Kotlin, so it can be unit-tested without a phone.
 */
object HeartRateParser {

    fun parse(data: ByteArray): Int? {
        if (data.size < 2) return null

        val flags = data[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0

        return if (isUint16) {
            if (data.size < 3) return null
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            data[1].toInt() and 0xFF
        }
    }
}
