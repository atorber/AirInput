# AirInput

用 Android 手机作为电脑的 **蓝牙鼠标/键盘 + 无线麦克风**。

- 手机平台：Android（Kotlin）
- 桌面：Windows + macOS（Electron + Vue 3 + 原生 Helper）
- 键鼠：Bluetooth HID Device
- 音频：Bluetooth Classic RFCOMM + Opus 48 kHz Mono @ 32 kbps
- 核心功能不依赖互联网

## 仓库结构

```
apps/android          Android App
apps/desktop          Electron + Vue 3
packages/audio-protocol  跨端音频帧协议
packages/shared       共享常量
native/macos/*        macOS RFCOMM / 虚拟麦
native/windows/*      Windows RFCOMM / 虚拟麦
native/opus           libopus 源码
docs/                 环境与阶段文档
```

## 快速开始（本机 macOS）

```bash
source scripts/env.sh
pnpm install
pnpm test
bash scripts/fetch-opus.sh

# Android
cd apps/android && ./gradlew :app:assembleDebug

# macOS RFCOMM helper（含 Opus 解码 → 虚拟麦 ring）
cd native/macos/rfcomm-server && swift build -c release

# 虚拟麦驱动
cd native/macos/virtual-mic && cmake -S . -B build && cmake --build build

# 桌面端
pnpm --filter @airinput/desktop build
pnpm --filter @airinput/desktop start   # 首次可能需 pnpm approve-builds
```

桌面端可一键安装虚拟麦（管理员密码）。实测步骤见 `docs/STATUS.md`。

Windows RFCOMM / 虚拟麦克风驱动需在 Windows + VS 2022 / WDK 上构建（本机标记 BLOCKED）。

## 文档

- [开发环境](docs/development-environment.md)
- [架构](docs/architecture.md)
- [构建](docs/build.md)
- [测试](docs/testing.md)
- [排障](docs/troubleshooting.md)
