#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/native/opus"
VERSION=1.5.2
URL="https://downloads.xiph.org/releases/opus/opus-${VERSION}.tar.gz"
if [[ -f "$DEST/include/opus/opus.h" ]]; then
  echo "opus already present at $DEST"
  exit 0
fi
TMP="$(mktemp -d)"
curl -fsSL "$URL" -o "$TMP/opus.tar.gz"
tar -xzf "$TMP/opus.tar.gz" -C "$TMP"
mkdir -p "$DEST"
rsync -a "$TMP/opus-${VERSION}/include/" "$DEST/include/"
rsync -a "$TMP/opus-${VERSION}/src/" "$DEST/src/"
rsync -a "$TMP/opus-${VERSION}/celt/" "$DEST/celt/"
rsync -a "$TMP/opus-${VERSION}/silk/" "$DEST/silk/"
cp "$TMP/opus-${VERSION}/COPYING" "$DEST/COPYING"
echo "$VERSION" > "$DEST/VERSION"
# Public headers are consumed as <opus/opus.h>
mkdir -p "$DEST/include/opus"
for f in opus.h opus_multistream.h opus_custom.h opus_types.h opus_defines.h opus_projection.h; do
  if [[ -f "$DEST/include/$f" ]]; then
    mv "$DEST/include/$f" "$DEST/include/opus/$f"
  fi
done
# Internal opus sources include "opus_types.h" (flat); public API uses <opus/opus.h>
for f in opus/*.h; do
  base="$(basename "$f")"
  ln -sfn "opus/$base" "$DEST/include/$base"
done
cat > "$DEST/include/config.h" <<'EOF'
#define USE_ALLOCA 1
#define HAVE_LRINT 1
#define HAVE_LRINTF 1
#define VAR_ARRAYS 1
#define OPUS_BUILD 1
#define PACKAGE_VERSION "1.5.2"
EOF
rm -rf "$TMP"
echo "fetched opus $VERSION"
