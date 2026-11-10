# AirInput 开发环境报告

生成时间：2026-11-10  
主机：macOS（本机）  
项目目录：`/Users/luyuchao/Documents/GitHub/AirInput`

## 1. 操作系统

| 项目 | 当前版本 | 要求版本 | 状态 | 处理建议 |
|---|---|---|---|---|
| 操作系统 | macOS 26.6.2 (Build 25G83) | macOS 13+（开发） | PASS | 可继续 macOS / Android 开发 |
| CPU 架构 | arm64（Apple M4 Pro） | Apple Silicon 或 Intel | PASS | — |
| CPU 核心 | 12 | — | PASS | — |
| 内存 | 24 GB | ≥8 GB 建议 | PASS | — |
| 磁盘可用 | 约 38 GB | ≥20 GB 建议 | WARNING | 安装 Android SDK/NDK、Xcode 后空间会紧张，建议保持 ≥25 GB 空闲 |
| 当前用户 | luyuchao | — | PASS | — |
| 工作目录 | `/Users/luyuchao/Documents/GitHub/AirInput` | 空仓库可初始化 | PASS | 初始化前已确认仅有 `.git` |

## 2. 通用开发工具

| 项目 | 当前版本 | 要求版本 | 状态 | 处理建议 |
|---|---|---|---|---|
| Git | 2.39.5 (Apple Git-154) | ≥2.30 | PASS | — |
| Node.js | v26.8.1（Homebrew；另有 nvm v20.19.0） | ≥20 LTS 建议 | WARNING | Electron 生态更稳妥使用 Node 20/22 LTS；仓库脚本将优先使用 PATH 中的 node |
| npm | 11.19.0 | ≥9 | PASS | 工作区统一用 pnpm |
| pnpm | 10.6.4 | ≥9 | PASS | — |
| Java / JDK | Temurin OpenJDK 17.0.13 | JDK 17 | PASS | `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` |
| Kotlin CLI | 未安装 | 可选（Gradle 插件提供编译器） | WARNING | Android 通过 Gradle 插件编译；可选：`brew install kotlin` |
| Gradle CLI | 未安装 | 可选（Android Wrapper 提供） | WARNING | 使用 Android Gradle Wrapper，无需全局 Gradle |
| Python | 3.9.6 | ≥3.9 | PASS | 用于脚本 |
| CMake | 4.4.5（Homebrew） | ≥3.22 | PASS | 已安装 |
| Ninja | 1.13.2 | 任意近期版本 | PASS | 已安装 |
| Clang / LLVM | Apple clang 17.0.0 | 任意近期 | PASS | 来自 Command Line Tools |
| GCC | 指向 Apple clang | — | PASS | macOS 上正常 |
| Docker | 29.8.0 | 可选 | PASS | 可用于交叉验证，非必须 |
| libopus（Homebrew） | 已安装于 `/opt/homebrew/opt/opus` | 需要 | PASS | 桌面/原生解码可链接系统 Opus；Android 将内置预编译或源码构建 |

## 3. Android 开发环境

| 项目 | 当前版本 | 要求版本 | 状态 | 处理建议 |
|---|---|---|---|---|
| Android Studio | 未安装 | 推荐但不强制 | WARNING | 可用命令行 SDK；可选安装 Android Studio |
| Android cmdline-tools | Homebrew cask 已装（sdkmanager 可用） | 需要 | PASS | `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` |
| Android SDK Platform | 已装 28/33/34/35/36 | API 28+ / 编译 34 | PASS | 已通过 sdkmanager 安装 |
| Build Tools | 34.0.0 / 35 / 36 | 34.x | PASS | 已安装 |
| Platform Tools / adb | adb 36.0.0（`~/.local/bin/adb`） | 任意近期 | PASS | 也可使用 SDK 内 platform-tools |
| Android Emulator | 未确认安装 | 可选 | WARNING | HID/蓝牙真机测试优先；模拟器蓝牙能力有限 |
| NDK | 26.1.10909125 与 27.0.12077973 | ≥25 | PASS | App 固定 ndkVersion 27.0.12077973 |
| CMake（SDK） | 3.22.1 | 3.22+ | PASS | Android NDK 构建用 |
| Kotlin | 由 Android Gradle Plugin 提供 | Kotlin 1.9+ / 2.x | PASS（工程配置） | 使用 Gradle Kotlin DSL |
| minSdk | 工程将设为 28 | 28 | PASS | 满足 BluetoothHidDevice |
| 蓝牙运行时权限 | 工程将声明 Android 12+ 权限 | Android 12+ | PASS（工程配置） | `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` + 麦克风权限 |

**Android 真机蓝牙 HID/RFCOMM：本阶段标记为需真机验证，模拟器不可替代。**

## 4. Windows 开发环境

当前开发机 **不是 Windows**。

