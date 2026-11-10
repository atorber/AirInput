import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  CodecId,
  PacketType,
  decodeFrame,
  decodeStartPayload,
  encodeFrame,
  encodeStartPayload,
} from "./index.js";
import { AudioFrameParser, ProtocolError } from "./parser.js";

const here = dirname(fileURLToPath(import.meta.url));
const vectorsPath = join(here, "../testdata/header-vectors.json");

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

describe("audio protocol", () => {
  it("encodes fixed START header matching cross-language vectors", () => {
    const vectors = JSON.parse(readFileSync(vectorsPath, "utf8")) as {
      cases: Array<{
        name: string;
        packetType: number;
        sequence: number;
        timestamp: string;
        payloadHex: string;
        frameHex: string;
      }>;
    };

    for (const c of vectors.cases) {
      const payload = Uint8Array.from(
        (c.payloadHex.match(/.{1,2}/g) ?? []).map((h) => parseInt(h, 16)),
      );
      const frame = encodeFrame(
        c.packetType as PacketType,
        c.sequence,
        BigInt(c.timestamp),
        payload,
      );
      expect(toHex(frame)).toBe(c.frameHex);
      const decoded = decodeFrame(frame);
      expect(decoded.header.sequence).toBe(c.sequence);
      expect(toHex(decoded.payload)).toBe(c.payloadHex);
    }
  });

  it("round-trips START payload fields", () => {
    const payload = encodeStartPayload({
      sampleRate: 48_000,
      channels: 1,
      frameMs: 20,
      bitrate: 32_000,
      codec: CodecId.Opus,
    });
    expect(decodeStartPayload(payload)).toEqual({
      sampleRate: 48_000,
      channels: 1,
      frameMs: 20,
      bitrate: 32_000,
      codec: CodecId.Opus,
    });
  });

  it("parses split chunks and multiple frames", () => {
    const a = encodeFrame(PacketType.Ping, 1, 10n, new Uint8Array([1]));
    const b = encodeFrame(PacketType.Pong, 2, 11n, new Uint8Array([2, 3]));
    const stream = new Uint8Array(a.length + b.length);
    stream.set(a, 0);
    stream.set(b, a.length);

    const parser = new AudioFrameParser();
    const mid = 7;
    const first = parser.push(stream.slice(0, mid));
    expect(first).toHaveLength(0);
    const rest = parser.push(stream.slice(mid));
    expect(rest).toHaveLength(2);
    expect(rest[0]?.header.packetType).toBe(PacketType.Ping);
    expect(rest[1]?.header.packetType).toBe(PacketType.Pong);
  });

  it("rejects invalid magic and oversized payload", () => {
    const bad = encodeFrame(PacketType.Audio, 1, 1n, new Uint8Array([9]));
    bad[0] = 0x00;
    expect(() => decodeFrame(bad)).toThrow(ProtocolError);

    expect(() =>
      encodeFrame(PacketType.Audio, 1, 1n, new Uint8Array(4097)),
    ).toThrow(/payload exceeds/);
  });

  it("rejects unsupported version", () => {
    const frame = encodeFrame(PacketType.Stop, 3, 5n);
    frame[2] = 99;
    expect(() => decodeFrame(frame)).toThrow(/version/);
  });
});
