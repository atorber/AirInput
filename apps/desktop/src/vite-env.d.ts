/// <reference types="vite/client" />

interface AirInputApi {
  getStatus: () => Promise<Record<string, unknown>>;
  start: () => Promise<Record<string, unknown>>;
  stop: () => Promise<Record<string, unknown>>;
  installVirtualMic: () => Promise<Record<string, unknown>>;
  detectVirtualMic: () => Promise<Record<string, unknown>>;
  onStatus: (cb: (status: unknown) => void) => () => void;
}

interface Window {
  airinput?: AirInputApi;
}

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<object, object, unknown>;
  export default component;
}
