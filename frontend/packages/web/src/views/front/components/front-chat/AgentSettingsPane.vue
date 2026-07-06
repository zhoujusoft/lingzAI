<template>
    <div class="agent-settings-pane flex h-full flex-col overflow-hidden bg-surface">
        <div class="agent-settings-scroll custom-scrollbar flex-1 overflow-y-auto px-4 py-4">
            <div v-if="isEditView && !readonly" class="space-y-5">
                <button
                    type="button"
                    class="inline-flex h-8 items-center gap-1.5 rounded-lg px-2 text-sm font-medium text-muted transition hover:bg-surface-alt hover:text-strong"
                    @click="viewMode = 'overview'"
                >
                    <span class="material-symbols-outlined text-base">arrow_back</span>
                    返回
                </button>
                <section
                    class="flex flex-col gap-4 rounded-xl border border-border-soft bg-surface-alt/40 p-4"
                >
                    <button
                        type="button"
                        class="group relative h-24 w-24 shrink-0 overflow-hidden rounded-full border border-border-soft bg-white shadow-sm transition hover:shadow-md disabled:cursor-not-allowed disabled:opacity-75"
                        :disabled="avatarUploading"
                        @click="$emit('trigger-avatar-upload')"
                    >
                        <img
                            v-if="avatarVisual.type === 'image'"
                            :src="avatarVisual.value"
                            alt="个人助手头像"
                            class="h-full w-full object-cover"
                        />
                        <div
                            v-else
                            class="flex h-full w-full items-center justify-center bg-[#f0f4f9] text-strong"
                        >
                            <span
                                v-if="avatarVisual.type === 'material'"
                                class="material-symbols-outlined text-[36px]"
                            >
                                {{ avatarVisual.value }}
                            </span>
                            <span v-else class="text-[36px] leading-none">
                                {{ avatarVisual.value }}
                            </span>
                        </div>
                        <div
                            class="absolute inset-0 flex items-center justify-center bg-slate-900/55 px-3 text-center text-xs font-medium text-white opacity-0 transition-opacity duration-200 group-hover:opacity-100"
                            :class="{ 'opacity-100': avatarUploading }"
                        >
                            {{ avatarUploading ? '上传中...' : '更换头像' }}
                        </div>
                    </button>

                    <div class="space-y-1.5">
                        <p class="text-sm font-semibold text-strong">助手头像</p>
                        <p class="text-sm leading-5 text-muted">
                            支持 JPG / PNG，文件大小不超过 2MB。
                        </p>
                        <p v-if="avatarErrorMessage" class="text-sm font-medium text-danger">
                            {{ avatarErrorMessage }}
                        </p>
                    </div>
                </section>

                <label class="block space-y-2">
                    <span class="text-xs font-semibold uppercase text-muted">助理名称</span>
                    <input
                        :value="modelValue"
                        type="text"
                        maxlength="40"
                        placeholder="请输入助理名称"
                        class="h-10 w-full rounded-lg border border-border-soft bg-white px-3 text-sm text-strong outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                        @input="$emit('update:modelValue', $event.target.value)"
                    />
                </label>
                <p class="text-xs text-muted">留空时将使用当前 Agent 模板默认名称。</p>
                <p v-if="errorMessage" class="text-sm text-danger">{{ errorMessage }}</p>
                <div class="flex justify-end">
                    <button
                        type="button"
                        class="inline-flex h-9 items-center rounded-lg bg-primary px-4 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving"
                        @click="$emit('save')"
                    >
                        {{ saving ? '保存中...' : '保存' }}
                    </button>
                </div>
            </div>

            <div v-else class="space-y-5">
                <section class="flex flex-col items-center px-2 py-4 text-center">
                    <div
                        class="flex h-16 w-16 items-center justify-center overflow-hidden rounded-full border border-border-soft bg-white"
                    >
                        <img
                            v-if="avatarVisual.type === 'image'"
                            :src="avatarVisual.value"
                            :alt="displayName || 'Agent 头像'"
                            class="h-full w-full object-cover"
                        />
                        <span
                            v-else-if="avatarVisual.type === 'material'"
                            class="material-symbols-outlined text-[32px] text-strong"
                        >
                            {{ avatarVisual.value }}
                        </span>
                        <span v-else class="text-[32px] leading-none">
                            {{ avatarVisual.value }}
                        </span>
                    </div>
                    <h4 class="mt-3 text-base font-semibold text-strong">
                        {{ displayName || (readonly ? '技能对话' : '助理') }}
                    </h4>
                    <p class="mt-2 max-w-[260px] text-sm leading-5 text-muted">
                        {{ description || defaultOverviewDescription }}
                    </p>
                    <button
                        v-if="!readonly"
                        type="button"
                        class="mt-4 inline-flex h-9 items-center rounded-xl border border-border-soft bg-white px-4 text-sm font-semibold text-strong shadow-sm transition hover:bg-surface-alt"
                        @click="viewMode = 'edit'"
                    >
                        修改 Agent 信息
                    </button>
                </section>

                <section
                    v-if="showCapabilities"
                    class="overflow-hidden rounded-2xl border border-border-soft bg-white shadow-sm"
                >
                    <div class="border-b border-border-soft px-4 py-3">
                        <div class="flex items-center justify-between gap-3">
                            <div>
                                <h5 class="text-sm font-semibold text-strong">专家能力</h5>
                                <p class="mt-1 text-xs text-muted">
                                    当前专家包内置的技能与工具，仅用于本次专家包对话。
                                </p>
                            </div>
                            <span
                                class="shrink-0 rounded-full bg-surface-alt px-2 py-1 text-[11px] font-medium text-muted"
                            >
                                {{ capabilityTotal }} 项
                            </span>
                        </div>
                    </div>

                    <div class="space-y-4 p-4">
                        <div v-if="normalizedSkills.length">
                            <div class="mb-2 flex items-center justify-between gap-2">
                                <h6 class="text-xs font-semibold text-strong">包含技能</h6>
                                <span class="text-[11px] text-muted">
                                    {{ normalizedSkills.length }} 项
                                </span>
                            </div>
                            <div class="grid gap-2">
                                <div
                                    v-for="skill in normalizedSkills"
                                    :key="skill.id || skill.displayName"
                                    class="flex items-start gap-2.5 rounded-xl border border-border-soft/70 bg-surface-alt/40 px-3 py-2.5"
                                >
                                    <div
                                        :class="[
                                            'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-white shadow-sm',
                                            getSkillIconGradientClass(skill.iconColor),
                                        ]"
                                    >
                                        <span class="material-symbols-outlined text-base">
                                            {{ resolveSkillIcon(skill.icon) }}
                                        </span>
                                    </div>
                                    <div class="min-w-0 flex-1">
                                        <p class="truncate text-sm font-semibold text-strong">
                                            {{
                                                skill.displayName ||
                                                skill.runtimeSkillName ||
                                                '未命名技能'
                                            }}
                                        </p>
                                        <p
                                            v-if="skill.description"
                                            class="mt-0.5 line-clamp-2 text-xs leading-4 text-muted"
                                        >
                                            {{ skill.description }}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div v-if="normalizedTools.length">
                            <div class="mb-2 flex items-center justify-between gap-2">
                                <h6 class="text-xs font-semibold text-strong">包含工具</h6>
                                <span class="text-[11px] text-muted">
                                    {{ normalizedTools.length }} 项
                                </span>
                            </div>
                            <div class="flex flex-wrap gap-1.5">
                                <span
                                    v-for="tool in normalizedTools"
                                    :key="tool.id || tool.toolName || tool.displayName"
                                    class="inline-flex max-w-full items-center gap-1.5 rounded-lg bg-surface-alt px-2 py-1 text-xs font-medium text-body"
                                    :title="tool.description || tool.displayName || tool.toolName"
                                >
                                    <span class="material-symbols-outlined text-sm text-primary">
                                        {{ resolveToolIcon(tool.toolType || tool.type) }}
                                    </span>
                                    <span class="max-w-[180px] truncate">
                                        {{ tool.displayName || tool.toolName || '未命名工具' }}
                                    </span>
                                </span>
                            </div>
                        </div>

                        <div
                            v-if="!capabilityTotal"
                            class="rounded-xl border border-dashed border-border-soft bg-surface-alt/40 px-4 py-5 text-center text-xs text-muted"
                        >
                            暂未配置技能或工具。
                        </div>
                    </div>
                </section>

                <section
                    v-if="showChannels"
                    class="overflow-hidden rounded-2xl border border-border-soft bg-white shadow-sm"
                >
                    <div
                        class="flex items-start justify-between gap-4 border-b border-border-soft px-5 py-4"
                    >
                        <div>
                            <h5 class="text-base font-semibold text-strong">渠道连接</h5>
                            <p class="mt-1 text-xs text-muted">
                                连接更多聊天平台，让 Agent 能够在不同渠道为用户提供服务。
                            </p>
                        </div>
                        <div v-if="!readonly" ref="channelMenuRef" class="relative shrink-0">
                            <button
                                type="button"
                                class="inline-flex h-9 items-center gap-1 rounded-lg bg-primary px-3 text-xs font-semibold text-white transition hover:bg-blue-700"
                                @click="channelMenuOpen = !channelMenuOpen"
                            >
                                <span class="material-symbols-outlined text-base">add</span>
                                添加渠道
                            </button>
                            <div
                                v-if="channelMenuOpen"
                                class="absolute right-0 top-[calc(100%+6px)] z-20 w-40 rounded-xl border border-border-soft bg-white p-1 text-strong shadow-lg"
                            >
                                <button
                                    v-for="channel in supportedChannels"
                                    :key="channel.type"
                                    type="button"
                                    class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition hover:bg-surface-alt"
                                    @click.stop="handleChannelClick(channel.type)"
                                >
                                    <img
                                        :src="channel.logo"
                                        :alt="channel.label"
                                        class="h-5 w-5 shrink-0 rounded-md object-contain"
                                    />
                                    <span>{{ channel.label }}</span>
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="space-y-2 p-4">
                        <div
                            v-for="channel in channelItems"
                            :key="channel.type"
                            class="flex items-center gap-3 rounded-xl border border-border-soft px-4 py-3"
                        >
                            <img
                                :src="channel.logo"
                                :alt="channel.label"
                                class="h-9 w-9 shrink-0 rounded-lg object-contain"
                            />
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-strong">
                                    {{ channel.label }}
                                </p>
                                <p class="mt-0.5 truncate text-xs text-muted">
                                    {{ channel.description }}
                                </p>
                            </div>
                            <span
                                class="inline-flex items-center gap-1 text-xs font-medium"
                                :class="channel.connected ? 'text-success' : 'text-muted'"
                            >
                                <span class="h-1.5 w-1.5 rounded-full bg-current" />
                                {{ channel.statusLabel }}
                            </span>
                            <button
                                v-if="!readonly"
                                type="button"
                                class="inline-flex h-8 items-center rounded-lg border border-border-soft px-3 text-xs font-medium text-body transition hover:bg-surface-alt disabled:cursor-not-allowed disabled:opacity-50"
                                :disabled="
                                    channel.bound && closingChannelIds.includes(channel.channelId)
                                "
                                @click="
                                    channel.bound
                                        ? $emit('close-channel', channel)
                                        : handleChannelClick(channel.type)
                                "
                            >
                                {{
                                    channel.bound
                                        ? closingChannelIds.includes(channel.channelId)
                                            ? '关闭中...'
                                            : '关闭'
                                        : '连接'
                                }}
                            </button>
                        </div>
                        <p v-if="channelStatusMessage" class="mt-1 text-xs text-muted">
                            {{ channelStatusMessage }}
                        </p>
                    </div>
                </section>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { CHANNEL_LOGO_SOURCES } from '@/utils/channelVisuals';
