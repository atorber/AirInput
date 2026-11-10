# @airinput/audio-protocol

跨平台音频帧协议（Big Endian header 18 bytes）。

Magic `0x4149`，Version `1`，Payload ≤ 4096。

跨语言向量：`testdata/header-vectors.json`（TypeScript / Kotlin / C++ 共用）。

```bash
pnpm --filter @airinput/audio-protocol test
```
