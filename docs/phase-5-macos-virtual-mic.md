# Phase 5 — macOS 虚拟麦克风

目标设备名：`AirInput Microphone`

技术：CoreAudio AudioServerPlugIn。

## 本机状态

| 项 | 状态 | 证据 |
|---|---|---|
| 共享 Ring Buffer + 单测 | PASS | `ctest` → `shared_ring_tests` |
| AudioServerPlugIn 主体编译 | PASS | `build/AirInputMic.driver`（arm64，`AirInput_Create`） |
| PCM 注入 CLI | PASS | `airinput_pcm_inject --tone` |
| 桌面端检测/一键安装 | DONE（代码） | Electron「安装虚拟麦克风」→ osascript 提权拷贝到 HAL |
| 安装到 HAL / 系统枚举 | PENDING 人工 | 需管理员密码；未签名可能无法加载 |

## 构建产物

```bash
cd native/macos/virtual-mic
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
ctest --test-dir build --output-on-failure
```

## 安装方式

1. **推荐**：启动桌面端 → 点「安装虚拟麦克风」（弹密码框）
2. 或手动：`./scripts/install.sh`

## 架构要点

- Helper 解码后写 `/tmp/airinput-vmic.ring`（s16le）
- 插件 IO 读 ring → float；欠载静音
- IO 回调禁止蓝牙与阻塞