import { getSkillIconGradientClass, resolveSkillIcon } from '@/utils/skillVisuals';
import { resolveToolIcon } from '@/utils/toolVisuals';

const props = defineProps({
    modelValue: {
        type: String,
        default: '',
    },
    readonly: {
        type: Boolean,
        default: false,
    },
    displayName: {
        type: String,
        default: '',
    },
    description: {
        type: String,
        default: '',
    },
    avatarVisual: {
        type: Object,
        default: () => ({ type: 'material', value: 'smart_toy' }),
    },
    showChannels: {
        type: Boolean,
        default: true,
    },
    avatarUploading: {
        type: Boolean,
        default: false,
    },
    avatarErrorMessage: {
        type: String,
        default: '',
    },
    saving: {
        type: Boolean,
        default: false,
    },
    errorMessage: {
        type: String,
        default: '',
    },
    channelStatusMessage: {
        type: String,
        default: '',
    },
    boundChannels: {
        type: Array,
        default: () => [],
    },
    closingChannelIds: {
        type: Array,
        default: () => [],
    },
    skills: {
        type: Array,
        default: () => [],
    },
    tools: {
        type: Array,
        default: () => [],
    },
});

const emit = defineEmits([
    'update:modelValue',
    'trigger-avatar-upload',
    'save',
    'authorize-channel',
    'close-channel',
]);

