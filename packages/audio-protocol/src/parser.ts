import {
  HEADER_SIZE,
  MAGIC,
  MAX_PAYLOAD_LENGTH,
  PROTOCOL_VERSION,
  PacketType,
  type DecodedFrame,
  type FrameHeader,
} from "./types.js";

const DEFAULT_MAX_BUFFER = 256 * 1024;

export class ProtocolError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ProtocolError";
  }
}

function parseHeader(buf: Uint8Array, offset: number): FrameHeader {
  const view = new DataView(buf.buffer, buf.byteOffset + offset, HEADER_SIZE);
  const magic = view.getUint16(0, false);
  if (magic !== MAGIC) {
    throw new ProtocolError(`invalid magic 0x${magic.toString(16)}`);
  }
  const version = view.getUint8(2);
  if (version !== PROTOCOL_VERSION) {
    throw new ProtocolError(`unsupported version ${version}`);
  }
  const packetType = view.getUint8(3) as PacketType;
  if (packetType < PacketType.Start || packetType > PacketType.AuthFailed) {
    throw new ProtocolError(`unknown packet type ${packetType}`);
  }
  const sequence = view.getUint32(4, false);
  const timestamp = view.getBigUint64(8, false);
  const payloadLength = view.getUint16(16, false);
  if (payloadLength > MAX_PAYLOAD_LENGTH) {
    throw new ProtocolError(`payload too large: ${payloadLength}`);
  }
  return { magic, version, packetType, sequence, timestamp, payloadLength };
}

/** Streaming parser for RFCOMM byte streams (handles partial frames). */
export class AudioFrameParser {
  private buffer = new Uint8Array(0);
  private readonly maxBufferBytes: number;

  constructor(maxBufferBytes: number = DEFAULT_MAX_BUFFER) {
    this.maxBufferBytes = maxBufferBytes;
  }

  reset(): void {
    this.buffer = new Uint8Array(0);
  }

  push(chunk: Uint8Array): DecodedFrame[] {
    if (chunk.byteLength === 0) {
      return [];
    }
    const merged = new Uint8Array(this.buffer.byteLength + chunk.byteLength);
    merged.set(this.buffer, 0);
    merged.set(chunk, this.buffer.byteLength);
    this.buffer = merged;

    if (this.buffer.byteLength > this.maxBufferBytes) {
      this.reset();
      throw new ProtocolError("parser buffer overflow");
    }

    const frames: DecodedFrame[] = [];
    let offset = 0;
    while (this.buffer.byteLength - offset >= HEADER_SIZE) {
      let header: FrameHeader;
      try {
        header = parseHeader(this.buffer, offset);
      } catch (err) {
        // Resync: drop one byte and continue searching for magic.
        offset += 1;
        continue;
      }
      const total = HEADER_SIZE + header.payloadLength;
      if (this.buffer.byteLength - offset < total) {
        break;
      }
      const payload = this.buffer.slice(offset + HEADER_SIZE, offset + total);
      frames.push({ header, payload });
      offset += total;
    }
    this.buffer = this.buffer.slice(offset);
    return frames;
  }
}

/** Decode a single complete frame buffer (header + payload). */
export function decodeFrame(bytes: Uint8Array): DecodedFrame {
  if (bytes.byteLength < HEADER_SIZE) {
    throw new ProtocolError("frame too short");
  }
  const header = parseHeader(bytes, 0);
  const total = HEADER_SIZE + header.payloadLength;
  if (bytes.byteLength < total) {
    throw new ProtocolError("incomplete frame");
  }
  if (bytes.byteLength > total) {
    throw new ProtocolError("trailing bytes in single-frame decode");
  }
  return {
    header,
    payload: bytes.slice(HEADER_SIZE, total),
  };
}
