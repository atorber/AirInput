<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useStatusStore } from "./stores/status";

const store = useStatusStore();
const busy = ref(false);
let unsubscribe: (() => void) | undefined;

const virtualMicLabel = computed(() => {
  switch (store.virtualMic) {
    case "installed":
      return "已安装且可加载";
    case "installed-blocked-signature":
      return "已安装，但签名不足（系统不显示）";
    case "built-not-installed":
      return "已编译，未安装";
    case "missing-build":
      return "缺少构建产物";
    case "unsupported":
      return "平台不支持";
    default:
      return store.virtualMic;
  }
});

const liveTone = computed(() => {
  if (store.authenticated || store.rfcommState === "streaming") return { label: "链路就绪", color: "var(--ok)" };
  if (store.running && store.rfcommState === "listening") return { label: "等待手机 App 连接", color: "var(--teal)" };
  if (store.running && (store.rfcommState === "connected" || store.rfcommState === "authenticated")) {
    return { label: "手机已连上", color: "var(--ok)" };
  }
  if (store.running) return { label: "服务运行中", color: "var(--warn)" };
  return { label: "已停止", color: "var(--mute)" };
});

const phoneLabel = computed(() => {
  if (store.phoneName) return store.phoneName;
  if (store.running && store.rfcommState === "listening") return "未连接（请在手机 App 点连接）";
  if (!store.running) return "服务未开启";
  return "未连接";
});

const authLabel = computed(() => {
  if (store.authenticated) return "已通过";
  if (store.rfcommState === "listening") return "未认证（等手机连上后自动 Challenge）";
  if (store.rfcommState === "connected") return "认证中…";
  return "未认证";
});

const needsMicInstall = computed(
  () =>
    store.virtualMic === "built-not-installed" ||
    store.virtualMic === "missing-build",
);

const showMicSignatureWarning = computed(
  () => store.virtualMic === "installed-blocked-signature",
);

onMounted(async () => {
  await store.refresh();
  unsubscribe = window.airinput?.onStatus((status) => {
    store.apply(status as Record<string, unknown>);
  });
});

onUnmounted(() => {
  unsubscribe?.();
});

async function start() {
  busy.value = true;
  try {
    await store.start();
  } finally {
    busy.value = false;
  }
}

async function stop() {
  busy.value = true;
  try {
    await store.stop();
  } finally {
    busy.value = false;
  }
}

async function installMic() {
  busy.value = true;
  try {
    await store.installVirtualMic();
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <main>
    <header class="hero">
      <div>
        <h1>AirInput</h1>
        <p class="lede">把 Android 手机变成电脑的蓝牙键鼠与无线麦克风。本地蓝牙通路，不依赖互联网。</p>
      </div>
      <div class="pill" :style="{ '--pill': liveTone.color }">
        <span class="dot" />
        {{ liveTone.label }}
      </div>
    </header>

    <section class="panel">
      <h2>服务</h2>
      <p class="hint">
        系统蓝牙「已连接」不等于 AirInput 已连通。需：桌面开启服务 → 手机 App 选择本机 → 点连接 → 出现认证通过。
      </p>
      <div class="actions">
        <button :disabled="busy || store.running" @click="start">开启服务</button>
        <button class="secondary" :disabled="busy || !store.running" @click="stop">关闭服务</button>
      </div>
    </section>

    <section v-if="needsMicInstall" class="panel">
      <h2>虚拟麦克风</h2>
      <div class="setup-row">
        <div class="setup-copy">
          <strong>尚未装入系统 HAL 目录</strong>
          <p class="hint" style="margin: 0">
            当前：{{ virtualMicLabel }}。安装会请求管理员密码，拷贝驱动并重启 coreaudiod。
          </p>
        </div>
        <button class="secondary" :disabled="busy" @click="installMic">安装虚拟麦克风</button>
      </div>
    </section>

    <section v-if="showMicSignatureWarning" class="panel">
      <h2>虚拟麦克风</h2>
      <strong>驱动已安装，但系统输入列表不会出现</strong>
      <p class="hint">
        本机没有 Apple Developer ID 证书，当前为 ad-hoc 签名；新版 macOS 会拒绝加载到「声音 → 输入」。
        要出现 AirInput Microphone，需用 Developer ID Application 签名（并建议公证）。
      </p>
      <p class="hint" style="margin-bottom: 0">
        临时验听：手机开麦后，解码音频会写入
        <code style="color: var(--teal)">/tmp/airinput-capture.wav</code>
        ，停麦后用 QuickTime 打开即可。
      </p>
    </section>

    <section class="panel">
      <h2>实时状态</h2>
      <div class="metrics">
        <div class="metric">
          <div class="k">蓝牙</div>
          <div class="v">{{ store.rfcommState || "—" }}</div>
        </div>
        <div class="metric">
          <div class="k">手机</div>
          <div class="v">{{ phoneLabel }}</div>
        </div>
        <div class="metric">
          <div class="k">认证</div>
          <div class="v">{{ authLabel }}</div>
        </div>
        <div class="metric">
          <div class="k">收包</div>
          <div class="v mono">{{ store.packets }}</div>
        </div>
        <div class="metric">
          <div class="k">解码错误</div>
          <div class="v mono">{{ store.decodeErrors }}</div>
        </div>
        <div class="metric">
          <div class="k">虚拟麦</div>
          <div class="v">{{ virtualMicLabel }}</div>
        </div>
        <div class="metric">
          <div class="k">采样</div>
          <div class="v mono">{{ store.sampleRate / 1000 }} kHz</div>
        </div>
        <div class="metric">
          <div class="k">码率</div>
          <div class="v mono">{{ Math.round(store.bitrate / 1000) }} kbps</div>
        </div>
        <div class="metric">
          <div class="k">Helper</div>
          <div class="v">{{ store.helperConnected ? "在线" : "离线" }}</div>
        </div>
      </div>
    </section>

    <section class="panel">
      <h2>详情</h2>
      <p class="detail">{{ store.detail || "等待事件…" }}</p>
    </section>
  </main>
</template>
