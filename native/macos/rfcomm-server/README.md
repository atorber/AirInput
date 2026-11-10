# macOS RFCOMM Server Helper

Swift + IOBluetooth 独立进程：

1. `IOBluetoothSDPServiceRecord.publishedServiceRecord(with:)` 发布 SDP
2. `getRFCOMMChannelID` 读取实际 channel
3. `IOBluetoothRFCOMMChannel.register(forChannelOpenNotifications:...)` 接受来电
4. `IOBluetoothRFCOMMChannelDelegate.rfcommChannelData` 收包
5. 状态以 JSON 行输出到 stdout；控制命令从 stdin 读取（`{"cmd":"stop"}`）

```bash
cd native/macos/rfcomm-server
swift build -c release
.build/release/AirInputRfcommServer --uuid 8d53dc1d-1db7-4cd3-868b-8a527460aa84
```

**注意：** 真机 Android 连接、SDP 可见性需人工验证。本机无完整 Xcode 时可用 Command Line Tools 编译。
