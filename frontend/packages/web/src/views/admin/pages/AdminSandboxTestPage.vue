<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
    getSandboxTestInfo,
    navigateSandboxTest,
    openSandboxTestBaidu,
    snapshotSandboxTest,
    startSandboxTest,
    stopSandboxTest,
    takeSandboxTestScreenshot,
} from '@/api/sandbox-test';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();
const session = ref(null);
const targetUrl = ref('https://www.baidu.com');
const busy = ref(false);
const errorMessage = ref('');
const lastResult = ref('暂无工具调用结果');
const lastScreenshotPath = ref('');
const frameKey = ref(0);

const hasSession = computed(() => Boolean(session.value?.sessionId));
const canOperate = computed(() => hasSession.value && !busy.value);
const statusText = computed(() => {
    if (busy.value) return '处理中';
    if (errorMessage.value) return '异常';
    return session.value?.status || '未启动';
});
const statusClass = computed(() => {
    const status = String(statusText.value || '').toLowerCase();
    if (status.includes('run')) return 'is-running';
    if (status.includes('异常') || status.includes('error')) return 'is-error';
    if (status.includes('处理') || status.includes('start')) return 'is-pending';
    return '';
});
const desktopTitle = computed(() => {
    if (session.value?.desktopUrl) {
        return session.value.desktopUrl;
    }
    return '未建立 VNC 连接';
});
const resultText = computed(() => {
    if (errorMessage.value) return errorMessage.value;
    if (session.value?.lastToolResult) return session.value.lastToolResult;
    return lastResult.value;
});
const screenshotPathText = computed(
    () => session.value?.lastScreenshotPath || lastScreenshotPath.value || '-'
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function goBack() {
    router.push(ROUTE_PATHS.adminHome);
}

async function withBusy(action) {
    busy.value = true;
    errorMessage.value = '';
    try {
        await action();
    } catch (error) {
        errorMessage.value = error?.message || '操作失败';
    } finally {
        busy.value = false;
    }
}

async function ensureSession() {
    if (session.value?.sessionId) {
        return session.value;
    }
    const data = await startSandboxTest({}, handleUnauthorized);
    session.value = rememberResult(data);
    frameKey.value += 1;
    return data;
}

function rememberResult(data) {
    if (data?.lastToolResult) {
        lastResult.value = data.lastToolResult;
    }
    if (data?.lastScreenshotPath) {
        lastScreenshotPath.value = data.lastScreenshotPath;
    }
    return data;
}

function startSession() {
    return withBusy(async () => {
        const data = await startSandboxTest({}, handleUnauthorized);
        session.value = rememberResult(data);
        frameKey.value += 1;
    });
}

function openBaidu() {
    return withBusy(async () => {
        const current = await ensureSession();
        session.value = rememberResult(
            await openSandboxTestBaidu(current.sessionId, handleUnauthorized)
        );
    });
}

function navigate() {
    return withBusy(async () => {
        const current = await ensureSession();
        session.value = await navigateSandboxTest(
            current.sessionId,
            targetUrl.value,
            handleUnauthorized
        );
        rememberResult(session.value);
    });
}

function refreshInfo() {
    return withBusy(async () => {
        const current = await ensureSession();
        session.value = rememberResult(
            await getSandboxTestInfo(current.sessionId, handleUnauthorized)
        );
    });
}

function refreshFrame() {
    frameKey.value += 1;
}

function takeScreenshot() {
    return withBusy(async () => {
        const current = await ensureSession();
        session.value = rememberResult(
            await takeSandboxTestScreenshot(current.sessionId, handleUnauthorized)
        );
    });
}

function snapshot() {
    return withBusy(async () => {
        const current = await ensureSession();
        session.value = rememberResult(
            await snapshotSandboxTest(current.sessionId, handleUnauthorized)
        );
    });
}

function stopSession() {
    return withBusy(async () => {
        const current = await ensureSession();
        rememberResult(await stopSandboxTest(current.sessionId, handleUnauthorized));
        session.value = null;
        frameKey.value += 1;
    });
}
</script>

<template>
    <main class="sandbox-test-page">
        <aside class="control-panel">
            <section class="panel-header">
                <button class="back-button" type="button" @click="goBack">
                    <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
                </button>
                <div>
                    <p class="eyebrow">Docker GUI</p>
                    <h1>云电脑测试台</h1>
                </div>
            </section>

            <section class="status-strip" :class="statusClass">
                <span class="status-dot" aria-hidden="true"></span>
                <div>
                    <strong>{{ statusText }}</strong>
                    <small>{{ session?.containerName || '尚未创建容器' }}</small>
                </div>
            </section>

            <section class="action-group">
                <button
                    class="primary-action"
                    type="button"
                    :disabled="busy || hasSession"
                    @click="startSession"
                >
                    <span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>
                    <span>启动 Docker</span>
                </button>
                <button type="button" :disabled="!canOperate" @click="openBaidu">
                    <span class="material-symbols-outlined" aria-hidden="true">travel_explore</span>
                    <span>打开百度</span>
                </button>
            </section>

            <section class="url-box">
                <label for="sandbox-url">打开 URL</label>
                <div class="url-row">
                    <input
                        id="sandbox-url"
                        v-model.trim="targetUrl"
                        type="text"
                        placeholder="https://www.baidu.com"
                        @keyup.enter="navigate"
                    />
                    <button
                        class="icon-button"
                        type="button"
                        :disabled="!canOperate || !targetUrl"
                        @click="navigate"
                    >
                        <span class="material-symbols-outlined" aria-label="打开"
                            >arrow_forward</span
                        >
                    </button>
                </div>
            </section>

            <section class="action-grid">
                <button type="button" :disabled="!canOperate" @click="refreshInfo">
                    <span class="material-symbols-outlined" aria-hidden="true">sync</span>
                    <span>刷新状态</span>
                </button>
                <button type="button" :disabled="!canOperate" @click="refreshFrame">
                    <span class="material-symbols-outlined" aria-hidden="true">refresh</span>
                    <span>刷新画面</span>
                </button>
                <button type="button" :disabled="!canOperate" @click="takeScreenshot">
                    <span class="material-symbols-outlined" aria-hidden="true"
                        >screenshot_monitor</span
                    >
                    <span>截图</span>
                </button>
                <button type="button" :disabled="!canOperate" @click="snapshot">
                    <span class="material-symbols-outlined" aria-hidden="true">account_tree</span>
                    <span>页面快照</span>
                </button>
            </section>

            <button
                class="danger-action"
                type="button"
                :disabled="!canOperate"
                @click="stopSession"
            >
                <span class="material-symbols-outlined" aria-hidden="true">stop_circle</span>
                <span>停止容器</span>
            </button>

            <section class="meta-list">
                <div>
                    <span>端口</span>
                    <strong>{{ session?.hostPort || '-' }}</strong>
                </div>
                <div>
                    <span>镜像</span>
                    <strong>{{ session?.imageName || '-' }}</strong>
                </div>
                <div>
                    <span>Base URL</span>
                    <strong>{{ session?.baseUrl || '-' }}</strong>
                </div>
                <div>
                    <span>工作目录</span>
                    <strong>{{ session?.workspacePath || '-' }}</strong>
                </div>
                <div>
                    <span>最近截图</span>
                    <strong>{{ screenshotPathText }}</strong>
                </div>
            </section>

            <section class="result-box">
                <header>
                    <span class="material-symbols-outlined" aria-hidden="true">terminal</span>
                    <strong>最近结果</strong>
                </header>
                <pre>{{ resultText }}</pre>
            </section>
        </aside>

        <section class="desktop-panel">
            <header class="desktop-toolbar">
                <div>
                    <p>VNC 实时画面</p>
                    <strong>{{ desktopTitle }}</strong>
                </div>
                <a
                    v-if="session?.desktopUrl"
                    :href="session.desktopUrl"
                    target="_blank"
                    rel="noreferrer"
                >
                    <span class="material-symbols-outlined" aria-hidden="true">open_in_new</span>
                    <span>新窗口</span>
                </a>
            </header>

            <div class="desktop-frame">
                <iframe
                    v-if="session?.desktopUrl"
                    :key="frameKey"
                    :src="session.desktopUrl"
                    title="Docker GUI VNC"
                    allow="clipboard-read; clipboard-write"
                ></iframe>
                <div v-else class="empty-desktop">
                    <span class="material-symbols-outlined" aria-hidden="true"
                        >desktop_windows</span
                    >
                    <h2>等待启动 Docker 容器</h2>
                    <p>点击左侧启动后，这里会直接加载容器里的 VNC 桌面。</p>
                </div>
            </div>
        </section>
    </main>
</template>

<style scoped>
.sandbox-test-page {
    display: grid;
    grid-template-columns: minmax(320px, 376px) minmax(0, 1fr);
    min-height: 100vh;
    background:
        linear-gradient(135deg, rgba(25, 41, 64, 0.96), rgba(8, 13, 18, 0.98)),
        radial-gradient(circle at 75% 20%, rgba(85, 191, 171, 0.16), transparent 34%);
    color: #edf4f7;
}

.control-panel {
    display: flex;
    flex-direction: column;
    gap: 18px;
    min-height: 100vh;
    padding: 22px;
    border-right: 1px solid rgba(236, 244, 246, 0.15);
    background: rgba(11, 17, 23, 0.78);
    box-shadow: 18px 0 50px rgba(0, 0, 0, 0.28);
}

.panel-header {
    display: flex;
    align-items: center;
    gap: 14px;
}

.back-button,
.icon-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 42px;
    height: 42px;
    flex: 0 0 42px;
    padding: 0;
}

