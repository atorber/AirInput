# AirInput Android

Kotlin + Jetpack Compose。

- HID：`BluetoothHidDevice`（鼠标 + 键盘）
- 音频：`AudioRecord` → Opus JNI → RFCOMM `BluetoothSocket`
- UUID：`8d53dc1d-1db7-4cd3-868b-8a527460aa84`

```bash
source ../../scripts/env.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

真机验证 HID/RFCOMM 为必需要求；模拟器蓝牙能力不足。
