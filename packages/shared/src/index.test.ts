import { describe, expect, it } from "vitest";
import {
  AIRINPUT_RFCOMM_UUID,
  AUDIO_FRAME_SAMPLES,
  AUDIO_SAMPLE_RATE,
  OPUS_TARGET_BITRATE,
} from "./index.js";

describe("@airinput/shared", () => {
  it("uses the locked RFCOMM UUID", () => {
    expect(AIRINPUT_RFCOMM_UUID).toBe("8d53dc1d-1db7-4cd3-868b-8a527460aa84");
  });

  it("locks audio geometry to 48kHz / 20ms / 960 samples / 32kbps", () => {
    expect(AUDIO_SAMPLE_RATE).toBe(48_000);
    expect(AUDIO_FRAME_SAMPLES).toBe(960);
    expect(OPUS_TARGET_BITRATE).toBe(32_000);
  });
});