| 项目 | 当前版本 | 要求版本 | 状态 | 处理建议 |
|---|---|---|---|---|
| Visual Studio / MSVC | 不可用 | VS 2022 + MSVC | BLOCKED | 在 Windows 10/11 x64 机器安装 VS 2022（含 C++、Windows SDK） |
| Windows SDK | 不可用 | 10.0.22621+ 建议 | BLOCKED | 同上 |
| WDK | 不可用 | 与 SDK 匹配 | BLOCKED | 虚拟麦克风驱动需要；签名与安装另见驱动文档 |
| Windows RFCOMM / Winsock 头库 | 不可用 | 需要 | BLOCKED | 本仓库仍提交完整 C++ 源码；在 Windows 上构建验证 |
| 驱动签名/安装 | 不可用 | 需要 | BLOCKED | 不得关闭安全启动；保留源码与人工步骤 |

**替代验证：** 在本机完成源码、协议测试、IPC 契约测试；Windows 二进制构建与 RFCOMM/驱动安装在 Windows 主机执行。

## 5. macOS 开发环境

| 项目 | 当前版本 | 要求版本 | 状态 | 处理建议 |
|---|---|---|---|---|
| Xcode.app | 未安装 / 未选中 | 推荐完整 Xcode | WARNING | 当前 `xcode-select` 指向 Command Line Tools；完整 IOBluetooth 调试与签名建议安装 Xcode：`xcode-select -s /Applications/Xcode.app/Contents/Developer` |
| Command Line Tools | 已安装 | 需要 | PASS | 可编译 Swift/C |
| Swift | 6.1 | 5.9+ | PASS | — |
| SwiftPM | 随 Swift 提供 | 需要 | PASS | RFCOMM Helper 可用 SwiftPM |
| IOBluetooth | 系统框架存在 | 需要 | WARNING | 需真机蓝牙联调；完整 Xcode 更利于调试 |
| CoreAudio | 系统框架存在 | 需要 | PASS | AudioServerPlugIn 开发可用；加载插件通常需签名/公证流程 |
| Apple Silicon | 是（M4 Pro） | 支持 | PASS | — |
| macOS SDK | CLT 提供 | 需要 | WARNING | 建议安装完整 Xcode 以获得最新 SDK |

## 6. 蓝牙硬件

| 项目 | 当前版本 | 要求版本 | 状态 | 处理建议 |
|---|---|---|---|---|
| 蓝牙适配器 | 存在（Apple BCM_4388C2，PCIe） | 需要 | PASS | — |
| 蓝牙开关 | On | On | PASS | — |
| Bluetooth Classic | Supported services 含 **SerialPort**、HID、A2DP 等 | 必须支持 Classic（非仅 BLE） | PASS | 含 SerialPort，可进行 RFCOMM 开发测试 |
| RFCOMM 服务端测试 | 硬件能力具备 | 需要 | WARNING | 需与 Android 真机联调；macOS 对来自手机的 HID Host / RFCOMM 行为需实测 |
| 已配对设备 | 耳机/平板等已配对（名称见系统蓝牙） | — | PASS | 报告不记录设备地址 |

**说明：** 不能将“仅 BLE”适配器视为合格。本机服务列表含 SerialPort，判定为具备 Classic 能力。

## 7. 总判定与可继续任务

| 领域 | 状态 | 说明 |
|---|---|---|
| Monorepo / TS / Electron / 协议 | PASS | 可立即开发与单测 |
| Android 应用工程 | WARNING → 推进中 | SDK 组件安装后可编译；HID/RFCOMM 需真机 |
| macOS RFCOMM Helper | WARNING | 可写源码并用 Swift 构建；蓝牙联调需真机 |
| macOS 虚拟麦克风 | WARNING | 可写 AudioServerPlugIn 源码；安装/加载可能需签名 |
| Windows RFCOMM / 虚拟麦 | BLOCKED（本机） | 仅提交源码与文档；换 Windows 机验证 |
| 端到端音频进系统麦 | BLOCKED（本阶段） | 依赖虚拟麦安装 + 真机蓝牙 |

## 8. 建议的本机安装命令（需确认后执行）

```bash
# 可选但强烈推荐
# 安装完整 Xcode（App Store），然后：
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer

# 原生构建工具
brew install cmake ninja

# Android SDK（已用 Homebrew cmdline-tools）
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platforms;android-34" "platforms;android-28" \
  "build-tools;34.0.0" "platform-tools" \
  "ndk;26.1.10909125" "cmake;3.22.1"

# JDK
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## 9. 环境变量建议（写入 `scripts/env.sh`）

见仓库 `scripts/env.sh`。

---

**下一步：** 初始化 Monorepo，优先实现 Android HID POC、跨平台音频协议与 macOS RFCOMM Helper；Windows 模块提交可构建源码并标记本机 BLOCKED。
