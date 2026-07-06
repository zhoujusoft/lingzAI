<template>
    <div class="agent-chat-page flex h-screen w-screen flex-col overflow-hidden">
        <div class="fixed inset-0 overflow-hidden pointer-events-none">
            <div
                class="absolute -top-20 -right-20 w-64 h-64 bg-violet-100/40 rounded-full blur-3xl"
            ></div>
            <div
                class="absolute -bottom-20 -left-20 w-64 h-64 bg-indigo-100/40 rounded-full blur-3xl"
            ></div>
        </div>

        <FrontAgentChatHeader
            :insight-open="showInsightPanel"
            @toggle-insight="toggleInsightPanel"
        />

        <div class="relative flex flex-1 overflow-hidden p-4">
            <!-- Left Area (Spacer) -->
            <div
                class="transition-all duration-500 cubic-bezier(0.4, 0, 0.2, 1)"
                :style="{ flex: !showFlowRail ? '1 1 0%' : '0 0 0%', minWidth: 0 }"
            ></div>

            <!-- Chat Workspace -->
            <div
                class="chat-workspace-container relative flex min-w-0 flex-none flex-col overflow-hidden rounded-2xl transition-all duration-500 cubic-bezier(0.4, 0, 0.2, 1)"
                :style="{
                    width: chatWidth,
                    marginLeft: showFlowRail ? '0' : '2rem',
                    marginRight: showFlowRail ? '0' : '2rem',
                }"
            >
                <div
                    v-if="runtimeBadge"
                    class="pointer-events-none absolute right-4 top-4 z-10 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold shadow-sm backdrop-blur"
                    :class="runtimeBadge.className"
                >
                    <span class="h-2 w-2 rounded-full" :class="runtimeBadge.dotClass"></span>
                    <span>{{ runtimeBadge.label }}</span>
                </div>
                <FrontAgentChatWorkspace
                    ref="agentChatWorkspaceRef"
                    :adapter="generalChatV2Adapter"
                    :session-storage-key="GENERAL_CHAT_V2_SESSION_STORAGE_KEY"
                    :request-options="requestOptions"
                    @unauthorized="handleUnauthorized"
                    @request-finished="handleRequestFinished"
                    @runtime-phase-change="handleRuntimePhaseChange"
                    @runtime-engine-change="handleRuntimeEngineChange"
                    @render-payload="handleRenderPayload"
                    @artifact-preview="handleArtifactPreview"
                />
            </div>

            <div v-if="runtimePanelVisible" class="pointer-events-none absolute left-4 top-4 z-20">
                <button
                    type="button"
                    class="pointer-events-auto inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white/95 px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm backdrop-blur transition hover:border-slate-300 hover:bg-white"
                    @click="showRuntimePanel = !showRuntimePanel"
                >
                    <span class="material-symbols-outlined text-[16px] text-slate-500"
                        >monitoring</span
                    >
                    <span>{{ runtimePanelToggleLabel }}</span>
                </button>
                <Transition name="panel-fade">
                    <div
                        v-if="showRuntimePanel"
                        class="pointer-events-auto mt-3 max-h-[calc(100vh-8rem)] overflow-auto"
                    >
                        <FrontAgentRuntimePanel :payload="runtimePanelPayload" />
                    </div>
                </Transition>
            </div>

            <!-- Right Area (Spacer + Flow Rail) -->
            <div
                class="flex min-w-0 transition-all duration-500 cubic-bezier(0.4, 0, 0.2, 1)"
                :style="{ flex: '1 1 0%', minWidth: 0 }"
            >
                <Transition name="flow-rail-slide">
                    <div
                        v-if="showFlowRail"
                        class="flex min-w-0 flex-1 gap-4"
                        :class="{ 'justify-end': !showPreviewPanel }"
                    >
                        <Transition name="panel-fade">
                            <PreviewTabContainer
                                v-if="showPreviewPanel"
                                class="preview-panel-container flex min-w-0 flex-1 flex-col overflow-hidden rounded-2xl"
                                :render-payload="renderPayload"
                                :session-code="sessionCode"
                                @close="closePreview"
                            />
                        </Transition>

                        <Transition name="panel-fade">
                            <FrontAgentInsightPanel
                                v-if="showInsightPanel"
                                class="insight-panel-container min-w-0 flex-none overflow-hidden rounded-2xl"
                                :style="{ width: insightWidth }"
                                @close="showInsightPanel = false"
                                @select-insight-item="handleInsightSelect"
                            />
                        </Transition>
                    </div>
                </Transition>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, provide, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import FrontAgentChatHeader from '../components/agent-chat/FrontAgentChatHeader.vue';