.eyebrow {
    margin: 0 0 4px;
    color: #8bd7cb;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0;
    text-transform: uppercase;
}

h1,
h2,
p {
    margin: 0;
}

h1 {
    color: #ffffff;
    font-size: 24px;
    line-height: 1.15;
}

button,
.desktop-toolbar a {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-height: 42px;
    border: 1px solid rgba(236, 244, 246, 0.16);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.08);
    color: #edf4f7;
    font: inherit;
    font-size: 14px;
    font-weight: 800;
    cursor: pointer;
    transition:
        border-color 0.16s ease,
        background 0.16s ease,
        transform 0.16s ease;
}

button:hover:not(:disabled),
.desktop-toolbar a:hover {
    border-color: rgba(139, 215, 203, 0.74);
    background: rgba(139, 215, 203, 0.14);
    transform: translateY(-1px);
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.48;
}

.material-symbols-outlined {
    font-size: 20px;
    line-height: 1;
}

.status-strip {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 12px;
    padding: 14px;
    border: 1px solid rgba(236, 244, 246, 0.14);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.07);
}

.status-dot {
    width: 11px;
    height: 11px;
    border-radius: 50%;
    background: #9ca8b3;
    box-shadow: 0 0 0 5px rgba(156, 168, 179, 0.14);
}

