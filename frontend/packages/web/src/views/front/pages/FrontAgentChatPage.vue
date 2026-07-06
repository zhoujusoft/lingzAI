<template>
    <div class="agent-chat-page flex h-screen w-screen flex-col overflow-hidden">
        <!-- 背景装饰（移除渐变光晕，保持简约） -->

        <FrontAgentChatHeader
            :insight-open="showInsightPanel"
            @toggle-insight="toggleInsightPanel"
        />

        <div
            class="relative flex flex-1 overflow-hidden min-h-0 p-4 lg:p-5"
            :style="pageContentStyle"
        >
            <!-- Left Area (Spacer) -->
            <div
                class="transition-all duration-500 cubic-bezier(0.4, 0, 0.2, 1)"
                :style="{ flex: shouldCenterChat ? '1 1 0%' : '0 0 0%', minWidth: 0 }"
            ></div>

            <!-- Chat Workspace -->
            <div
                class="chat-workspace-container h-full relative flex min-w-0 flex-none flex-shrink-0 flex-col overflow-hidden rounded-2xl transition-all duration-500 cubic-bezier(0.4, 0, 0.2, 1)"
                :style="{
                    width: chatWidth,
                    marginLeft: showFlowRail ? '0' : '2rem',
                    marginRight: showFlowRail ? '1rem' : '2rem',
                }"
            >
                <FrontAgentChatWorkspace
                    ref="agentChatWorkspaceRef"
                    :welcome-actions="welcomeActions"
                    :active-skill-context="activeSkillContext"
                    @unauthorized="handleUnauthorized"
                    @reset-chat-context="handleResetChatContext"
                    @skill-context-change="handleSkillContextChange"
                    @request-finished="handleRequestFinished"
                    @render-payload="handleRenderPayload"
                    @artifact-preview="handleArtifactPreview"
                    @welcome-action="handleWelcomeAction"
                />
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
                        :style="flowRailStyle"
                    >
                        <!-- Preview 面板（中间位置，自适应宽度） -->
                        <Transition name="panel-fade">
                            <PreviewTabContainer
                                v-if="showPreviewPanel"
                                class="preview-panel-container flex min-w-0 flex-col overflow-hidden rounded-2xl"
                                :class="previewPanelClass"
                                :style="previewPanelStyle"
                                :render-payload="renderPayload"
                                :session-code="sessionCode"
                                @close="closePreview"
                            />
                        </Transition>

                        <!-- Insight 面板（右侧位置，固定宽度） -->
                        <Transition name="panel-fade">
                            <FrontAgentInsightPanel
                                v-if="showInsightPanel && !showDockedInsight"
                                class="insight-panel-container min-w-0 flex-none overflow-hidden rounded-2xl"
                                :style="{ width: insightWidth }"
                                @close="showInsightPanel = false"
                                @select-insight-item="handleInsightSelect"
                            />
                        </Transition>
                    </div>
                </Transition>
            </div>

            <Transition name="insight-dock-slide">
                <div
                    v-if="showDockedInsight"
                    class="pointer-events-none absolute inset-y-4 right-4 z-20 flex justify-end"
                >
                    <FrontAgentInsightPanel
                        class="insight-panel-container pointer-events-auto min-w-0 overflow-hidden rounded-2xl"
                        :style="{ width: insightWidth }"
                        @close="showInsightPanel = false"
                        @select-insight-item="handleInsightSelect"
                    />
                </div>
            </Transition>
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, provide, reactive, ref, watch } from 'vue';
import FrontAgentChatHeader from '../components/agent-chat/FrontAgentChatHeader.vue';
import FrontAgentInsightPanel from '../components/agent-chat/FrontAgentInsightPanel.vue';
import FrontAgentChatWorkspace from '../components/agent-chat/FrontAgentChatWorkspace.vue';
import PreviewTabContainer from '../components/agent-chat/PreviewTabContainer.vue';

const emit = defineEmits(['unauthorized']);

const agentChatWorkspaceRef = ref(null);
const showPreview = ref(false);
const showInsightPanel = ref(false);
const renderPayload = ref(null);
const welcomeActions = [
    {
        label: '帮我梳理下近期的重点工作',
        actionType: 'open_insight',
    },
    {
        label: '今年整体经营情况怎么样?',
        actionType: 'send_prompt',
    },
    {
        label: '哪些项目实际收入远低于计划?',
        actionType: 'send_prompt',
    },
];

