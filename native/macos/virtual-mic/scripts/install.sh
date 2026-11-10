#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DRIVER_SRC="$ROOT/build/AirInputMic.driver"
DEST="/Library/Audio/Plug-Ins/HAL/AirInputMic.driver"
STAGE="${TMPDIR:-/tmp}/airinput-vmic-stage-$$"

if [[ ! -d "$DRIVER_SRC" ]]; then
  echo "缺少 $DRIVER_SRC ，请先: cmake -S . -B build && cmake --build build"
  exit 1
fi

echo "将安装到 $DEST （需要管理员权限）"
echo "注意：未签名驱动在较新 macOS 上可能无法被 coreaudiod 加载。"

# Stage out of TCC-restricted paths (e.g. Documents) before escalating.
rm -rf "$STAGE"
mkdir -p "$STAGE"
ditto "$DRIVER_SRC" "$STAGE/AirInputMic.driver"

sudo mkdir -p /Library/Audio/Plug-Ins/HAL
sudo rm -rf "$DEST"
sudo ditto "$STAGE/AirInputMic.driver" "$DEST"
sudo chown -R root:wheel "$DEST"
rm -rf "$STAGE"

echo "重启 coreaudiod ..."
sudo killall coreaudiod 2>/dev/null || true
sleep 1
echo "完成。请在「系统设置 → 声音 → 输入」查看 AirInput Microphone。"
echo "若未见设备：检查签名/公证，或查看 Console 中 coreaudiod 日志。"
