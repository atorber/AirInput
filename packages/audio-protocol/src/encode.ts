import {
  CodecId,
  HEADER_SIZE,
  MAGIC,
  MAX_PAYLOAD_LENGTH,
  PROTOCOL_VERSION,
  PacketType,
  type StartPayload,
} from "./types.js";

function assertUInt32(n: number, name: string): void {
  if (!Number.isInteger(n) || n < 0 || n > 0xffff_ffff) {
    throw new RangeError(`${name} must be uint32`);
  }
}

function assertUInt16(n: number, name: string): void {
  if (!Number.isInteger(n) || n < 0 || n > 0xffff) {
    throw new RangeError(`${name} must be uint16`);
  }
}

/** Encode a protocol frame. Multi-byte fields are Big Endian. */
export function encodeFrame(
  packetType: PacketType,
  sequence: number,
  timestamp: bigint,
  payload: Uint8Array = new Uint8Array(0),
): Uint8Array {
  assertUInt32(sequence, "sequence");
  if (timestamp < 0n || timestamp > 0xffff_ffff_ffff_ffffn) {
    throw new RangeError("timestamp must be uint64");
  }
  if (payload.byteLength > MAX_PAYLOAD_LENGTH) {
    throw new RangeError(`payload exceeds ${MAX_PAYLOAD_LENGTH}`);
  }
  assertUInt16(payload.byteLength, "payloadLength");

  const out = new Uint8Array(HEADER_SIZE + payload.byteLength);
  const view = new DataView(out.buffer, out.byteOffset, out.byteLength);
  view.setUint16(0, MAGIC, false);
  view.setUint8(2, PROTOCOL_VERSION);
  view.setUint8(3, packetType);
  view.setUint32(4, sequence, false);
  view.setBigUint64(8, timestamp, false);
  view.setUint16(16, payload.byteLength, false);
  out.set(payload, HEADER_SIZE);
  return out;
}

export function encodeStartPayload(payload: StartPayload): Uint8Array {
  const out = new Uint8Array(4 + 1 + 1 + 4 + 1);
  const view = new DataView(out.buffer);
  view.setUint32(0, payload.sampleRate, false);
  view.setUint8(4, payload.channels);
  view.setUint8(5, payload.frameMs);
  view.setUint32(6, payload.bitrate, false);
  view.setUint8(10, payload.codec);
  return out;
}

export function decodeStartPayload(bytes: Uint8Array): StartPayload {
  if (bytes.byteLength < 11) {
    throw new Error("START payload too short");
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const codec = view.getUint8(10);
  if (codec !== CodecId.Opus) {
    throw new Error(`unsupported codec: ${codec}`);
  }
  return {
    sampleRate: view.getUint32(0, false),
    channels: view.getUint8(4),
    frameMs: view.getUint8(5),
    bitrate: view.getUint32(6, false),
    codec: CodecId.Opus,
  };
}
