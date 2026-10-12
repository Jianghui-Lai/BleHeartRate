package com.example.bleheartrate.ui

private const val AEROBIC_START = 100
private const val HIGH_START = 140

/** Simple fixed heart-rate zones, for display only. */
enum class HeartRateZone(val label: String, val rangeText: String) {
    Calm("Calm", "under $AEROBIC_START bpm"),
    Aerobic("Aerobic", "$AEROBIC_START–${HIGH_START - 1} bpm"),
    High("High intensity", "$HIGH_START bpm and above");

    companion object {
        fun fromBpm(bpm: Int): HeartRateZone = when {
            bpm >= HIGH_START -> High
            bpm >= AEROBIC_START -> Aerobic
            else -> Calm
        }
    }
}