const viewMode = ref('overview');
const channelMenuOpen = ref(false);
const channelMenuRef = ref(null);

const isEditView = computed(() => viewMode.value === 'edit');
const defaultOverviewDescription = computed(() =>
    props.readonly ? '当前内容由系统配置，暂不支持在这里修改。' : '管理助理信息与接入渠道。'
);
const normalizedSkills = computed(() => (Array.isArray(props.skills) ? props.skills : []));
const normalizedTools = computed(() => (Array.isArray(props.tools) ? props.tools : []));
const capabilityTotal = computed(
    () => normalizedSkills.value.length + normalizedTools.value.length
);
const showCapabilities = computed(
    () => props.readonly && (normalizedSkills.value.length > 0 || normalizedTools.value.length > 0)
);

const supportedChannels = [
    {
        type: 'weixin',
        label: '微信',
        logo: CHANNEL_LOGO_SOURCES.weixin,
        description: '连接微信个人号，与用户进行消息交互',
    },
    {
        type: 'wecom',
        label: '企业微信',
        logo: CHANNEL_LOGO_SOURCES.wecom,
        description: '连接企业微信机器人，服务企业成员',
    },
    {
        type: 'dingtalk',
        label: '钉钉',
        logo: CHANNEL_LOGO_SOURCES.dingtalk,
        description: '连接钉钉机器人，为群聊与工作台提供服务',
    },
];

