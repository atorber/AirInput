/** Shared constants for AirInput Android + desktop. */

export const AIRINPUT_RFCOMM_UUID = "8d53dc1d-1db7-4cd3-868b-8a527460aa84";

export const AUDIO_SAMPLE_RATE = 48_000;
export const AUDIO_CHANNELS = 1;
export const AUDIO_FRAME_MS = 20;
export const AUDIO_FRAME_SAMPLES = 960; // 48000 * 0.02
export const OPUS_TARGET_BITRATE = 32_000;
export const PCM_BYTES_PER_SAMPLE = 2;

export const VIRTUAL_MIC_NAME = "AirInput Microphone";

/** Default HMAC pairing key (UTF-8). Must match Android + macOS Helper. */
export const DEFAULT_PAIRING_KEY = "AirInput-Default-Pairing-Key-v1!!";

export const MACOS_VIRTUAL_MIC_DEST =
  "/Library/Audio/Plug-Ins/HAL/AirInputMic.driver";

export type ConnectionState =
  | "disconnected"
  | "connecting"
  | "connected"
  | "authenticated"
  | "error";

export interface DeviceAuthChallenge {
  /** Random challenge bytes as hex */
  challengeHex: string;
  /** Unix ms */
  issuedAt: number;
}
