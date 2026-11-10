#!/usr/bin/env bash
set -euo pipefail
DEST="/Library/Audio/Plug-Ins/HAL/AirInputMic.driver"
RING="/tmp/airinput-vmic.ring"

echo "卸载 $DEST"
sudo rm -rf "$DEST"
rm -f "$RING"
sudo killall coreaudiod 2>/dev/null || true
echo "完成。"
