package com.example.bleheartrate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bleheartrate.ble.BleManager
import com.example.bleheartrate.ble.GattEvent
import com.example.bleheartrate.ble.ScannedDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState { Idle, Connecting, Connected }

data class UiState(
    val scanning: Boolean = false,
    val onlyHeartRate: Boolean = true,
    val devices: List<ScannedDevice> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val deviceLabel: String = "",
    val heartRate: Int? = null,
    val error: String? = null,
    val debug: List<String> = emptyList(),
)

/** Holds screen state and drives the BLE flows (MVVM). */
class BleViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BleManager(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var connectJob: Job? = null

    fun setOnlyHeartRate(value: Boolean) {
        _state.update { it.copy(onlyHeartRate = value) }
        if (_state.value.scanning) startScan()
    }

    fun startScan() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = true, devices = emptyList(), error = null) }
        scanJob = viewModelScope.launch {
            ble.scan(_state.value.onlyHeartRate)
                .catch { e ->
                    _state.update { it.copy(scanning = false, error = e.message ?: "Scan failed.") }
                }
                .collect { device ->
                    _state.update { s ->
                        val others = s.devices.filterNot { it.address == device.address }
                        s.copy(devices = (others + device).sortedByDescending { it.rssi })
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanning = false) }
    }

    fun connect(device: ScannedDevice) {
        stopScan()
        connectJob?.cancel()
        _state.update {
            it.copy(
                connection = ConnectionState.Connecting,
                deviceLabel = device.name ?: device.address,
                heartRate = null,
                error = null,
                debug = emptyList(),
            )
        }
        connectJob = viewModelScope.launch {
            ble.connect(device.address)
                .catch { e ->
                    _state.update {
                        it.copy(connection = ConnectionState.Idle, error = e.message ?: "Connection failed.")
                    }
                }
                .collect { event ->
                    when (event) {
                        GattEvent.Connected ->
                            _state.update { it.copy(connection = ConnectionState.Connected) }
                        GattEvent.Disconnected ->
                            _state.update { it.copy(connection = ConnectionState.Idle, heartRate = null) }
                        is GattEvent.Debug ->
                            _state.update { it.copy(debug = (it.debug + event.message).takeLast(4)) }
                        is GattEvent.HeartRate ->
                            _state.update { it.copy(heartRate = event.bpm) }
                        is GattEvent.Error ->
                            _state.update {
                                it.copy(connection = ConnectionState.Idle, heartRate = null, error = event.message)
                            }
                    }
                }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        _state.update { it.copy(connection = ConnectionState.Idle, heartRate = null) }
    }
}