.status-strip strong,
.status-strip small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.status-strip small {
    margin-top: 4px;
    color: #a7b4bd;
}

.status-strip.is-running .status-dot {
    background: #64d49f;
    box-shadow: 0 0 0 5px rgba(100, 212, 159, 0.16);
}

.status-strip.is-pending .status-dot {
    background: #e7c56a;
    box-shadow: 0 0 0 5px rgba(231, 197, 106, 0.16);
}

.status-strip.is-error .status-dot {
    background: #ff7c73;
    box-shadow: 0 0 0 5px rgba(255, 124, 115, 0.16);
}

.action-group,
.action-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}

.primary-action {
    border-color: rgba(99, 213, 184, 0.72);
    background: linear-gradient(135deg, #45b89e, #287d8e);
    color: #041310;
}

.url-box {
    display: grid;
    gap: 8px;
}

.url-box label {
    color: #b9c7cf;
    font-size: 12px;
    font-weight: 800;
}

.url-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 42px;
    gap: 8px;
}

input {
    min-width: 0;
    height: 42px;
    padding: 0 12px;
    border: 1px solid rgba(236, 244, 246, 0.16);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.08);
    color: #ffffff;
    font: inherit;
    outline: none;
}

input:focus {
    border-color: rgba(139, 215, 203, 0.74);
}

