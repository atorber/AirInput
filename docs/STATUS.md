# AirInput 阶段性交付状态（2026-11-10）

## 1. 当前开发机环境

- macOS 26.6.2 / Apple M4 Pro / 24GB
- Bluetooth Classic：**PASS**（含 SerialPort）
- Node/pnpm/JDK17/CMake/Ninja/Android cmdline-tools：**可用**
- 完整 Xcode.app：**未安装**（CLT 可用；Swift Helper 已用 CLT 编过）
- Windows / WDK：**本机 BLOCKED**

详见 `docs/development-environment.md`。

## 2. 已完成模块

| 模块 | 状态 | 证据 |
|---|---|---|
| Monorepo 初始化 | DONE | `pnpm-workspace.yaml` 等 |
| 音频协议 TS + 向量 | DONE | `pnpm test` 通过 |
| 共享常量 | DONE | vitest 通过 |
| C++ 协议编解码 | DONE | `FrameParser` + `protocol_tests` |
| Android HID/UI/RFCOMM/音频/认证 | DONE（可编译） | `assembleDebug` + unit tests |
| Opus JNI | DONE | CMake 三 ABI |
| macOS RFCOMM Helper（解码写 ring） | DONE（可编译） | `swift build -c release`；含 Opus+HMAC |
| Electron/Vue 桌面壳 + 虚拟麦安装 | DONE（可编译） | `pnpm --filter @airinput/desktop build` |
| macOS 虚拟麦驱动 | DONE（可编译） | `AirInputMic.driver`；系统加载 PENDING |
| Windows RFCOMM / 虚拟麦 | 源码 / BLOCKED | 本机无 MSVC/WDK |

## 3. 未完成 / 未实测（不得标 PASS）

- Android 真机 HID/RFCOMM 联调
- 虚拟麦安装后系统输入列表枚举与录音
- 端到端「手机说话 → AirInput Microphone」听感
- Windows 构建与驱动安装

## 4. 编译情况

- `pnpm` shared/desktop tests：PASS
- C++ `protocol_tests`：PASS
- `swift build -c release`（含 pipeline）：PASS
- `./gradlew :app:assembleDebug`：PASS
- desktop `dist/` + `dist-electron/`：PASS
- virtual-mic `.driver`：PASS（此前已构建）

## 5. 实测步骤（macOS）

1. 装 APK：`adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk`
2. 系统蓝牙配对手机 ↔ Mac
3. 启动桌面：`pnpm --filter @airinput/desktop start`（需已 approve electron）
4. 点「安装虚拟麦克风」→ 输入管理员密码
5. 点「开启服务」
6. 手机 App 选电脑 → 连接 → 等「已认证」→ 开麦 / 测触控板
7. 系统声音输入选 **AirInput Microphone**，用 QuickTime 录音验证

默认配对密钥：`AirInput-Default-Pairing-Key-v1!!`（仅开发用）。

## 6. 已知风险

- 未签名 HAL 插件可能不被 coreaudiod 加载
- 部分 OEM 后台注销 HID
- pnpm 默认忽略 electron 构建脚本，需 `pnpm approve-builds`
