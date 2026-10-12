package com.example.bleheartrate.ble

/** A BLE device found during scanning. */
data class ScannedDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
)

/** Events produced by an active GATT connection. */
sealed interface GattEvent {
    data object Connected : GattEvent
    data object Disconnected : GattEvent
    data class HeartRate(val bpm: Int) : GattEvent
    data class Error(val message: String) : GattEvent
    /** Diagnostic message shown on screen while we debug the connection. */
    data class Debug(val message: String) : GattEvent
}