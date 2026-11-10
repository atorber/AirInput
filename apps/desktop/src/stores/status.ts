import { defineStore } from "pinia";

export type DesktopStatus = {
  running: boolean;
  helperConnected: boolean;
  rfcommState: string;
  phoneName: string;
  packets: number;
  decodeErrors: number;
  sampleRate: number;
  bitrate: number;
  virtualMic: string;
  authenticated: boolean;
  detail: string;
};

export const useStatusStore = defineStore("status", {
  state: (): DesktopStatus => ({
    running: false,
    helperConnected: false,
    rfcommState: "stopped",
    phoneName: "",
    packets: 0,
    decodeErrors: 0,
    sampleRate: 48_000,
    bitrate: 32_000,
    virtualMic: "unknown",
    authenticated: false,
    detail: "等待启动",
  }),
  actions: {
    apply(partial: Record<string, unknown>) {
      Object.assign(this, partial);
    },
    async refresh() {
      if (!window.airinput) {
        this.detail = "非 Electron 预加载环境（Vite 浏览器预览）";
        return;
      }
      this.apply(await window.airinput.getStatus());
    },
    async start() {
      if (!window.airinput) return;
      this.apply(await window.airinput.start());
    },
    async stop() {
      if (!window.airinput) return;
      this.apply(await window.airinput.stop());
    },
    async installVirtualMic() {
      if (!window.airinput) return;
      this.apply(await window.airinput.installVirtualMic());
    },
  },
});
