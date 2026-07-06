<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
    renderPayload: {
        type: Object,
        default: () => null,
    },
});

const emit = defineEmits(['close']);

const iframeLoading = ref(true);

const previewTitle = computed(() => {
    const text = String(props.renderPayload?.title || '').trim();
    return text || '预览';
});

const isArtifactPreview = computed(() => {
    return props.renderPayload?.type === 'artifact_preview' && props.renderPayload?.previewUrl;
});

function onIframeLoad() {
    iframeLoading.value = false;
}

function onIframeError() {
    iframeLoading.value = false;
}
</script>

<template>
    <aside class="preview-container flex h-full flex-col overflow-hidden">
        <!-- Content Area -->
        <div class="flex-1 overflow-y-auto custom-scrollbar">
            <div v-if="renderPayload" class="h-full">
                <!-- Artifact preview with iframe -->
                <div v-if="isArtifactPreview" class="preview-iframe-wrapper">
                    <!-- Loading skeleton -->
                    <div v-if="iframeLoading" class="preview-iframe-skeleton">
                        <div class="skeleton-line w-3/4"></div>
                        <div class="skeleton-line w-1/2"></div>
                        <div class="skeleton-line w-2/3"></div>
                        <div class="skeleton-line w-1/3"></div>
                        <div class="skeleton-line w-3/5"></div>
                        <div class="skeleton-line w-2/5"></div>
                    </div>
                    <iframe
                        :src="renderPayload.previewUrl"
                        class="preview-iframe"
                        :class="{ 'iframe-loaded': !iframeLoading }"
                        sandbox="allow-scripts allow-same-origin"
                        @load="onIframeLoad"
                        @error="onIframeError"
                    ></iframe>
                </div>

                <!-- Fallback: JSON display -->
                <div v-else class="preview-json-card">
                    <div class="flex items-center gap-2 mb-3">
                        <span class="material-symbols-outlined text-[16px] text-slate-400"
                            >code</span
                        >
                        <span
                            class="text-[12px] font-semibold text-slate-400 uppercase tracking-wider"
                            >Raw Data</span
                        >
                    </div>
                    <pre class="text-[12px] leading-relaxed text-slate-600 overflow-auto">{{
                        JSON.stringify(renderPayload, null, 2)
                    }}</pre>
                </div>
            </div>

            <!-- Empty State -->
            <div v-else class="preview-empty">
                <div class="preview-empty-icon">
                    <span class="material-symbols-outlined text-[36px] text-slate-300"
                        >preview</span
                    >
                </div>
                <p class="text-[14px] font-semibold text-slate-500">暂无预览内容</p>
                <p class="mt-1.5 text-[13px] leading-6 text-slate-400">
                    发送客户或商机查询后<br />右侧会展示对应模板结果
                </p>
            </div>
        </div>
    </aside>
</template>

<style scoped>
.preview-container {
    background: #f8fafc;
}

/* ── Iframe Preview ── */
.preview-iframe-wrapper {
    position: relative;
    height: 100%;
    min-height: 400px;
    border-radius: 8px;
    overflow: hidden;
    background: #ffffff;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.preview-iframe {
    width: 100%;
    height: 100%;
    min-height: 500px;
    border: none;
    opacity: 0;
    transition: opacity 0.3s ease;
}

.preview-iframe.iframe-loaded {
    opacity: 1;
}

/* ── Loading Skeleton ── */
.preview-iframe-skeleton {
    position: absolute;
    inset: 0;
    z-index: 2;
    padding: 28px 24px;
    background: rgba(255, 255, 255, 0.8);
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.skeleton-line {
    height: 12px;
    border-radius: 6px;
    background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
    background-size: 200% 100%;
    animation: skeleton-shimmer 1.5s ease-in-out infinite;
}

.skeleton-line:nth-child(1) {
    width: 75%;
}
.skeleton-line:nth-child(2) {
    width: 50%;
}
.skeleton-line:nth-child(3) {
    width: 66%;
}
.skeleton-line:nth-child(4) {
    width: 33%;
}
.skeleton-line:nth-child(5) {
    width: 60%;
}
.skeleton-line:nth-child(6) {
    width: 40%;
}

@keyframes skeleton-shimmer {
    0% {
        background-position: 200% 0;
    }
    100% {
        background-position: -200% 0;
    }
}

/* ── JSON Card ── */
.preview-json-card {
    border-radius: 8px;
    background: #ffffff;
    padding: 20px;
}

/* ── Empty State ── */
.preview-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 320px;
    height: 100%;
    text-align: center;
    border-radius: 8px;
    background: #ffffff;
    padding: 40px 24px;
}

.preview-empty-icon {
    width: 72px;
    height: 72px;
    border-radius: 8px;
    background: #f1f5f9;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 16px;
}

/* ── Reduced Motion ── */
@media (prefers-reduced-motion: reduce) {
    .skeleton-line {
        animation: none;
        background: #e2e8f0;
    }

    .preview-iframe {
        transition: none;
        opacity: 1;
    }
}
</style>
