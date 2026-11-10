# Windows Virtual Microphone

基于 Microsoft SYSVAD 示例思路实现 **AirInput Microphone** Capture Endpoint。

## 状态（本开发机）

**BLOCKED**：当前主机为 macOS，无 WDK / MSVC / 驱动签名环境。

仓库将保留：

- 驱动工程骨架与用户态 PCM 注入接口说明
- 安装 / 卸载脚本模板
- 签名与安全限制说明（不得关闭 Secure Boot / 驱动强制签名来“图方便”）

请在 Windows 10/11 x64 + 匹配的 Windows SDK/WDK 上继续 Phase 4。