import FrontAgentInsightPanel from '../components/agent-chat/FrontAgentInsightPanel.vue';
import FrontAgentRuntimePanel from '../components/agent-chat/FrontAgentRuntimePanel.vue';
import FrontAgentChatWorkspace from '../components/agent-chat/FrontAgentChatWorkspace.vue';
import PreviewTabContainer from '../components/agent-chat/PreviewTabContainer.vue';
import { generalChatV2Adapter } from '../components/front-chat/adapters/generalChatV2Adapter';
import { GENERAL_CHAT_V2_SESSION_STORAGE_KEY } from '@/model/session';

const emit = defineEmits(['unauthorized']);
const route = useRoute();

const agentChatWorkspaceRef = ref(null);
const showPreview = ref(false);
const showInsightPanel = ref(false);
const renderPayload = ref(null);
const runtimePhase = ref('');
const runtimeMode = ref('');
const runtimeSubStage = ref('');
const runtimeSubStageLabel = ref('');
const runtimeProgressMessage = ref('');
const runtimeIterationCount = ref(0);
const runtimeLlmCallCount = ref(0);
const runtimeToolCallCount = ref(0);
const runtimeDecisionRepairCount = ref(0);
const runtimeFinishReason = ref('');
const runtimeRequestId = ref('');
const runtimeAt = ref('');
const runtimeEnginePayload = ref(null);
const showRuntimePanel = ref(false);
const viewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth);
const pendingSkillSelect = ref(null);

provide('pendingSkillSelect', pendingSkillSelect);

function updateViewportWidth() {
    viewportWidth.value = window.innerWidth;
}

onMounted(() => {
    updateViewportWidth();
    window.addEventListener('resize', updateViewportWidth);
});

onUnmounted(() => {
    window.removeEventListener('resize', updateViewportWidth);
});

const layoutConfig = computed(() => {
    const width = viewportWidth.value;

    // 提升会话区域占比：大屏下提升至 860px，中屏也相应增加比例
    let chatWidthPx;
    if (width < 1024) {
        chatWidthPx = Math.max(540, Math.round(width * 0.55));
    } else if (width < 1440) {
        chatWidthPx = Math.max(720, Math.round(width * 0.5));
    } else {
        chatWidthPx = 860;
    }

    let insightWidth = '340px';
    if (width < 1024) {
        insightWidth = '300px';
    } else if (width < 1280) {
        insightWidth = '320px';
    } else if (width < 1440) {
        insightWidth = '340px';
    }

    return {
        chatWidth: `${chatWidthPx}px`,
        insightWidth,
    };
});

const chatWidth = computed(() => layoutConfig.value.chatWidth);
const insightWidth = computed(() => layoutConfig.value.insightWidth);

const sessionCode = computed(() => {
    // Access sessionId from the nested workspace ref
    return agentChatWorkspaceRef.value?.sessionId || '';
});

const showPreviewPanelManual = ref(false);

// 预览面板显示条件：只有出现产物/预览内容时才展示，文件列表只是面板内附带 tab
const showPreviewPanel = computed(() => {
    return showPreviewPanelManual.value && showPreview.value;
});

// 监听新的预览内容，自动打开面板
watch(
    showPreview,
    (newPreview, oldPreview) => {
        const gotNewPreview = newPreview && !oldPreview;
        if (gotNewPreview) {
            showPreviewPanelManual.value = true;
        }
    },
    { immediate: true }
);