const channelItems = computed(() =>
    supportedChannels.map(channel => {
        const binding = props.boundChannels.find(item => item.channelType === channel.type);
        return {
            ...channel,
            ...(binding || {}),
            bound: Boolean(binding),
            connected: Boolean(binding?.connected),
            statusLabel: binding?.statusLabel || '未连接',
        };
    })
);
watch(
    () => props.readonly,
    () => {
        viewMode.value = 'overview';
        channelMenuOpen.value = false;
    }
);

onMounted(() => {
    document.addEventListener('mousedown', handleDocumentMouseDown, true);
});

onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleDocumentMouseDown, true);
});

function handleDocumentMouseDown(event) {
    if (!channelMenuOpen.value) {
        return;
    }
    const root = channelMenuRef.value;
    if (root && !root.contains(event.target)) {
        channelMenuOpen.value = false;
    }
}

function handleChannelClick(type) {
    channelMenuOpen.value = false;
    emit('authorize-channel', type);
}
</script>

<style scoped>
.agent-settings-scroll.custom-scrollbar {
    scrollbar-gutter: stable;
    scrollbar-width: thin;
    scrollbar-color: rgba(148, 163, 184, 0.72) transparent;
}

.agent-settings-scroll.custom-scrollbar::-webkit-scrollbar {
    display: block;
    width: 8px;
}

.agent-settings-scroll.custom-scrollbar::-webkit-scrollbar-track {
    background: transparent;
}

.agent-settings-scroll.custom-scrollbar::-webkit-scrollbar-thumb {
    border: 2px solid transparent;
    border-radius: 999px;
    background: rgba(148, 163, 184, 0.62);
    background-clip: content-box;
}

.agent-settings-scroll.custom-scrollbar::-webkit-scrollbar-thumb:hover {
    background: rgba(100, 116, 139, 0.78);
    background-clip: content-box;
}
</style>