function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}

function reduceWidth(current, min, overflow) {
    const shrinkable = Math.max(0, current - min);
    const reduced = Math.min(shrinkable, Math.max(0, overflow));
    return {
        value: current - reduced,
        overflow: overflow - reduced,
    };
}

// 响应式断点：根据屏幕宽度计算各区域宽度
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440);

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

// ============================================================
// Insight 面板显示模式
// 'none': 不显示
// 'floating': 小屏悬浮，纯遮盖，不预留空间
// 'docked': 大屏固定在右侧绝对定位，预留相应空间
// 'flow': 中大屏内联显示在 flowRail 中
// ============================================================
const insightDisplayMode = computed(() => {
    if (!showInsightPanel.value) return 'none';

    const width = viewportWidth.value;

    // 强制悬浮阈值：提升至 1600px。确保中等屏幕（1280, 1366, 1440）一律使用绝对定位悬浮
    if (width < 1600) {
        return 'floating';
    }

    // 大屏预留空间策略
    const minWidth = showPreviewPanel.value ? 1680 : 1380;
    if (width >= minWidth) {
        return 'docked';
    }

    return 'flow';
});

// 为了兼容原模板中的变量名，showDockedInsight 此时代表 "是否使用绝对定位容器"
const showDockedInsight = computed(() => {
    return insightDisplayMode.value === 'docked' || insightDisplayMode.value === 'floating';
});

