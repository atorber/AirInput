# 构建指南

## 通用

```bash
source scripts/env.sh
pnpm install
pnpm -r build
pnpm test
```

## Android

```bash
source scripts/env.sh
bash scripts/fetch-opus.sh
cd apps/android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

产物：`apps/android/app/build/outputs/apk/debug/app-debug.apk`

## Desktop

```bash
pnpm --filter @airinput/desktop build
```

## macOS RFCOMM Helper

```bash
cd native/macos/rfcomm-server
swift build -c release
```

## Windows RFCOMM Helper（仅 Windows）

```bat
cd native\windows\rfcomm-server
cmake -S . -B build -A x64
cmake --build build --config Release
```

## 虚拟麦克风

见 `docs/phase-4-windows-virtual-mic.md` 与 `docs/phase-5-macos-virtual-mic.md`。
