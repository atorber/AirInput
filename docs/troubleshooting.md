# 排障

## Android 无法注册 HID

- 确认 API ≥ 28
- 确认已授予 `BLUETOOTH_CONNECT`
- 查看 logcat：`AirInputHID`，等待 `onAppStatusChanged registered=true`
- 部分 OEM 在后台会注销 HID：保持前台

## RFCOMM 连接失败（`read failed, socket might closed`）

这是 Android `BluetoothSocket.connect()` 的常见文案，多半是建链失败而非读线程问题。

- **先**在电脑点「开启服务」，确认 Helper `listening`，再在手机点连接
- 两端 UUID 必须同为 `8d53dc1d-1db7-4cd3-868b-8a527460aa84`
- 手机与电脑需已系统蓝牙配对
- App 会先连 HID、约 1.2s 后再连 RFCOMM，并自动尝试 insecure / 反射信道回退
- 仍失败：关掉手机蓝牙再开，或取消配对后重配；查看 logcat 标签 `AirInputRFCOMM`

## 虚拟麦安装报 `Operation not permitted`

提权后的 `osascript` 读不了 `~/Documents` 等受保护目录。桌面端会先把 `.driver` 暂存到 `/tmp` 再安装；请使用最新桌面构建重试。

也可手动：

```bash
cd native/macos/virtual-mic && ./scripts/install.sh
```

## 系统「声音 → 输入」没有 AirInput Microphone

- 驱动文件在 `/Library/Audio/Plug-Ins/HAL/` **不等于**会被加载
- 新版 macOS 拒绝 **ad-hoc / 未签名** AudioServerPlugIn（`spctl` 会 rejected）
- 本机需有 **Developer ID Application** 证书对 `.driver` 签名（建议再公证）；不要关 SIP/Gatekeeper 绕过
- 无证书时的验听：手机开麦后看 `/tmp/airinput-capture.wav`，停麦后用 QuickTime 打开

## 有声音但系统听不到

- 桌面端「虚拟麦克风」应为「已安装」；否则点「安装虚拟麦克风」
- 系统设置 → 声音 → 输入 是否出现 AirInput Microphone（未签名时可能缺失）
- Helper 状态是否 `authenticated` / `streaming`，收包与 decodeErrors
- 欠载会填静音：确认 `/tmp/airinput-vmic.ring` 存在且收包增长

## 认证失败

- 两端默认密钥须一致：`AirInput-Default-Pairing-Key-v1!!`
- 手机须在收到 AUTH_OK 后再开麦
- Helper 需先 listening，再由手机发起 RFCOMM

## Windows 驱动

- 不要关闭 Secure Boot / 驱动签名强制来绕过
- 使用测试签名时仅在受控开发机，并按 WDK 文档操作
