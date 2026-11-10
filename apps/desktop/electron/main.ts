import { app, BrowserWindow, ipcMain } from "electron";
import {
  spawn,
  spawnSync,
  execFile,
  type ChildProcessWithoutNullStreams,
} from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
// Keep constants local: Electron main is CommonJS and cannot require the
// workspace ESM TypeScript package directly at runtime.
const AIRINPUT_RFCOMM_UUID = "8d53dc1d-1db7-4cd3-868b-8a527460aa84";
const AUDIO_SAMPLE_RATE = 48_000;
const MACOS_VIRTUAL_MIC_DEST = "/Library/Audio/Plug-Ins/HAL/AirInputMic.driver";
const VIRTUAL_MIC_NAME = "AirInput Microphone";

const execFileAsync = promisify(execFile);

let mainWindow: BrowserWindow | null = null;
let helper: ChildProcessWithoutNullStreams | null = null;

type ServiceStatus = {
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

const status: ServiceStatus = {
  running: false,
  helperConnected: false,
  rfcommState: "stopped",
  phoneName: "",
  packets: 0,
  decodeErrors: 0,
  sampleRate: AUDIO_SAMPLE_RATE,
  bitrate: 32_000,
  virtualMic: "unknown",
  authenticated: false,
  detail: `RFCOMM UUID ${AIRINPUT_RFCOMM_UUID}`,
};

function repoRoot(): string {
  // electron/main.js → apps/desktop → repo root
  return path.resolve(__dirname, "../../..");
}

function driverBuildPath(): string {
  return path.join(repoRoot(), "native/macos/virtual-mic/build/AirInputMic.driver");
}

function isAdhocSigned(driverPath: string): boolean {
  const r = spawnSync("codesign", ["-dv", "--verbose=2", driverPath], { encoding: "utf8" });
  const out = `${r.stdout ?? ""}\n${r.stderr ?? ""}`;
  return /Signature=adhoc/i.test(out) || /flags=0x2\(adhoc\)/i.test(out);
}

function detectVirtualMic(): string {
  if (process.platform !== "darwin") return "unsupported";
  if (fs.existsSync(MACOS_VIRTUAL_MIC_DEST)) {
    // Modern macOS will not load ad-hoc / unsigned AudioServerPlugIn into inputs.
    return isAdhocSigned(MACOS_VIRTUAL_MIC_DEST)
      ? "installed-blocked-signature"
      : "installed";
  }
  if (fs.existsSync(driverBuildPath())) return "built-not-installed";
  return "missing-build";
}

function refreshVirtualMicStatus() {
  status.virtualMic = detectVirtualMic();
}

function pushStatus() {
  mainWindow?.webContents.send("airinput:status", { ...status });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 960,
    height: 720,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  const devUrl = process.env.VITE_DEV_SERVER_URL;
  if (devUrl) {
    void mainWindow.loadURL(devUrl);
  } else {
    void mainWindow.loadFile(path.join(__dirname, "../dist/index.html"));
  }
}

function helperPath(): string {
  if (process.platform === "darwin") {
    return path.join(
      repoRoot(),
      "native/macos/rfcomm-server/.build/release/AirInputRfcommServer",
    );
  }
  if (process.platform === "win32") {
    return path.join(
      repoRoot(),
      "native/windows/rfcomm-server/build/Release/airinput_rfcomm_server.exe",
    );
  }
  return "";
}

function startHelper() {
  if (helper) return;
  refreshVirtualMicStatus();
  const bin = helperPath();
  if (!bin || !fs.existsSync(bin)) {
    status.detail = `helper missing: ${bin || "(unsupported platform)"}`;
    pushStatus();
    return;
  }
  try {
    helper = spawn(bin, ["--uuid", AIRINPUT_RFCOMM_UUID], {
      stdio: ["pipe", "pipe", "pipe"],
    });
    status.running = true;
    status.helperConnected = true;
    status.rfcommState = "listening";
    status.detail = `helper started: ${bin}`;
    helper.stdout.on("data", (buf: Buffer) => {
      const line = buf.toString("utf8").trim();
      for (const part of line.split("\n")) {
        if (!part.startsWith("{")) continue;
        try {
          const msg = JSON.parse(part) as Record<string, unknown>;
          if (typeof msg.packets === "number") status.packets = msg.packets;
          if (typeof msg.state === "string") status.rfcommState = msg.state;
          if (typeof msg.phoneName === "string") status.phoneName = msg.phoneName;
          if (typeof msg.decodeErrors === "number") status.decodeErrors = msg.decodeErrors;
          if (typeof msg.authenticated === "boolean") status.authenticated = msg.authenticated;
        } catch {
          // ignore non-json
        }
      }
      pushStatus();
    });
    helper.stderr.on("data", (buf: Buffer) => {
      status.detail = buf.toString("utf8").slice(0, 500);
      pushStatus();
    });
    helper.on("exit", (code) => {
      status.running = false;
      status.helperConnected = false;
      status.rfcommState = "stopped";
      status.authenticated = false;
      status.detail = `helper exited code=${code}`;
      helper = null;
      pushStatus();
    });
  } catch (err) {
    status.running = false;
    status.detail = err instanceof Error ? err.message : String(err);
    pushStatus();
  }
}

function stopHelper() {
  if (!helper) {
    status.running = false;
    status.rfcommState = "stopped";
    return;
  }
  helper.stdin.write('{"cmd":"stop"}\n');
  helper.kill("SIGTERM");
  helper = null;
  status.running = false;
  status.helperConnected = false;
  status.rfcommState = "stopped";
  status.authenticated = false;
}

async function installVirtualMic(): Promise<ServiceStatus> {
  refreshVirtualMicStatus();
  if (process.platform !== "darwin") {
    status.detail = "虚拟麦安装仅支持 macOS";
    return { ...status };
  }
  const src = driverBuildPath();
  if (!fs.existsSync(src)) {
    status.virtualMic = "missing-build";
    status.detail =
      "缺少 build/AirInputMic.driver，请先: cd native/macos/virtual-mic && cmake --build build";
    return { ...status };
  }

  // Ad-hoc sign before copy (helps some macOS versions; Developer ID still recommended).
  try {
    await execFileAsync("codesign", ["--force", "--deep", "--sign", "-", src]);
  } catch (err) {
    status.detail = `codesign warning: ${err instanceof Error ? err.message : String(err)}`;
  }

  // osascript 提权进程通常读不了 ~/Documents（TCC Operation not permitted）。
  // 先由当前用户进程把 .driver 暂存到 /tmp，再让管理员脚本从 /tmp 安装。
  const stageRoot = path.join(os.tmpdir(), `airinput-vmic-stage-${process.pid}`);
  const stageDriver = path.join(stageRoot, "AirInputMic.driver");
  const dest = MACOS_VIRTUAL_MIC_DEST;
  const scriptPath = path.join(os.tmpdir(), `airinput-install-vmic-${process.pid}.sh`);

  try {
    fs.rmSync(stageRoot, { recursive: true, force: true });
    fs.mkdirSync(stageRoot, { recursive: true });
    await execFileAsync("ditto", [src, stageDriver]);
  } catch (err) {
    status.detail = `暂存驱动失败：${err instanceof Error ? err.message : String(err)}`;
    return { ...status };
  }

  const script = `#!/bin/bash
set -euo pipefail
mkdir -p /Library/Audio/Plug-Ins/HAL
rm -rf ${JSON.stringify(dest)}
ditto ${JSON.stringify(stageDriver)} ${JSON.stringify(dest)}
chown -R root:wheel ${JSON.stringify(dest)}
killall coreaudiod 2>/dev/null || true
`;
  fs.writeFileSync(scriptPath, script, { mode: 0o755 });

  try {
    await execFileAsync("osascript", [
      "-e",
      `do shell script ${JSON.stringify(`/bin/bash ${scriptPath}`)} with administrator privileges`,
    ]);
    await new Promise((r) => setTimeout(r, 800));
    refreshVirtualMicStatus();
    status.detail =
      status.virtualMic === "installed"
        ? `已安装 ${VIRTUAL_MIC_NAME} 到 ${dest}。请在「系统设置 → 声音 → 输入」确认。`
        : `安装命令已执行，但未检测到 ${dest}`;
  } catch (err) {
    refreshVirtualMicStatus();
    status.detail = `安装失败或已取消：${err instanceof Error ? err.message : String(err)}`;
  } finally {
    try {
      fs.unlinkSync(scriptPath);
    } catch {
      // ignore
    }
    try {
      fs.rmSync(stageRoot, { recursive: true, force: true });
    } catch {
      // ignore
    }
  }
  return { ...status };
}

app.whenReady().then(() => {
  refreshVirtualMicStatus();
  createWindow();
  ipcMain.handle("airinput:getStatus", () => {
    refreshVirtualMicStatus();
    return { ...status };
  });
  ipcMain.handle("airinput:start", () => {
    startHelper();
    return { ...status };
  });
  ipcMain.handle("airinput:stop", () => {
    stopHelper();
    return { ...status };
  });
  ipcMain.handle("airinput:installVirtualMic", async () => installVirtualMic());
  ipcMain.handle("airinput:detectVirtualMic", () => {
    refreshVirtualMicStatus();
    return { ...status };
  });
});

app.on("window-all-closed", () => {
  stopHelper();
  if (process.platform !== "darwin") app.quit();
});
