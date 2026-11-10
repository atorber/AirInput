# 开发指南

1. 先读 `docs/development-environment.md`，确认本机 PASS/WARNING/BLOCKED。
2. 协议变更必须同步更新：
   - TypeScript `packages/audio-protocol`
   - Kotlin `apps/android/.../AudioFrame.kt`
   - C++ `native/common/audio_protocol.*`
   - 并重新生成/校验 `testdata/header-vectors.json`
3. Android HID 成功以 `onAppStatusChanged(registered=true)` 为准。
4. 音频路径禁止 Base64；禁止在 Electron 主线程做编解码。
5. 未实测功能不得标 PASS。
