# AirInput Desktop

Electron + Vue 3 + Pinia 桌面端。

- Main：生命周期与 RFCOMM Helper 进程管理
- Preload：`contextBridge` 暴露 `window.airinput`
- Renderer：状态面板（服务启停、收包、虚拟麦状态）

```bash
pnpm --filter @airinput/desktop build
pnpm --filter @airinput/desktop test
```

macOS Helper 需先构建：`native/macos/rfcomm-server`。
