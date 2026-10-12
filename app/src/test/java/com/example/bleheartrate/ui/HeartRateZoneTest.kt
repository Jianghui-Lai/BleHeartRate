package com.example.bleheartrate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateZoneTest {

    @Test
    fun restingRateIsCalm() {
        assertEquals(HeartRateZone.Calm, HeartRateZone.fromBpm(72))
        assertEquals(HeartRateZone.Calm, HeartRateZone.fromBpm(99))
    }

    @Test
    fun midRangeIsAerobic() {
        assertEquals(HeartRateZone.Aerobic, HeartRateZone.fromBpm(100))
        assertEquals(HeartRateZone.Aerobic, HeartRateZone.fromBpm(139))
    }

    @Test
    fun highRateIsHighIntensity() {
        assertEquals(HeartRateZone.High, HeartRateZone.fromBpm(140))
        assertEquals(HeartRateZone.High, HeartRateZone.fromBpm(200))
    }
}