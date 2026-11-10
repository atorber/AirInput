export const MAGIC = 0x4149;
export const PROTOCOL_VERSION = 1;
export const HEADER_SIZE = 18;
export const MAX_PAYLOAD_LENGTH = 4096;

export enum PacketType {
  Start = 1,
  Audio = 2,
  Stop = 3,
  Ping = 4,
  Pong = 5,
  Challenge = 6,
  AuthResponse = 7,
  AuthOk = 8,
  AuthFailed = 9,
}

export enum CodecId {
  Opus = 1,
}

export interface FrameHeader {
  magic: number;
  version: number;
  packetType: PacketType;
  sequence: number;
  timestamp: bigint;
  payloadLength: number;
}

export interface StartPayload {
  sampleRate: number;
  channels: number;
  frameMs: number;
  bitrate: number;
  codec: CodecId;
}

export interface DecodedFrame {
  header: FrameHeader;
  payload: Uint8Array;
}