const showFlowRail = computed(() => showPreviewPanel.value || showInsightPanel.value);
const runtimePanelVisible = computed(
    () =>
        Boolean(runtimePhase.value) ||
        Boolean(runtimeMode.value) ||
        Boolean(runtimeEnginePayload.value) ||
        runtimeLlmCallCount.value > 0 ||
        runtimeToolCallCount.value > 0 ||
        runtimeDecisionRepairCount.value > 0
);
const runtimePanelPayload = computed(() => {
    if (!runtimePanelVisible.value) {
        return null;
    }
    return {
        phase: runtimePhase.value,
        mode: runtimeMode.value,
        subStage: runtimeSubStage.value,
        subStageLabel: runtimeSubStageLabel.value,
        progressMessage: runtimeProgressMessage.value,
        iterationCount: runtimeIterationCount.value,
        llmCallCount: runtimeLlmCallCount.value,
        toolCallCount: runtimeToolCallCount.value,
        decisionRepairCount: runtimeDecisionRepairCount.value,
        finishReason: runtimeFinishReason.value,
        requestId: runtimeRequestId.value,
        at: runtimeAt.value,
        graph: runtimeEnginePayload.value,
    };
});
const runtimePanelToggleLabel = computed(() =>
    showRuntimePanel.value ? '收起运行态' : '查看运行态'
);
const runtimeBadge = computed(() => {
    const phase = String(runtimePhase.value || '')
        .trim()
        .toUpperCase();
    if (!phase) {
        return null;
    }
    const mode = String(runtimeMode.value || '')
        .trim()
        .toUpperCase();
    const modeLabel =
        mode === 'REACT' ? 'ReAct' : mode === 'DIRECT' ? 'Direct' : mode === 'CODE' ? 'Code' : 'V2';
    const phaseLabelMap = {
        TRIAGE: '请求分流',
        REASONING: '模型推理',
        ACTION: '执行工具',
        OBSERVATION: '整理观察',
        FINAL_CANDIDATE: '请求结束',
        COMPLETION_CHECK: '检查闭环',
        FINAL_STREAMING: '流式输出',
        FINALIZING: '整理输出',
        COMPLETED: '已完成',
        FAILED: '执行失败',
    };
    const label = `${modeLabel} · ${phaseLabelMap[phase] || phase}`;
    if (phase === 'FAILED') {
        return {
            label,
            className: 'border-rose-200 bg-rose-50/92 text-rose-700',
            dotClass: 'bg-rose-500',
        };
    }
    if (phase === 'COMPLETED') {
        return {
            label,
            className: 'border-emerald-200 bg-emerald-50/92 text-emerald-700',
            dotClass: 'bg-emerald-500',
        };
    }
    return {
        label,
        className: 'border-sky-200 bg-white/88 text-slate-700',
        dotClass: 'bg-sky-500',
    };
});
const requestOptions = computed(() => {
    const runtimeV2Engine = String(route.query.runtimeV2Engine || '')
        .trim()
        .toLowerCase();
    if (!runtimeV2Engine) {
        return {};
    }
    return {
        runtimeV2Engine,
    };
});

watch(runtimePanelVisible, visible => {
    if (!visible) {
        showRuntimePanel.value = false;
    }
});

function handleUnauthorized() {
    emit('unauthorized');
}

function handleRequestFinished(payload) {
    if (payload?.success === false) {
        runtimePhase.value = 'FAILED';
        if (!runtimeFinishReason.value) {
            runtimeFinishReason.value = 'FAILED';
        }
        return;
    }
    if (!runtimePhase.value) {
        runtimePhase.value = 'COMPLETED';
    }
}

