# Phase 2 — RFCOMM POC

## 已完成（代码）

- Android `RfcommClient`（UUID 统一、后台线程 I/O）
- macOS Swift Helper：SDP 发布 + incoming RFCOMM + JSON 状态
- Windows C++ Helper：`AF_BTH` socket、bind、getsockname、WSASetService、listen/accept、Named Pipe IPC（源码）

## 本机限制

| 项 | 状态 |
|---|---|
| macOS Helper 编译 | 执行 `swift build` 验证 |
| macOS↔Android 联调 | PENDING 真机 |
| Windows 编译/联调 | BLOCKED（非 Windows 主机） |

## 下一步

1. 真机连接验证 SDP 可见与吞吐
2. 补齐二进制 IPC 音频转发与应用层认证握手
