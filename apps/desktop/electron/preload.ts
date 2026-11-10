import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("airinput", {
  getStatus: () => ipcRenderer.invoke("airinput:getStatus"),
  start: () => ipcRenderer.invoke("airinput:start"),
  stop: () => ipcRenderer.invoke("airinput:stop"),
  installVirtualMic: () => ipcRenderer.invoke("airinput:installVirtualMic"),
  detectVirtualMic: () => ipcRenderer.invoke("airinput:detectVirtualMic"),
  onStatus: (cb: (status: unknown) => void) => {
    const listener = (_: unknown, status: unknown) => cb(status);
    ipcRenderer.on("airinput:status", listener);
    return () => ipcRenderer.removeListener("airinput:status", listener);
  },
});
