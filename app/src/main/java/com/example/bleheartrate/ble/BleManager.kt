package com.example.bleheartrate.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Wraps the Android BLE APIs and exposes them as Kotlin Flows.
 *
 * BLE callbacks arrive on Binder threads. callbackFlow + trySend moves them
 * safely into coroutine land, and awaitClose cleans up when the collector
 * is cancelled (scan stops, GATT connection closes).
 *
 * The caller must hold the Bluetooth runtime permissions before using this class.
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {

    private val appContext = context.applicationContext

    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /** Emits every advertisement we see. Collect to scan, cancel to stop. */
    fun scan(onlyHeartRate: Boolean): Flow<ScannedDevice> = callbackFlow {
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth is off. Turn it on and try again."))
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("This phone does not support Bluetooth LE scanning."))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    ScannedDevice(
                        name = result.scanRecord?.deviceName ?: safeName(result.device),
                        address = result.device.address,
                        rssi = result.rssi,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed (error $errorCode)."))
            }
        }

        val filters = if (onlyHeartRate) {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build())
        } else {
            emptyList()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            close(IllegalStateException("Bluetooth permission is missing."))
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission revoked while scanning; nothing left to stop.
            }
        }
    }

    /**
     * Connects to a device, subscribes to heart-rate notifications and emits events.
     * Cancelling the collector disconnects and releases the GATT client.
     */
    fun connect(address: String): Flow<GattEvent> = callbackFlow {
        val device: BluetoothDevice? = try {
            adapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (device == null) {
            close(IllegalStateException("Could not find that device."))
            return@callbackFlow
        }

        val callback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        trySend(GattEvent.Connected)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            trySend(GattEvent.Error("Connection lost (status $status). Try again."))
                        } else {
                            trySend(GattEvent.Disconnected)
                        }
                        close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    trySend(GattEvent.Error("Could not read the device's services (status $status)."))
                    close()
                    return
                }
                val characteristic = gatt.getService(HEART_RATE_SERVICE)
                    ?.getCharacteristic(HEART_RATE_MEASUREMENT)
                if (characteristic == null) {
                    trySend(GattEvent.Error("This device has no heart-rate service."))
                    close()
                    return
                }
                val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                if (descriptor == null) {
                    trySend(GattEvent.Error("Heart-rate characteristic cannot send notifications."))
                    close()
                    return
                }
                val localOk = gatt.setCharacteristicNotification(characteristic, true)
                val writeStarted = writeNotifyDescriptor(gatt, descriptor)
                trySend(GattEvent.Debug("1. Service found. Subscribe requested (local=$localOk, write=$writeStarted)"))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                val message = if (status == BluetoothGatt.GATT_SUCCESS) {
                    "2. Subscribed. Waiting for the device to send data."
                } else {
                    "2. Subscribe FAILED (status $status)."
                }
                trySend(GattEvent.Debug(message))
            }

            // Android 13+ delivers the value directly.
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                emitHeartRate(value)
            }

            // Android 12 and below.
            @Deprecated("Used on Android 12 and below")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                characteristic.value?.let { emitHeartRate(it) }
            }

            private fun emitHeartRate(value: ByteArray) {
                val hex = value.joinToString(" ") { "%02X".format(it) }
                val bpm = HeartRateParser.parse(value)
                if (bpm == null) {
                    trySend(GattEvent.Debug("3. Received [$hex] but it has no heart-rate value."))
                } else {
                    trySend(GattEvent.Debug("3. Received [$hex]"))
                    trySend(GattEvent.HeartRate(bpm))
                }
            }
        }

        val gatt = try {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            close(IllegalStateException("Bluetooth permission is missing."))
            return@callbackFlow
        }

        awaitClose {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: SecurityException) {
                // Permission revoked; the system cleans up the connection.
            }
        }
    }

    /** Returns true if the descriptor write was started. */
    @Suppress("DEPRECATION")
    private fun writeNotifyDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

    private fun safeName(device: BluetoothDevice): String? =
        try {
            device.name
        } catch (_: SecurityException) {
            null
        }

    private companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}