input::placeholder {
    color: rgba(237, 244, 247, 0.45);
}

.danger-action {
    border-color: rgba(255, 124, 115, 0.38);
    color: #ffc8c4;
}

.meta-list {
    display: grid;
    gap: 9px;
    padding: 14px;
    border: 1px solid rgba(236, 244, 246, 0.14);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.06);
}

.meta-list div {
    display: grid;
    gap: 4px;
}

.meta-list span {
    color: #99aab3;
    font-size: 12px;
}

.meta-list strong {
    overflow-wrap: anywhere;
    color: #edf4f7;
    font-size: 12px;
    font-weight: 700;
}

.result-box {
    display: grid;
    min-height: 140px;
    overflow: hidden;
    border: 1px solid rgba(236, 244, 246, 0.14);
    border-radius: 8px;
    background: #061015;
}

.result-box header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 11px 12px;
    border-bottom: 1px solid rgba(236, 244, 246, 0.1);
    color: #dbe7ea;
}

.result-box pre {
    min-height: 94px;
    max-height: 240px;
    margin: 0;
    overflow: auto;
    padding: 12px;
    color: #a9d8cf;
    font-size: 12px;
    line-height: 1.55;
    white-space: pre-wrap;
}

.desktop-panel {
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    min-width: 0;
    min-height: 100vh;
    padding: 20px;
}

.desktop-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding: 0 2px 14px;
}

.desktop-toolbar p {
    color: #8bd7cb;
    font-size: 12px;
    font-weight: 800;
}

.desktop-toolbar strong {
    display: block;
    max-width: min(70vw, 900px);
    margin-top: 4px;
    overflow: hidden;
    color: #ffffff;
    font-size: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.desktop-toolbar a {
    min-width: 106px;
    padding: 0 14px;
    text-decoration: none;
}

.desktop-frame {
    position: relative;
    min-height: 0;
    overflow: hidden;
    border: 1px solid rgba(236, 244, 246, 0.18);
    border-radius: 8px;
    background: #02070b;
    box-shadow: 0 20px 80px rgba(0, 0, 0, 0.32);
}

.desktop-frame iframe {
    width: 100%;
    height: 100%;
    border: 0;
    background: #02070b;
}

.empty-desktop {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    min-height: 520px;
    padding: 28px;
    text-align: center;
}

.empty-desktop .material-symbols-outlined {
    margin-bottom: 16px;
    color: #8bd7cb;
    font-size: 64px;
}

.empty-desktop h2 {
    color: #ffffff;
    font-size: 24px;
}

.empty-desktop p {
    max-width: 420px;
    margin-top: 10px;
    color: #a8b7bf;
    line-height: 1.6;
}

@media (max-width: 900px) {
    .sandbox-test-page {
        grid-template-columns: 1fr;
    }

    .control-panel {
        min-height: auto;
        border-right: 0;
        border-bottom: 1px solid rgba(236, 244, 246, 0.15);
    }

    .desktop-panel {
        min-height: 620px;
    }
}

@media (max-width: 520px) {
    .action-group,
    .action-grid {
        grid-template-columns: 1fr;
    }

    .desktop-toolbar {
        align-items: flex-start;
        flex-direction: column;
    }
}
</style>
