<template>
    <FrontChatWorkspace
        :key="workspaceKey"
        class="min-h-0 w-full flex-1 overflow-hidden"
        :adapter="activeAdapter"
        :history-adapter="historyAdapter"
        :header-title="headerTitle"
        :header-status-text="headerStatusText"
        :header-icon="headerIcon"
        :show-header="true"
        :show-sidebar-assistant-header="true"
        :initial-sidebar-collapsed="false"
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        :empty-icon="emptyIcon"
        :draft-placeholder="draftPlaceholder"
        :show-knowledge-select="false"
        :enable-attachments="true"
        :enable-skill-mention="isGeneralMode"
        :active-skill-context="currentSkillContext"
        :agent-settings-context="agentSettingsContext"
        :default-knowledge="defaultScopeId"
        :session-storage-key="workspaceSessionStorageKey"
        :restore-initial-session="isGeneralMode"
        :use-runtime-header-status="isGeneralMode"
        :sidebar-collapsed="sidebarCollapsed"
        :hide-sidebar-when-collapsed="true"
        @unauthorized="emitUnauthorized"
        @update:sidebar-collapsed="$emit('update:sidebarCollapsed', $event)"
        @conversation-context-change="handleConversationContextChange"
        @reset-chat-context="handleResetChatContext"
        @assistant-summary-click="handleAssistantSummaryClick"
    />
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getEnabledExpertPackage } from '@/api/agents';
import {
    agentConfigState,
    ensureAgentConfigLoaded,
    getAgentDisplayName,
    getAgentIcon,
} from '@/composables/useAgentConfig';
import FrontChatWorkspace from '@/views/front/components/front-chat/FrontChatWorkspace.vue';
import { generalChatAdapter } from '@/views/front/components/front-chat/adapters/generalChatAdapter';
import { skillChatAdapter } from '@/views/front/components/front-chat/adapters/skillChatAdapter';
import { expertPackageChatAdapter } from '@/views/front/components/front-chat/adapters/expertPackageChatAdapter';
import { MIXED_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
import { ROUTE_PATHS } from '@/router/routePaths';

defineProps({
    sidebarCollapsed: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['unauthorized', 'update:sidebarCollapsed']);
const route = useRoute();
const router = useRouter();
let entryLoadToken = 0;
const mixedChatSessionStorageKey = MIXED_CHAT_SESSION_STORAGE_KEY;

function normalizeQueryText(value) {
    if (Array.isArray(value)) {
        return normalizeQueryText(value[0]);
    }
    return value == null ? '' : String(value).trim();
}

function normalizeSkillId(value) {
    if (Array.isArray(value)) {
        return normalizeSkillId(value[0]);
    }
    if (value == null) {
        return '';
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
        return String(value);
    }
    const text = String(value).trim();
    return /^\d+$/.test(text) ? text : '';
}

function normalizeSkillContext(skill) {
    const id = normalizeSkillId(skill?.id ?? skill?.scopeId);
    if (!id) {
        return null;
    }
    return {
        id,
        displayName:
            skill?.displayName || skill?.scopeDisplayName || skill?.runtimeSkillName || '技能对话',
        description: skill?.description || '',
        runtimeSkillName: skill?.runtimeSkillName || '',
    };
}

function createExpertPackageFallback(packageId, source = {}) {
    return {
        id: packageId,
        agentName:
            normalizeQueryText(source.agentName) ||
            normalizeQueryText(source.expertPackageName) ||
            normalizeQueryText(source.expertPackageTitle) ||
            '专家技能包',
        description:
            normalizeQueryText(source.description) ||
            normalizeQueryText(source.expertPackageDescription),
        openingMessage:
            normalizeQueryText(source.openingMessage) ||
            normalizeQueryText(source.expertPackageOpeningMessage),
        icon:
            normalizeQueryText(source.icon) ||
            normalizeQueryText(source.expertPackageIcon) ||
            'psychology',
        skills: Array.isArray(source.skills) ? source.skills : [],
        tools: Array.isArray(source.tools) ? source.tools : [],
    };
}

function mergeExpertPackageDetail(packageId, fallback = {}, detail = {}) {
    return {
        id: detail?.id ?? fallback?.id ?? packageId,
        agentName:
            normalizeQueryText(detail?.agentName) ||
            normalizeQueryText(detail?.displayName) ||
            normalizeQueryText(fallback?.agentName) ||
            '专家技能包',
        description:
            normalizeQueryText(detail?.description) || normalizeQueryText(fallback?.description),
        openingMessage:
            normalizeQueryText(detail?.openingMessage) ||
            normalizeQueryText(fallback?.openingMessage),
        icon:
            normalizeQueryText(detail?.icon) || normalizeQueryText(fallback?.icon) || 'psychology',
        skills: Array.isArray(detail?.skills)
            ? detail.skills
            : Array.isArray(fallback?.skills)
              ? fallback.skills
              : [],
        tools: Array.isArray(detail?.tools)
            ? detail.tools
            : Array.isArray(fallback?.tools)
              ? fallback.tools
              : [],
    };
}

const initialRouteExpertPackageId = normalizeSkillId(route.query.expertPackageId);
const initialRouteSkillContext = initialRouteExpertPackageId
    ? null
    : normalizeSkillContext({
          id: route.query.skillId,
          displayName: normalizeQueryText(route.query.skillName),
          description: normalizeQueryText(route.query.skillDescription),
      });
const currentMode = ref(
    initialRouteExpertPackageId ? 'expert-package' : initialRouteSkillContext ? 'skill' : 'general'
);
const currentSkillContext = ref(initialRouteSkillContext);
const currentExpertPackage = ref(
    initialRouteExpertPackageId
        ? createExpertPackageFallback(initialRouteExpertPackageId, route.query)
        : null
);
const currentConversationType = ref(
    initialRouteExpertPackageId
        ? 'EXPERT_SKILL_PACKAGE_CHAT'
        : initialRouteSkillContext
          ? 'SKILL_CHAT'
          : ''
);

function resetEntryContext(skill) {
    const normalized = normalizeSkillContext(skill);
    if (normalized) {
        currentMode.value = 'skill';
        currentSkillContext.value = normalized;
        currentExpertPackage.value = null;
        currentConversationType.value = 'SKILL_CHAT';
        return;
    }
    currentMode.value = 'general';
    currentSkillContext.value = null;
    currentExpertPackage.value = null;
    currentConversationType.value = '';
}

const initialSkill = computed(() => {
    const skillId = route.query.skillId;
    if (!skillId) {
        return null;
    }
    return {
        id: skillId,
        displayName: normalizeQueryText(route.query.skillName),
        description: normalizeQueryText(route.query.skillDescription),
    };
});
const initialExpertPackageId = computed(() => normalizeSkillId(route.query.expertPackageId));
const initialExpertPackage = computed(() => {
    const packageId = initialExpertPackageId.value;
    if (!packageId) {
        return null;
    }
    return createExpertPackageFallback(packageId, route.query);
});
const expertPackageHeader = computed(() => {
    if (!initialExpertPackageId.value) {
        return null;
    }
    return {
        id: initialExpertPackageId.value,
        agentName: initialExpertPackage.value?.agentName || '专家技能包',
        description: initialExpertPackage.value?.description || '',
        openingMessage: initialExpertPackage.value?.openingMessage || '',
        icon: initialExpertPackage.value?.icon || 'psychology',
    };
});
const workspaceSessionStorageKey = computed(() =>
    initialExpertPackageId.value
        ? expertPackageChatAdapter.sessionStorageKey
        : mixedChatSessionStorageKey
);
const defaultScopeId = computed(() =>
    isExpertPackageMode.value
        ? normalizeSkillId(currentExpertPackage.value?.id || initialExpertPackageId.value)
        : normalizeSkillId(currentSkillContext.value?.id)
);

const workspaceKey = computed(
    () =>
        `chat-${initialExpertPackageId.value ? `expert-${initialExpertPackageId.value}` : initialSkill.value?.id || 'general'}`
);
const isGeneralMode = computed(() => currentMode.value === 'general');
const isAgentAssistantConversation = computed(() => {
    if (!isGeneralMode.value) {
        return false;
    }
    const type = String(currentConversationType.value || '').trim();
    return !type || type === 'GENERAL_CHAT';
});
const isSkillMode = computed(
    () => currentMode.value === 'skill' && Boolean(currentSkillContext.value?.id)
);
const isExpertPackageMode = computed(
    () => currentMode.value === 'expert-package' && Boolean(currentExpertPackage.value?.id)
);
const activeAdapter = computed(() => {
    if (isExpertPackageMode.value) {
        return expertPackageChatAdapter;
    }
    return isSkillMode.value ? skillChatAdapter : generalChatAdapter;
});
const historyAdapter = computed(() =>
    isExpertPackageMode.value || isSkillMode.value ? generalChatAdapter : null
);
const headerTitle = computed(() => {
    if (isExpertPackageMode.value) {
        return currentExpertPackage.value?.agentName || expertPackageHeader.value?.agentName;
    }
    if (!isSkillMode.value) {
        return getAgentDisplayName();
    }
    return (
        currentSkillContext.value?.displayName ||
        currentSkillContext.value?.runtimeSkillName ||
        '技能对话'
    );
});
const headerStatusText = computed(() => {
    if (isExpertPackageMode.value) {
        return (
            currentExpertPackage.value?.description || expertPackageHeader.value?.description || ''
        );
    }
    if (!isSkillMode.value) {
        return '工作区已就绪';
    }
    return currentSkillContext.value?.description || '当前技能上下文已固定';
});
const headerIcon = computed(() => {
    if (isExpertPackageMode.value) {
        return currentExpertPackage.value?.icon || expertPackageHeader.value?.icon || 'psychology';
    }
    return isSkillMode.value ? 'description' : getAgentIcon();
});
const emptyTitle = computed(() => {
    if (isExpertPackageMode.value) {
        return `和「${
            currentExpertPackage.value?.agentName ||
            expertPackageHeader.value?.agentName ||
            '专家技能包'
        }」开始协作`;
    }
    if (!isSkillMode.value) {
        return '今天想完成什么？';
    }
    return currentSkillContext.value?.displayName
        ? `和「${currentSkillContext.value.displayName}」开始协作`
        : '开始新的技能任务';
});
const emptyIcon = computed(() => {
    if (isExpertPackageMode.value) {
        return currentExpertPackage.value?.icon || 'psychology';
    }
    return isSkillMode.value ? 'inventory_2' : getAgentIcon();
});
const emptyDescription = computed(() => {
    if (isExpertPackageMode.value) {
        return (
            currentExpertPackage.value?.openingMessage ||
            expertPackageHeader.value?.openingMessage ||
            currentExpertPackage.value?.description ||
            expertPackageHeader.value?.description ||
            '围绕当前专家技能包直接发起任务。'
        );
    }
    if (isSkillMode.value) {
        return (
            currentSkillContext.value?.description ||
            '围绕当前技能直接发起任务，系统会保留独立的会话记录和上下文。'
        );
    }
    return '继续已有会话，或直接从下方输入一个任务，让 AI 帮你分析、写作和整理信息。';
});
const draftPlaceholder = computed(() =>
    isExpertPackageMode.value
        ? '向当前专家技能包发起任务...'
        : isSkillMode.value
          ? '向当前技能发起任务...'
          : '告诉我你想完成什么...'
);
const agentSettingsContext = computed(() => {
    if (isExpertPackageMode.value) {
        return {
            type: 'expert-package',
            displayName:
                currentExpertPackage.value?.agentName || expertPackageHeader.value?.agentName,
            description:
                currentExpertPackage.value?.description ||
                expertPackageHeader.value?.description ||
                '',
            icon:
                currentExpertPackage.value?.icon || expertPackageHeader.value?.icon || 'psychology',
            skills: Array.isArray(currentExpertPackage.value?.skills)
                ? currentExpertPackage.value.skills
                : [],
            tools: Array.isArray(currentExpertPackage.value?.tools)
                ? currentExpertPackage.value.tools
                : [],
        };
    }
    if (isAgentAssistantConversation.value) {
        return {
            type: 'agent',
        };
    }
    return {
        type: 'hidden',
    };
});

watch(
    () => [initialExpertPackageId.value, initialSkill.value, initialExpertPackage.value],
    async ([packageId, skill, packageFallback]) => {
        const token = ++entryLoadToken;
        if (!packageId) {
            resetEntryContext(skill);
            return;
        }
        const fallbackPackage = packageFallback ||
            expertPackageHeader.value || {
                id: packageId,
                agentName: '专家技能包',
                description: '',
                openingMessage: '',
                icon: 'psychology',
            };
        currentMode.value = 'expert-package';
        currentExpertPackage.value = fallbackPackage;
        currentSkillContext.value = null;
        currentConversationType.value = 'EXPERT_SKILL_PACKAGE_CHAT';
        try {
            const detail = await getEnabledExpertPackage(packageId, emitUnauthorized);
            if (token !== entryLoadToken) {
                return;
            }
            currentExpertPackage.value = mergeExpertPackageDetail(
                packageId,
                fallbackPackage,
                detail
            );
        } catch (error) {
            if (token === entryLoadToken) {
                currentMode.value = 'expert-package';
                currentExpertPackage.value = fallbackPackage;
                currentSkillContext.value = null;
                currentConversationType.value = 'EXPERT_SKILL_PACKAGE_CHAT';
            }
        }
    },
    { immediate: true, deep: true }
);

async function hydrateExpertPackage(packageId, fallback = {}) {
    if (!packageId) {
        return;
    }
    const existing = currentExpertPackage.value;
    if (String(existing?.id || '') === String(packageId) && existing?.description) {
        return;
    }
    try {
        const detail = await getEnabledExpertPackage(packageId, emitUnauthorized);
        if (String(currentExpertPackage.value?.id || '') !== String(packageId)) {
            return;
        }
        currentExpertPackage.value = mergeExpertPackageDetail(packageId, fallback, detail);
    } catch (error) {
        // 历史会话切换时详情补全失败，不影响继续对话；保留会话上下文里的名称。
    }
}

function handleConversationContextChange(context) {
    const sessionType = String(context?.sessionType || '').trim();
    currentConversationType.value = sessionType;
    if (sessionType === 'EXPERT_SKILL_PACKAGE_CHAT') {
        const packageId = normalizeSkillId(context?.scopeId);
        if (packageId) {
            const isCurrentPackage =
                String(currentExpertPackage.value?.id || '') === String(packageId);
            const fallbackPackage = {
                id: packageId,
                agentName: context?.scopeDisplayName || '专家技能包',
                description: isCurrentPackage ? currentExpertPackage.value?.description || '' : '',
                openingMessage: isCurrentPackage
                    ? currentExpertPackage.value?.openingMessage || ''
                    : '',
                icon: isCurrentPackage
                    ? currentExpertPackage.value?.icon || 'psychology'
                    : 'psychology',
                skills:
                    isCurrentPackage && Array.isArray(currentExpertPackage.value?.skills)
                        ? currentExpertPackage.value.skills
                        : [],
                tools:
                    isCurrentPackage && Array.isArray(currentExpertPackage.value?.tools)
                        ? currentExpertPackage.value.tools
                        : [],
            };
            currentMode.value = 'expert-package';
            currentExpertPackage.value = isCurrentPackage
                ? mergeExpertPackageDetail(packageId, fallbackPackage, currentExpertPackage.value)
                : fallbackPackage;
            currentSkillContext.value = null;
            hydrateExpertPackage(packageId, fallbackPackage);
            return;
        }
    }
    if (sessionType === 'SKILL_CHAT' || sessionType === 'PUBLISHED_SKILL_CHAT') {
        const normalized = normalizeSkillContext({
            id: context?.scopeId,
            scopeDisplayName: context?.scopeDisplayName,
            displayName: context?.scopeDisplayName,
        });
        if (normalized) {
            currentMode.value = 'skill';
            currentSkillContext.value = {
                ...normalized,
                description:
                    currentSkillContext.value?.id === normalized.id
                        ? currentSkillContext.value?.description || ''
                        : '',
            };
            currentExpertPackage.value = null;
            return;
        }
    }
    currentMode.value = 'general';
    currentSkillContext.value = null;
    currentExpertPackage.value = null;
}

function handleResetChatContext() {
    if (initialExpertPackageId.value) {
        return;
    }
    resetEntryContext(initialSkill.value);
}

function handleAssistantSummaryClick() {
    if (isGeneralMode.value) {
        return;
    }
    router.push(ROUTE_PATHS.frontChat);
}

function emitUnauthorized() {
    emit('unauthorized');
}

onMounted(() => {
    if (!agentConfigState.initialized && !agentConfigState.loading) {
        ensureAgentConfigLoaded().catch(() => {});
    }
});
</script>
