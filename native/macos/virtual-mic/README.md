# macOS Virtual Microphone（AudioServerPlugIn）

在系统输入设备列表中提供 **AirInput Microphone**（48 kHz / Mono / Float32 物理格式）。

Helper / 注入器写入 `/tmp/airinput-vmic.ring`（s16le PCM），插件在实时 IO 回调中读取并转为 float；欠载填静音。IO 路径不做蓝牙、不做堆分配。

## 构建

```bash
cd native/macos/virtual-mic
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
ctest --test-dir build --output-on-failure
```

产物：

- `build/AirInputMic.driver` — HAL 插件包
- `build/airinput_pcm_inject` — 测试音/原始 PCM 注入
- `build/shared_ring_tests` — Ring Buffer 单测

## 安装（人工，需确认）

```bash
./scripts/install.sh
```

卸载：

```bash
./scripts/uninstall.sh
```

### 签名说明

正式环境应对 `AirInputMic.driver` 使用 Developer ID 签名并按需公证。  
本仓库**不会**关闭 Gatekeeper / SIP。未签名时设备可能不出现，属预期限制（文档记为 WARNING）。

## 本地验证（安装成功后）

```bash
# 注入 3 秒 440Hz 测试音
./build/airinput_pcm_inject --tone --seconds 3 --hz 440

# 另开终端：用系统录音或 QuickTime「新建音频录制」选择 AirInput Microphone
```

## 与 RFCOMM Helper 对接

解码后的 PCM s16le 调用与 `airinput_pcm_inject` 相同的 `SharedRingWriteS16` 写入共享环即可。
