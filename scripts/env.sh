#!/usr/bin/env bash
# Source before Android/native builds: `source scripts/env.sh`
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

# Prefer Homebrew cmake/ninja when present
if [[ -d /opt/homebrew/bin ]]; then
  export PATH="/opt/homebrew/bin:$PATH"
fi

if [[ -n "${JAVA_HOME}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ -d "$ANDROID_HOME/platform-tools" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi

if [[ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]]; then
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi

echo "JAVA_HOME=${JAVA_HOME:-unset}"
echo "ANDROID_HOME=${ANDROID_HOME}"
