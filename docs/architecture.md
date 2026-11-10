# AirInput 架构

## 数据面

```
Android Mic ──PCM──► Opus Encoder ──RFCOMM──► Desktop Helper ──PCM──► Virtual Mic ──► OS / 语音输入法
Android Touchpad ──HID Reports──────────────────────────────► OS 鼠标/键盘栈
```

## 控制面

- Android：前台 UI 管理 HID 注册、RFCOMM 连接、录音启停
- Desktop Electron：启停原生 Helper，展示状态；不在主线程做蓝牙 I/O
- Helper ↔ Electron：
  - macOS：Unix Domain Socket + stdout JSON 状态行
  - Windows：Named Pipe（控制 JSON 行 / 音频二进制）

## 协议

见 `packages/audio-protocol`。应用层 Challenge/HMAC 认证在 RFCOMM 建链后、START 音频前完成。

## 平台边界

| 模块 | 技术 |
|---|---|
| Android HID | `BluetoothHidDevice` |
| Android RFCOMM | `BluetoothSocket` Classic |
| macOS RFCOMM | IOBluetooth SDP + RFCOMMChannel |
| Windows RFCOMM | Winsock `AF_BTH` + `WSASetService` |
| macOS 虚拟麦 | CoreAudio AudioServerPlugIn |
| Windows 虚拟麦 | SYSVAD 风格 AVStream Capture |
