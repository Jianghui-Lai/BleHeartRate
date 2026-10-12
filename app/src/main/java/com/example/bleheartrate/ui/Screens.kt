package com.example.bleheartrate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bleheartrate.ble.ScannedDevice

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bluetooth access needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(8.dp))
        Text(
            "This app scans for nearby heart-rate devices and connects to one. " +
                    "Allow Bluetooth access to continue.",
        )
        Spacer(Modifier.padding(12.dp))
        Button(onClick = onGrant) { Text("Allow Bluetooth access") }
    }
}

@Composable
fun ScanScreen(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFilterChange: (Boolean) -> Unit,
    onDeviceClick: (ScannedDevice) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = if (state.scanning) onStop else onStart) {
                Text(if (state.scanning) "Stop scan" else "Start scan")
            }
            Spacer(Modifier.weight(1f))
            Text("Heart-rate devices only")
            Spacer(Modifier.width(8.dp))
            Switch(checked = state.onlyHeartRate, onCheckedChange = onFilterChange)
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (state.devices.isEmpty()) {
            Text(
                text = if (state.scanning) {
                    "Looking for devices…"
                } else {
                    "Tap Start scan to look for nearby devices."
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.devices, key = { it.address }) { device ->
                DeviceRow(device, onDeviceClick)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice, onClick: (ScannedDevice) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(device) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name ?: "Unknown device", fontWeight = FontWeight.Medium)
            Text(device.address, style = MaterialTheme.typography.bodySmall)
        }
        Text("${device.rssi} dBm", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HeartRateScreen(state: UiState, onDisconnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.deviceLabel, style = MaterialTheme.typography.titleMedium)
        Text(
            text = when (state.connection) {
                ConnectionState.Connecting -> "Connecting…"
                ConnectionState.Connected ->
                    if (state.heartRate == null) "Connected. Waiting for data…" else "Connected"
                ConnectionState.Idle -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.padding(16.dp))
        Text(
            text = state.heartRate?.toString() ?: "--",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("beats per minute", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(24.dp))
        Button(onClick = onDisconnect) { Text("Disconnect") }
        Spacer(Modifier.padding(16.dp))
        state.debug.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}