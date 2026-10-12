# BLE Heart Rate (Android, Kotlin, Jetpack Compose)

A small app that scans for Bluetooth LE heart-rate devices, connects over GATT,
and shows the live heart rate. Built to practise the Central side of BLE.

## Run it

1. Open this folder in Android Studio (JDK 17). Let Gradle sync.
   If Android Studio offers to upgrade the Android Gradle Plugin, accept.
2. Connect a **physical** Android phone (USB debugging on). Emulators do not support BLE.
3. Press Run.

## Test it with an iPhone as the fake heart-rate device

1. Install **LightBlue** on the iPhone, open Virtual Devices, and switch on **Heart Rate**.
   Keep the app in the foreground.
2. On the Android phone: allow Bluetooth access, tap **Start scan**, tap the iPhone in the list.
3. The heart-rate number updates as the iPhone sends new values.

If nothing shows up: turn off "Heart-rate devices only", and check Bluetooth is on.
On Android 11 and below, Location must also be on for scanning.

## Structure

| File | Role |
|---|---|
| `ble/BleManager.kt` | Scan, connect, notifications, wrapped as Kotlin Flows (`callbackFlow`) |
| `ble/HeartRateParser.kt` | Decodes the Heart Rate Measurement bytes (pure Kotlin) |
| `ble/Models.kt` | `ScannedDevice`, `GattEvent` |
| `ui/BleViewModel.kt` | MVVM: holds `StateFlow<UiState>`, runs scan/connect jobs in `viewModelScope` |
| `ui/Screens.kt` | Compose screens: permission, scan list, heart-rate display |
| `MainActivity.kt` | Permissions, back handling, screen switching |
| `test/.../HeartRateParserTest.kt` | JUnit tests for the parser |

## BLE flow in four lines

1. Peripheral (iPhone) advertises the Heart Rate service (UUID 0x180D).
2. Central (this app) scans, then calls `connectGatt`.
3. After `discoverServices`, it finds characteristic 0x2A37 and writes the
   notification descriptor (0x2902).
4. Each notification arrives in `onCharacteristicChanged`, is parsed, and flows
   to the UI through `StateFlow`.