const sessionCode = computed(() => {
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

const showFlowRail = computed(() => showPreviewPanel.value || insightDisplayMode.value === 'flow');

const shouldCenterChat = computed(() => {
    // 纯悬浮模式下，靠左对齐，避免聊天区域居中导致右侧被悬浮面板遮挡
    if (insightDisplayMode.value === 'floating') {
        return false;
    }
    return !showFlowRail.value;
});

// ============================================================
// 统一布局常量
// ============================================================
const LAYOUT_CONSTANTS = {
    CHAT: { MIN: 440 }, // 基础保底
    PREVIEW: { MIN: 400 },
    INSIGHT: { MIN: 320, FIXED_FLOAT: 360 },
    GAP: 16,
    PADDING: 40, // 左右各 20px
};

/**
 * 核心布局算法：动态比例分配（带阶梯式平滑过渡）
 */
function calculateFluidLayout(available, fullWidth, showPreview, insightMode) {
    const { CHAT, PREVIEW, INSIGHT, GAP } = LAYOUT_CONSTANTS;

    // 1. 计算当前屏幕下的“对话框最大期望宽度”
    // 目的：缩小模式切换时的宽度差
    let chatMaxExpected = 1000;
    if (fullWidth < 1366) chatMaxExpected = 720;
    else if (fullWidth < 1600) chatMaxExpected = 800;

    // 状态 1：仅对话
    if (!showPreview && (insightMode === 'none' || insightMode === 'floating')) {
        return {
            chatWidth: clamp(available * 0.7, CHAT.MIN, chatMaxExpected),
            previewWidth: 0,
            insightWidth: insightMode === 'floating' ? INSIGHT.FIXED_FLOAT : 0,
            railWidth: 0,
        };
    }

    // 状态 2：对话 + 预览 (Insight 隐藏或悬浮)
    if (showPreview && (insightMode === 'none' || insightMode === 'floating')) {
        const netAvailable = available - GAP;

        // 动态比例：较小屏幕提高 Chat 权重 (45%)，大屏保持标准 (40%)
        const chatRatio = fullWidth < 1440 ? 0.45 : 0.4;
        const previewRatio = 1 - chatRatio;

        let chatWidth = netAvailable * chatRatio;
        let previewWidth = netAvailable * previewRatio;

        // 保底修正
        if (chatWidth < CHAT.MIN) {
            chatWidth = CHAT.MIN;
            previewWidth = Math.max(PREVIEW.MIN, netAvailable - chatWidth);
        } else if (previewWidth < PREVIEW.MIN) {
            previewWidth = PREVIEW.MIN;
            chatWidth = Math.max(CHAT.MIN, netAvailable - previewWidth);
        }

        return {
            chatWidth: Math.min(chatWidth, chatMaxExpected + 100),
            previewWidth,
            insightWidth: insightMode === 'floating' ? INSIGHT.FIXED_FLOAT : 0,
            railWidth: previewWidth,
        };
    }

    // 状态 3：对话 + Insight (内联 flow 模式，无预览)
    if (!showPreview && insightMode === 'flow') {
        const netAvailable = available - GAP;
        let chatWidth = netAvailable * 0.65;
        let insightWidth = netAvailable * 0.35;

        return {
            chatWidth: clamp(chatWidth, CHAT.MIN, chatMaxExpected),
            previewWidth: 0,
            insightWidth: Math.max(INSIGHT.MIN, insightWidth),
            railWidth: Math.max(INSIGHT.MIN, insightWidth),
        };
    }

    // 状态 4：全开模式 (Preview + Insight 均内联，通常在超大屏)
    const netAvailable = available - GAP * 2;
    // 比例：Chat 35%, Preview 45%, Insight 20%
    let chatWidth = netAvailable * 0.35;
    let previewWidth = netAvailable * 0.45;
    let insightWidth = netAvailable * 0.2;

    return {
        chatWidth: Math.max(CHAT.MIN, chatWidth),
        previewWidth: Math.max(PREVIEW.MIN, previewWidth),
        insightWidth: Math.max(INSIGHT.MIN, insightWidth),
        railWidth: Math.max(PREVIEW.MIN, previewWidth) + GAP + Math.max(INSIGHT.MIN, insightWidth),
    };
}

// 响应式宽度配置
const layoutConfig = computed(() => {
    const width = viewportWidth.value;
    const available = width - LAYOUT_CONSTANTS.PADDING;

    const config = calculateFluidLayout(
        available,
        width,
        showPreviewPanel.value,
        insightDisplayMode.value
    );

    return {
        chatWidthPx: config.chatWidth,
        insightWidthPx: config.insightWidth,
        previewWidthPx: config.previewWidth,
        railWidthPx: config.railWidth,
        dockedInsightReservePx:
            insightDisplayMode.value === 'docked' ? config.insightWidth + LAYOUT_CONSTANTS.GAP : 0,
    };
});

const chatWidth = computed(() => `${layoutConfig.value.chatWidthPx}px`);

const insightWidth = computed(() => `${layoutConfig.value.insightWidthPx}px`);

const previewPanelClass = computed(() => 'flex-1');

const previewPanelStyle = computed(() => undefined);

const flowRailStyle = computed(() => {
    if (!showFlowRail.value) {
        return undefined;
    }

    // 轨道样式：由 Flex 自动撑开，如果是纯预览，则占满轨道
    return {
        flex: '1 1 0%',
        minWidth: 0,
        justifyContent: 'flex-end',
    };
});

const pageContentStyle = computed(() => {
    // 仅在 Docked 模式下增加右侧内边距，为绝对定位面板留出物理空间
    if (insightDisplayMode.value !== 'docked' || showPreviewPanel.value) {
        return undefined;
    }
    return {
        paddingRight: `${LAYOUT_CONSTANTS.GAP + layoutConfig.value.dockedInsightReservePx}px`,
    };
});

// 技能选择状态：用于 Header 点击技能后通知 Workspace
const pendingSkillSelect = ref(null);
const pendingSkillContext = ref(null);
const lastActiveSessionCode = ref('');
const activeSkillContextBySession = reactive({});
provide('pendingSkillSelect', pendingSkillSelect);

function normalizeSessionCode(value) {
    return String(value || '').trim();
}

const activeSkillContext = computed(() => {
    const normalizedSessionCode = normalizeSessionCode(sessionCode.value);
    if (!normalizedSessionCode) {
        return pendingSkillContext.value;
    }
    return activeSkillContextBySession[normalizedSessionCode] || null;
});

const activeSkillDisplayName = computed(() => {
    return (
        activeSkillContext.value?.displayName ||
        activeSkillContext.value?.runtimeSkillName ||
        '已选择技能'
    );
});

const activeSkillCaption = computed(() => {
    return normalizeSessionCode(sessionCode.value) ? '当前会话技能' : '当前待用技能';
});

function normalizeSkillContext(skill) {
    const rawId = skill?.id ?? skill?.scopeId ?? null;
    const normalizedId =
        typeof rawId === 'number' && Number.isFinite(rawId) ? rawId : String(rawId || '').trim();
    if (normalizedId == null || normalizedId === '') {
        return null;
    }
    return {
        id: normalizedId,
        displayName: skill?.displayName || skill?.scopeDisplayName || skill?.runtimeSkillName || '',
        description: skill?.description || '',
        runtimeSkillName: skill?.runtimeSkillName || '',
    };
}

function setSkillContextForSession(sessionId, skill) {
    const normalizedSkill = normalizeSkillContext(skill);
    const normalizedSessionCode = normalizeSessionCode(sessionId);
    if (!normalizedSkill) {
        return;
    }
    if (!normalizedSessionCode) {
        pendingSkillContext.value = normalizedSkill;
        return;
    }
    activeSkillContextBySession[normalizedSessionCode] = normalizedSkill;
}

function clearSkillContextForSession(sessionId) {
    const normalizedSessionCode = normalizeSessionCode(sessionId);
    if (!normalizedSessionCode) {
        pendingSkillContext.value = null;
        return;
    }
    delete activeSkillContextBySession[normalizedSessionCode];
}

watch(
    sessionCode,
    nextSessionCode => {
        const normalizedSessionCode = normalizeSessionCode(nextSessionCode);
        if (!normalizedSessionCode) {
            return;
        }
        lastActiveSessionCode.value = normalizedSessionCode;
    },
    { immediate: true }
);

function handleUnauthorized() {
    emit('unauthorized');
}

function handleRequestFinished(payload) {
    const normalizedSessionCode = normalizeSessionCode(payload?.sessionId);
    if (!normalizedSessionCode || !pendingSkillContext.value) {
        return;
    }
    activeSkillContextBySession[normalizedSessionCode] = pendingSkillContext.value;
    pendingSkillContext.value = null;
    lastActiveSessionCode.value = normalizedSessionCode;
}

function handleResetChatContext() {
    const currentSessionCode =
        normalizeSessionCode(sessionCode.value) ||
        normalizeSessionCode(lastActiveSessionCode.value);
    clearSkillContextForSession(currentSessionCode);
    pendingSkillContext.value = null;
}

function handleSkillContextChange(skill) {
    if (!normalizeSkillContext(skill)) {
        return;
    }
    setSkillContextForSession(sessionCode.value, skill);
}

function clearCurrentSkillContext() {
    clearSkillContextForSession(sessionCode.value);
}

function toggleInsightPanel() {
    showInsightPanel.value = !showInsightPanel.value;
}

function handleWelcomeAction(action) {
    const actionType = String(action?.actionType || '')
        .trim()
        .toLowerCase();
    if (actionType === 'open_insight') {
        showInsightPanel.value = true;
    }
}

function handleInsightSelect(payload) {
    if (!payload?.skill?.id) {
        return;
    }
    setSkillContextForSession(sessionCode.value, payload.skill);
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
    // 尝试从文件名推断可读标题
    const readableTitle = inferPreviewTitle(segment.fileName || segment.title);
    renderPayload.value = {
        type: 'artifact_preview',
        title: readableTitle,
        previewUrl: segment.previewUrl,
        downloadUrl: segment.downloadUrl,
        fileName: segment.fileName,
    };
    showPreview.value = true;
}

function inferPreviewTitle(fileName) {
    // 文件名到可读标题的映射
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
    // 移除 .html 后缀作为 fallback
    return fileName.replace(/\.html?$/i, '') || 'HTML 产物预览';
}

function closePreview() {
    showPreview.value = false;
    showPreviewPanelManual.value = false;
}
</script>

<style scoped>
.agent-chat-page {
    background: linear-gradient(180deg, #ffffff 0%, #f7f9fc 100%);
}

.preview-panel-container,
.insight-panel-container {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.08);
}

.chat-workspace-container {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.08);
}

/* Flow Rail 离开时宽度收缩到 0，避免占位导致布局跳动 */
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

/* 单个面板淡入动画 */
.panel-fade-enter-active,
.panel-fade-leave-active {
    transition: opacity 0.2s ease;
}

.panel-fade-enter-from,
.panel-fade-leave-to {
    opacity: 0;
}

.insight-dock-slide-enter-active,
.insight-dock-slide-leave-active {
    transition:
        opacity 0.2s ease,
        transform 0.2s ease;
}

.insight-dock-slide-enter-from,
.insight-dock-slide-leave-to {
    opacity: 0;
    transform: translateX(12px);
}
</style>
