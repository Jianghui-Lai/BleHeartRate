package com.example.bleheartrate.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun parsesEightBitValue() {
        assertEquals(72, HeartRateParser.parse(byteArrayOf(0x00, 72)))
    }

    @Test
    fun treatsEightBitValueAsUnsigned() {
        assertEquals(200, HeartRateParser.parse(byteArrayOf(0x00, 200.toByte())))
    }

    @Test
    fun parsesSixteenBitLittleEndianValue() {
        // 300 = 0x012C, sent as 0x2C then 0x01
        assertEquals(300, HeartRateParser.parse(byteArrayOf(0x01, 0x2C, 0x01)))
    }

    @Test
    fun ignoresOtherFlagBits() {
        // Sensor-contact flags set, still an 8-bit value
        assertEquals(65, HeartRateParser.parse(byteArrayOf(0x06, 65)))
    }

    @Test
    fun returnsNullWhenDataTooShort() {
        assertNull(HeartRateParser.parse(byteArrayOf()))
        assertNull(HeartRateParser.parse(byteArrayOf(0x00)))
        assertNull(HeartRateParser.parse(byteArrayOf(0x01, 0x2C)))
    }
}
