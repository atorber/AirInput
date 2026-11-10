# Phase 4 — Windows 虚拟麦克风

**本机状态：BLOCKED**（无 WDK/MSVC）。

计划：

1. 基于 SYSVAD 建立 Capture Endpoint「AirInput Microphone」
2. 用户态 → 驱动 Ring Buffer 注入 PCM s16le 48k mono
3. 欠载填静音
4. 安装/卸载脚本 + 签名说明（不关闭系统安全）

源码目录：`native/windows/virtual-mic/`
