# 测试

## 自动（本机可跑）

```bash
pnpm test
cd apps/android && ./gradlew :app:testDebugUnitTest
cd native/macos/rfcomm-server && swift build
# Windows protocol unit test（跨平台可编译部分）：
cmake -S native/common -B native/common/build && cmake --build native/common/build
```

## 真机 / 硬件（不可伪造）

| 项 | 状态 |
|---|---|
| Android HID 被 macOS/Windows 识别为鼠标键盘 | 待真机 |
| RFCOMM 建链 + 收发包 | 待真机 |
| Opus 往返听感 / WAV dump | 待 Phase 3 联调 |
| 虚拟麦出现在系统输入列表 | macOS WARNING/签名；Windows BLOCKED 本机 |

## 集成清单

- HID + RFCOMM 并发
- 断连释放按键
- 切后台 HID 注销与恢复
- 长时间录音缓冲上限