function handleRuntimePhaseChange(payload) {
    if (!payload) {
        runtimePhase.value = '';
        runtimeMode.value = '';
        runtimeSubStage.value = '';
        runtimeSubStageLabel.value = '';
        runtimeProgressMessage.value = '';
        runtimeIterationCount.value = 0;
        runtimeLlmCallCount.value = 0;
        runtimeToolCallCount.value = 0;
        runtimeDecisionRepairCount.value = 0;
        runtimeFinishReason.value = '';
        runtimeRequestId.value = '';
        runtimeAt.value = '';
        return;
    }
    runtimePhase.value = String(payload.phase || '')
        .trim()
        .toUpperCase();
    runtimeMode.value = String(payload.mode || '')
        .trim()
        .toUpperCase();
    runtimeSubStage.value = String(payload.subStage || '')
        .trim()
        .toUpperCase();
    runtimeSubStageLabel.value = String(payload.subStageLabel || '').trim();
    runtimeProgressMessage.value = String(payload.progressMessage || '').trim();
    runtimeIterationCount.value = Number(payload.iterationCount || 0);
    runtimeLlmCallCount.value = Number(payload.llmCallCount || 0);
    runtimeToolCallCount.value = Number(payload.toolCallCount || 0);
    runtimeDecisionRepairCount.value = Number(payload.decisionRepairCount || 0);
    runtimeFinishReason.value = String(payload.finishReason || '')
        .trim()
        .toUpperCase();
    runtimeRequestId.value = String(payload.requestId || '').trim();
    runtimeAt.value = String(payload.at || '').trim();
}

function handleRuntimeEngineChange(payload) {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
        runtimeEnginePayload.value = null;
        return;
    }
    runtimeEnginePayload.value = payload;
}

function toggleInsightPanel() {
    showInsightPanel.value = !showInsightPanel.value;
}

function handleInsightSelect(payload) {
    if (!payload?.skill?.id) {
        return;
    }
    agentChatWorkspaceRef.value?.appendInsightMessage?.(payload);
}

function handleRenderPayload(payload) {
    if (!payload) {
        renderPayload.value = null;
        showPreview.value = false;
        return;
    }
    renderPayload.value = payload;
    showPreview.value = true;
}

function handleArtifactPreview(segment) {
    if (!segment) {
        renderPayload.value = null;
        showPreview.value = false;
        return;
    }
    renderPayload.value = {
        type: 'artifact_preview',
        title: inferPreviewTitle(segment.fileName || segment.title),
        previewUrl: segment.previewUrl,
        downloadUrl: segment.downloadUrl,
        fileName: segment.fileName,
    };
    showPreview.value = true;
}

function inferPreviewTitle(fileName) {
    const titleMap = {
        'customer-opportunity-overview.html': '客户商机总览',
        'opportunity-detail-contacts.html': '商机详情与关系人',
        'opportunity-followup-recommendation.html': '商机跟进与建议',
    };
    const normalized = String(fileName || '')
        .trim()
        .toLowerCase();
    if (titleMap[normalized]) {
        return titleMap[normalized];
    }
    return fileName.replace(/\.html?$/i, '') || 'HTML 产物预览';
}

function closePreview() {
    showPreview.value = false;
    showPreviewPanelManual.value = false;
}
</script>

<style scoped>
.agent-chat-page {
    background: linear-gradient(135deg, #fafbfc 0%, #f3f4f6 50%, #f5f3ff 100%);
}

.chat-workspace-container,
.preview-panel-container,
.insight-panel-container {
    background: rgba(255, 255, 255, 0.98);
    border: none;
}

.flow-rail-slide-enter-active {
    transition:
        opacity 0.25s ease,
        transform 0.25s ease;
}

.flow-rail-slide-leave-active {
    transition:
        opacity 0.2s ease,
        flex 0.2s ease;
    overflow: hidden;
}

.flow-rail-slide-enter-from {
    opacity: 0;
    transform: translateX(12px);
}

.flow-rail-slide-leave-to {
    opacity: 0;
    flex: 0 0 0;
    min-width: 0;
    gap: 0;
    padding: 0;
}

.panel-fade-enter-active,
.panel-fade-leave-active {
    transition: opacity 0.2s ease;
}

.panel-fade-enter-from,
.panel-fade-leave-to {
    opacity: 0;
}
</style>
