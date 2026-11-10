import { describe, expect, it } from "vitest";
import { AIRINPUT_RFCOMM_UUID, DEFAULT_PAIRING_KEY, MACOS_VIRTUAL_MIC_DEST } from "@airinput/shared";

describe("desktop constants", () => {
  it("shares RFCOMM UUID with Android", () => {
    expect(AIRINPUT_RFCOMM_UUID).toBe("8d53dc1d-1db7-4cd3-868b-8a527460aa84");
  });

  it("shares default pairing key and virtual-mic path", () => {
    expect(DEFAULT_PAIRING_KEY).toBe("AirInput-Default-Pairing-Key-v1!!");
    expect(MACOS_VIRTUAL_MIC_DEST).toContain("AirInputMic.driver");
  });
});
