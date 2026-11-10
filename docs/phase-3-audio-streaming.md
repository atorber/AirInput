# Phase 3 — 音频传输

## 已完成（代码）

- 跨端协议 + 向量测试（TS / C++ FrameParser）
- Android `AudioRecord` 48k/mono/20ms + Opus JNI + 帧封装发送
- macOS Helper：流式解帧 + Opus 解码 + 写 `/tmp/airinput-vmic.ring`
- Challenge / HMAC-SHA256 认证闭环（默认配对密钥，见 `@airinput/shared`）
- libopus：Android 源码编；macOS Helper 链 Homebrew opus

## 未完成 / 人工

- 真机长时录音与丢包统计
- 端到端听感验证（装驱动 + 选 AirInput Microphone）

状态：代码路径已接通；真机听感 **PENDING**。
