# Windows RFCOMM Server

C++ / Winsock `AF_BTH` + `BTHPROTO_RFCOMM` + `WSASetService` SDP 发布。

与 Electron 的 IPC：

- 控制：Named Pipe `\\.\pipe\airinput-control`（JSON 行）
- 音频：Named Pipe `\\.\pipe\airinput-audio`（原始二进制，禁止 Base64）

## 构建（必须在 Windows x64）

```bat
cmake -S . -B build -A x64
cmake --build build --config Release
ctest --test-dir build -C Release
```

本 macOS 开发机无法链接 Windows Bluetooth 库，构建状态：**BLOCKED（本机）**。源码已提交，请在 Windows 10/11 + VS 2022 验证。
