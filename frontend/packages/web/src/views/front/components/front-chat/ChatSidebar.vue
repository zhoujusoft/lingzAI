<template>
    <aside class="flex h-full flex-col overflow-hidden bg-transparent">
        <div v-if="collapsed" class="flex h-full flex-col px-4 py-3">
            <div v-if="showToggleButton" class="flex h-[56px] items-center justify-center">
                <button
                    type="button"
                    class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-[#f0f4f9] text-body transition-all duration-150 hover:bg-[#e8edf5] hover:text-strong active:scale-[0.97]"
                    title="展开会话历史"
                    @click="$emit('toggle-sidebar')"
                >
                    <span class="h-5 w-5 bg-current" :style="sidebarExpandIconStyle"></span>
                </button>
            </div>

            <div v-if="showToggleButton" class="h-px bg-[#eef2f6]"></div>

            <div
                v-if="showAssistantSummary"
                class="group relative mx-1 flex h-[72px] items-center justify-center"
            >
                <button
                    type="button"
                    class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-all duration-150"
                    :class="[
                        isNewChatImageIcon
                            ? 'overflow-hidden bg-transparent hover:opacity-90'
                            : 'bg-[#f0f4f9] text-primary text-strong hover:bg-[#e8edf5]',
                    ]"
                    title="新对话"
                    @click="$emit('new-chat')"
                >
                    <img
                        v-if="isNewChatImageIcon"
                        :src="resolvedNewChatIcon"
                        alt="chat assistant icon"
                        class="h-full w-full object-cover"
                    />
                    <span v-else class="material-symbols-outlined text-[18px]">
                        {{ resolvedNewChatIcon }}
                    </span>
                </button>
                <button
                    v-if="showAssistantEditButton"
                    type="button"
                    class="absolute -right-1 top-1/2 -translate-y-1/2 flex h-6 w-6 items-center justify-center rounded-full bg-white text-muted opacity-0 shadow-sm ring-1 ring-inset ring-[#e6edf5] transition-all duration-150 hover:text-strong hover:ring-[#d9e1ea] group-hover:opacity-100"
                    title="编辑助手"
                    @click.stop="$emit('open-assistant-config')"
                >
                    <span class="material-symbols-outlined !text-[14px]">edit</span>
                </button>
            </div>

            <div v-if="showAssistantSummary" class="h-px bg-[#eef2f6]"></div>

            <div class="custom-scrollbar flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto py-3">
                <button
                    v-for="item in compactConversationItems"
                    :key="item.id || item.title"
                    type="button"
                    :title="resolveConversationTooltip(item)"
                    :aria-current="item.active ? 'page' : undefined"
                    :class="[
                        'group flex shrink-0 cursor-pointer flex-col items-start overflow-hidden rounded-lg px-2 py-1.5 transition-all duration-150',
                        item.active ? 'bg-[#eef3fb]' : 'bg-transparent hover:bg-[#f5f7fb]',
                    ]"
                    @click="$emit('select-conversation', item)"
                >
                    <div class="flex w-full min-w-0 items-center gap-1.5">
                        <span
                            v-if="isConversationVisualImage(item)"
                            class="h-4 w-4 shrink-0 overflow-hidden rounded-full"
                            :class="resolveConversationImageFrameClass(item)"
                        >
                            <img
                                :src="resolveConversationVisualValue(item)"
                                alt=""
                                :class="resolveConversationImageClass(item)"
                            />
                        </span>
                        <span
                            v-else-if="isConversationVisualBrand(item)"
                            class="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-[4px] text-[9px] font-bold leading-none text-white"
                            :class="resolveConversationVisualClass(item)"
                        >
                            {{ resolveConversationVisualValue(item) }}
                        </span>
                        <span
                            v-else
                            class="material-symbols-outlined fill-0 shrink-0 text-[14px] leading-none"
                            :class="resolveConversationCompactIconClass(item)"
                        >
                            {{ resolveConversationVisualValue(item) }}
                        </span>
                        <span
                            :class="[
                                'min-w-0 flex-1 truncate text-left text-[11px] font-medium leading-tight',
                                item.active ? 'text-body' : 'text-muted group-hover:text-body',
                            ]"
                        >
                            {{ resolveCollapsedTitle(item) }}
                        </span>
                    </div>
                    <div class="mt-0.5 text-[10px] leading-none text-muted">
                        {{ resolveRelativeTime(item.updatedAt) }}
                    </div>
                </button>

                <div
                    v-if="!compactConversationItems.length"
                    class="flex cursor-default items-center justify-center rounded-xl bg-[#f0f4f9] px-2 py-3 text-muted"
                    title="暂无对话"
                >
                    <span class="material-symbols-outlined fill-0 text-[16px] leading-none">
                        forum
                    </span>
                </div>
            </div>
        </div>

        <div v-else class="flex h-full min-h-0 flex-col px-4 py-3">
            <div class="shrink-0">
                <div v-if="showToggleButton" class="flex h-12 items-center justify-between gap-3">
                    <div class="min-w-0 flex-1">
                        <div class="flex items-center gap-2">
                            <h3 class="truncate text-[17px] font-bold text-strong">会话</h3>
                            <span
                                class="inline-flex h-8 items-center rounded-full bg-[#f3f6fb] px-3 text-[13px] font-bold text-[#40516f]"
                            >
                                {{ resolvedConversationTotal }}
                            </span>
                        </div>
                    </div>
                    <button
                        type="button"
                        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-[18px] bg-[#f3f6fb] text-[#53627b] transition-all hover:bg-[#e8edf5] hover:text-strong active:scale-[0.98]"
                        title="折叠会话历史"
                        @click="$emit('toggle-sidebar')"
                    >
                        <span class="h-5 w-5 bg-current" :style="sidebarCollapseIconStyle"></span>
                    </button>
                </div>

                <div v-if="showToggleButton" class="mt-3 h-px bg-[#eef2f6]"></div>

                <div
                    v-if="showAssistantSummary"
                    class="group relative flex h-[72px] min-w-0 w-full items-center gap-3 rounded-[18px]"
                >
                    <button
                        type="button"
                        class="flex min-w-0 flex-1 items-center gap-3 rounded-[18px] px-2.5 py-2 text-left transition-all duration-150 hover:bg-[#f8fafc]"
                        title="新对话"
                        @click="$emit('new-chat')"
                    >
                        <div
                            :class="[
                                'flex h-10 w-10 shrink-0 items-center justify-center rounded-full',
                                isNewChatImageIcon
                                    ? 'overflow-hidden bg-transparent'
                                    : 'bg-[#f0f4f9] text-primary text-strong',
                            ]"
                        >
                            <img
                                v-if="isNewChatImageIcon"
                                :src="resolvedNewChatIcon"
                                alt="chat assistant icon"
                                class="h-full w-full object-cover"
                            />
                            <span v-else class="material-symbols-outlined text-[18px]">
                                {{ resolvedNewChatIcon }}
                            </span>
                        </div>
                        <div class="min-w-0 flex-1">
                            <h2 class="truncate text-base font-semibold leading-tight text-strong">
                                {{ resolvedNewChatLabel }}
                            </h2>
                        </div>
                    </button>
                    <button
                        v-if="showAssistantEditButton"
                        type="button"
                        class="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white text-strong opacity-0 shadow-[0_8px_16px_-4px_rgba(15,23,42,0.12),0_4px_8px_-2px_rgba(15,23,42,0.06)] transition-all duration-200 group-hover:opacity-100 hover:scale-110 hover:text-primary active:scale-95"
                        title="编辑助手"
                        @click.stop="$emit('open-assistant-config')"
                    >
                        <span class="material-symbols-outlined !text-[16px]">edit</span>
                    </button>
                </div>

                <div v-if="showAssistantSummary" class="h-px bg-[#eef2f6]"></div>

                <div class="mt-4 flex items-center gap-2">
                    <label class="relative min-w-0 flex-1">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 fill-0 text-[20px] text-[#8c9bb2]"
                        >
                            search
                        </span>
                        <input
                            type="text"
                            :value="searchKeyword"
                            placeholder="搜索会话"
                            class="h-12 w-full rounded-[16px] border border-transparent bg-[#f6f8fb] py-2 pl-12 pr-4 text-[14px] font-semibold text-strong outline-none transition placeholder:text-[#9ba8bc] focus:border-[#dfe7f1] focus:bg-white focus:ring-0"
                            @input="$emit('update:searchKeyword', $event.target.value)"
                        />
                    </label>

                    <div v-if="showKnowledgeSelect" class="shrink-0">
                        <AppSelect
                            :model-value="selectedKnowledge"
                            :options="normalizedKnowledgeOptions"
                            placeholder="暂无知识库"
                            size="sm"
                            :full-width="false"
                            leading-icon="tune"
                            button-class="!h-12 !min-w-12 !w-12 !rounded-[16px] !border-transparent !bg-[#f6f8fb] !px-0 !pr-0 !shadow-none [&_.material-symbols-outlined:last-child]:hidden [&_span.truncate]:hidden"
                            menu-class="right-0 left-auto min-w-[180px]"
                            @update:model-value="$emit('update:selectedKnowledge', $event)"
                        />
                    </div>
                </div>
            </div>

            <div class="flex min-h-0 flex-1 flex-col pt-5">
                <div
                    ref="conversationListRef"
                    class="custom-scrollbar flex-1 space-y-4 overflow-y-auto pr-1"
                    @scroll="handleConversationListScroll"
                >
                    <div
                        v-for="group in groupedConversationItems"
                        :key="group.key"
                        class="space-y-1"
                    >
                        <div
                            class="sticky top-0 z-10 bg-white/90 px-1 py-1 text-[14px] font-bold leading-6 text-[#65738b] backdrop-blur"
                        >
                            {{ group.label }}
                        </div>

                        <div
                            v-for="item in group.items"
                            :key="item.id || item.title"
                            class="group relative"
                        >
                            <div
                                v-if="isEditing(item.id)"
                                class="rounded-[14px] bg-[#f8fafc] px-2 py-2 shadow-none"
                            >
                                <div class="flex items-start gap-2.5">
                                    <div
                                        class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white text-[11px] font-semibold text-body"
                                        :class="resolveConversationEditingVisualFrameClass(item)"
                                    >
                                        <img
                                            v-if="isConversationVisualImage(item)"
                                            :src="resolveConversationVisualValue(item)"
                                            alt=""
                                            :class="resolveConversationImageClass(item)"
                                        />
                                        <span
                                            v-else-if="isConversationVisualBrand(item)"
                                            class="inline-flex h-5 w-5 items-center justify-center rounded-[6px] text-[10px] font-bold leading-none text-white"
                                            :class="resolveConversationVisualClass(item)"
                                        >
                                            {{ resolveConversationVisualValue(item) }}
                                        </span>
                                        <span
                                            v-else
                                            class="material-symbols-outlined fill-0 text-[17px]"
                                        >
                                            {{ resolveConversationVisualValue(item) }}
                                        </span>
                                    </div>
                                    <div class="min-w-0 flex-1">
                                        <input
                                            ref="editingInputRef"
                                            :value="editingConversationName"
                                            type="text"
                                            class="w-full rounded-[14px] border border-border-soft/80 bg-white px-3 py-2 text-sm font-medium text-strong outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                                            @input="editingConversationName = $event.target.value"
                                            @keydown.enter.prevent="commitInlineRename(item)"
                                            @keydown.esc.prevent="cancelInlineRename"
                                            @blur="commitInlineRename(item)"
                                        />
                                        <div class="mt-1 truncate text-[11px] leading-5 text-body">
                                            {{ resolveConversationPreview(item) }}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <button
                                v-else
                                type="button"
                                class="relative flex h-11 w-full items-center gap-2.5 overflow-hidden rounded-[14px] px-1.5 text-left transition-all duration-150"
                                :class="
                                    item.active
                                        ? 'bg-[#f4f7fb] shadow-none'
                                        : 'bg-transparent hover:bg-[#f8fafc]'
                                "
                                :title="resolveConversationTooltip(item)"
                                @click="$emit('select-conversation', item)"
                            >
                                <div
                                    class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold transition-colors"
                                    :class="resolveConversationVisualFrameClass(item)"
                                >
                                    <img
                                        v-if="isConversationVisualImage(item)"
                                        :src="resolveConversationVisualValue(item)"
                                        alt=""
                                        :class="resolveConversationImageClass(item)"
                                    />
                                    <span
                                        v-else-if="isConversationVisualBrand(item)"
                                        class="inline-flex h-5 w-5 items-center justify-center rounded-[6px] text-[10px] font-bold leading-none text-white"
                                        :class="resolveConversationVisualClass(item)"
                                    >
                                        {{ resolveConversationVisualValue(item) }}
                                    </span>
                                    <span
                                        v-else
                                        class="material-symbols-outlined fill-0 text-[17px]"
                                    >
                                        {{ resolveConversationVisualValue(item) }}
                                    </span>
                                </div>

                                <div class="min-w-0 flex-1">
                                    <div
                                        class="truncate text-[14px] leading-5"
                                        :class="
                                            item.active
                                                ? 'font-bold text-[#40516f]'
                                                : 'font-bold text-[#4d5b73] group-hover:text-[#2f3b52]'
                                        "
                                        @dblclick.stop.prevent="startInlineRename(item)"
                                    >
                                        {{ resolveConversationTitle(item) }}
                                    </div>
                                </div>

                                <span
                                    class="shrink-0 pl-2 text-right text-[13px] font-semibold text-[#7d8aa2]"
                                >
                                    {{ resolveConversationListTime(item.updatedAt) }}
                                </span>
                            </button>

                            <button
                                v-if="!isEditing(item.id)"
                                type="button"
                                class="absolute right-12 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white text-strong opacity-0 shadow-[0_8px_16px_-4px_rgba(15,23,42,0.12),0_4px_8px_-2px_rgba(15,23,42,0.06)] transition-all duration-200 group-hover:opacity-100 hover:scale-110 hover:text-primary active:scale-95"
                                :class="{ 'opacity-100': isMenuOpenFor(item.id) }"
                                title="更多"
                                @click.stop="toggleConversationMenu(item)"
                            >
                                <span
                                    class="material-symbols-outlined fill-0 text-[18px] leading-none"
                                    >more_horiz</span
                                >
                            </button>

                            <div
                                v-if="isMenuOpenFor(item.id)"
                                class="absolute right-2 top-11 z-20 w-32 rounded-2xl border border-[#e6edf5] bg-white p-1.5 shadow-[0_18px_36px_-28px_rgba(15,23,42,0.18)] backdrop-blur-xl"
                            >
                                <button
                                    type="button"
                                    class="flex w-full items-center justify-center rounded-xl px-2 py-2 text-xs font-medium text-body transition hover:bg-surface-alt disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="isRenaming(item.id)"
                                    @click.stop="handleRenameConversation(item)"
                                >
                                    {{ isRenaming(item.id) ? '保存中...' : '重命名' }}
                                </button>
                                <button
                                    type="button"
                                    class="mt-1 flex w-full items-center justify-center rounded-xl px-2 py-2 text-xs font-medium text-danger transition hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="isDeleting(item.id)"
                                    @click.stop="handleDeleteConversation(item)"
                                >
                                    {{ isDeleting(item.id) ? '删除中...' : '删除' }}
                                </button>
                            </div>
                        </div>
                    </div>

                    <div
                        v-if="!conversationItems.length"
                        class="mt-6 flex flex-col items-center justify-center rounded-[18px] bg-[#fafbfd] px-4 py-8 text-center shadow-none ring-1 ring-inset ring-[#edf2f7]"
                    >
                        <div
                            class="flex h-10 w-10 items-center justify-center rounded-full bg-white text-muted ring-1 ring-inset ring-[#edf2f7]"
                        >
                            <span class="material-symbols-outlined fill-0 text-[18px]">forum</span>
                        </div>
                        <p class="mt-3 text-sm font-medium text-body">暂无匹配对话</p>
                        <p class="mt-1 text-xs leading-5 text-muted">
                            从上方新建对话或调整搜索关键词。
                        </p>
                    </div>

                    <div
                        v-else-if="loadingMoreConversations || hasMoreConversations"
                        class="flex justify-center px-2 py-3"
                    >
                        <div
                            class="inline-flex h-8 items-center gap-2 rounded-full border border-border-soft/70 bg-surface-alt/80 px-3 text-xs font-medium text-muted"
                        >
                            <span
                                v-if="loadingMoreConversations"
                                class="h-3 w-3 animate-spin rounded-full border-2 border-primary/15 border-t-primary"
                            ></span>
                            <span>{{
                                loadingMoreConversations ? '正在加载更多会话' : '下滑加载更多会话'
                            }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </aside>
</template>

<script>
import AppSelect from '@/components/AppSelect.vue';
import sidebarCollapseIcon from '@/assets/images/sidebar-collapse.svg';
import sidebarExpandIcon from '@/assets/images/sidebar-expand.svg';
import { resolveChannelLogo } from '@/utils/channelVisuals';
import { isImageSource } from '@/utils/iconDisplay';
import { getSkillIconGradientClass, resolveSkillIcon } from '@/utils/skillVisuals';

export default {
    name: 'ChatSidebar',
    components: {
        AppSelect,
    },
    emits: [
        'update:selectedKnowledge',
        'update:searchKeyword',
        'toggle-sidebar',
        'open-assistant-config',
        'new-chat',
        'delete-conversation',
        'rename-conversation',
        'select-conversation',
        'load-more-conversations',
    ],
    props: {
        collapsed: {
            type: Boolean,
            default: false,
        },
        showToggleButton: {
            type: Boolean,
            default: true,
        },
        showAssistantSummary: {
            type: Boolean,
            default: false,
        },
        assistantTitle: {
            type: String,
            default: '',
        },
        assistantStatusText: {
            type: String,
            default: '',
        },
        assistantStatusTone: {
            type: String,
            default: 'success',
        },
        assistantIcon: {
            type: String,
            default: 'description',
        },
        newChatLabel: {
            type: String,
            default: '',
        },
        newChatIcon: {
            type: String,
            default: '',
        },
        agentIcon: {
            type: String,
            default: '',
        },
        showAssistantEditButton: {
            type: Boolean,
            default: true,
        },
        showKnowledgeSelect: {
            type: Boolean,
            default: false,
        },
        selectedKnowledge: {
            type: String,
            default: '',
        },
        searchKeyword: {
            type: String,
            default: '',
        },
        knowledgeOptions: {
            type: Array,
            default: () => [],
        },
        selectLabel: {
            type: String,
            default: '知识库选择',
        },
        conversationItems: {
            type: Array,
            default: () => [],
        },
        conversationTotal: {
            type: Number,
            default: 0,
        },
        hasMoreConversations: {
            type: Boolean,
            default: false,
        },
        loadingMoreConversations: {
            type: Boolean,
            default: false,
        },
        deletingConversationIds: {
            type: Array,
            default: () => [],
        },
        renamingConversationIds: {
            type: Array,
            default: () => [],
        },
    },
    data() {
        return {
            openedConversationMenuId: null,
            editingConversationId: null,
            editingConversationName: '',
            sidebarCollapseIcon,
            sidebarExpandIcon,
        };
    },
    computed: {
        isAssistantImageIcon() {
            return isImageSource(this.assistantIcon);
        },
        resolvedNewChatLabel() {
            return String(this.newChatLabel || this.assistantTitle || '新对话').trim();
        },
        resolvedNewChatIcon() {
            return String(this.newChatIcon || this.assistantIcon || 'add').trim();
        },
        effectiveAssistantStatusText() {
            return String(this.assistantStatusText || '').trim();
        },
        isNewChatImageIcon() {
            return isImageSource(this.resolvedNewChatIcon);
        },
        normalizedKnowledgeOptions() {
            return this.knowledgeOptions
                .map(option => this.normalizeKnowledgeOption(option))
                .filter(option => option.value);
        },
        compactConversationItems() {
            const items = Array.isArray(this.conversationItems) ? this.conversationItems : [];
            const limit = 8;
            if (items.length <= limit) {
                return items;
            }

            const activeIndex = items.findIndex(item => item?.active);
            if (activeIndex < 0 || activeIndex < limit) {
                return items.slice(0, limit);
            }

            return [...items.slice(0, limit - 1), items[activeIndex]];
        },
        groupedConversationItems() {
            const items = Array.isArray(this.conversationItems) ? this.conversationItems : [];
            const groupMap = new Map();

            items.forEach(item => {
                const group = this.resolveConversationDateGroup(item);
                if (!groupMap.has(group.key)) {
                    groupMap.set(group.key, {
                        ...group,
                        items: [],
                    });
                }
                groupMap.get(group.key).items.push(item);
            });

            return Array.from(groupMap.values()).sort((left, right) => left.order - right.order);
        },
        resolvedConversationTotal() {
            const total = Number(this.conversationTotal || 0);
            return total > 0 ? total : this.conversationItems.length;
        },
        sidebarCollapseIconStyle() {
            return this.resolveMaskIconStyle(this.sidebarCollapseIcon);
        },
        sidebarExpandIconStyle() {
            return this.resolveMaskIconStyle(this.sidebarExpandIcon);
        },
    },
    watch: {
        collapsed(value) {
            if (value) {
                this.openedConversationMenuId = null;
                this.cancelInlineRename();
            }
        },
    },
    mounted() {
        document.addEventListener('click', this.handleClickOutside, true);
    },
    beforeUnmount() {
        document.removeEventListener('click', this.handleClickOutside, true);
    },
    methods: {
        resolveMaskIconStyle(iconUrl) {
            return {
                WebkitMaskImage: `url(${iconUrl})`,
                maskImage: `url(${iconUrl})`,
                WebkitMaskRepeat: 'no-repeat',
                maskRepeat: 'no-repeat',
                WebkitMaskPosition: 'center',
                maskPosition: 'center',
                WebkitMaskSize: 'contain',
                maskSize: 'contain',
            };
        },
        toggleConversationMenu(item) {
            const itemId = item?.id ?? null;
            if (itemId === null) {
                return;
            }
            this.openedConversationMenuId =
                this.openedConversationMenuId === itemId ? null : itemId;
        },
        isMenuOpenFor(itemId) {
            return (
                itemId !== null && itemId !== undefined && this.openedConversationMenuId === itemId
            );
        },
        isEditing(itemId) {
            return itemId !== null && itemId !== undefined && this.editingConversationId === itemId;
        },
        isDeleting(itemId) {
            return this.deletingConversationIds.includes(itemId);
        },
        isRenaming(itemId) {
            return this.renamingConversationIds.includes(itemId);
        },
        startInlineRename(item) {
            const itemId = item?.id ?? null;
            if (itemId === null) {
                return;
            }
            this.openedConversationMenuId = null;
            this.editingConversationId = itemId;
            this.editingConversationName = String(item?.name || item?.title || '').trim();
            this.$nextTick(() => {
                const refValue = this.$refs.editingInputRef;
                const input = Array.isArray(refValue) ? refValue[0] : refValue;
                if (input && typeof input.focus === 'function') {
                    input.focus();
                    if (typeof input.select === 'function') {
                        input.select();
                    }
                }
            });
        },
        cancelInlineRename() {
            this.editingConversationId = null;
            this.editingConversationName = '';
        },
        commitInlineRename(item) {
            const currentName = String(item?.name || item?.title || '').trim();
            const nextName = String(this.editingConversationName || '').trim();
            if (!nextName || nextName === currentName) {
                this.cancelInlineRename();
                return;
            }
            const payload = {
                item,
                name: nextName,
                inline: true,
            };
            this.cancelInlineRename();
            this.$emit('rename-conversation', payload);
        },
        handleRenameConversation(item) {
            this.openedConversationMenuId = null;
            this.$emit('rename-conversation', item);
        },
        handleDeleteConversation(item) {
            this.openedConversationMenuId = null;
            this.$emit('delete-conversation', item);
        },
        handleClickOutside(event) {
            if (!this.openedConversationMenuId) {
                return;
            }
            const root = this.$refs.conversationListRef;
            if (root && !root.contains(event.target)) {
                this.openedConversationMenuId = null;
            }
        },
        handleConversationListScroll(event) {
            const node = event?.target || this.$refs.conversationListRef;
            if (!node || this.loadingMoreConversations || !this.hasMoreConversations) {
                return;
            }
            const threshold = 80;
            const nearBottom = node.scrollTop + node.clientHeight >= node.scrollHeight - threshold;
            if (nearBottom) {
                this.$emit('load-more-conversations');
            }
        },
        normalizeKnowledgeOption(option) {
            if (option && typeof option === 'object') {
                const rawValue = option.value ?? option.id ?? option.kbId ?? '';
                const rawLabel = option.label ?? option.name ?? option.kbName ?? rawValue;
                return {
                    value: String(rawValue ?? '').trim(),
                    label: String(rawLabel ?? '').trim(),
                };
            }
            const text = String(option ?? '').trim();
            return {
                value: text,
                label: text,
            };
        },
        resolveConversationPreview(item) {
            const preview = String(item?.lastMessage || '').trim();
            if (preview) {
                return preview;
            }
            return item?.active ? '当前会话已就绪，继续输入内容。' : '继续当前会话';
        },
        resolveConversationTypeLine(item) {
            return String(item?.sessionTypeLabel || '').trim() || '对话';
        },
        resolveConversationTitle(item) {
            const subtitle = String(item?.subtitle || '').trim();
            if (subtitle) {
                return subtitle;
            }
            const sourceLabel = String(item?.scopeDisplayName || item?.sourceLabel || '').trim();
            const summary = String(item?.titleSummary || item?.name || item?.title || '').trim();
            if (sourceLabel && summary && sourceLabel !== summary) {
                return `${sourceLabel} · ${summary}`;
            }
            if (summary) {
                return summary;
            }
            return this.resolveConversationPreview(item);
        },
        resolveConversationTooltip(item) {
            const header = this.resolveConversationTitle(item);
            const subtitle = this.resolveConversationTypeLine(item);
            return [header, subtitle].filter(Boolean).join(' / ');
        },
        resolveConversationVisual(item) {
            const sessionType = String(item?.sessionType || '')
                .trim()
                .toUpperCase();
            const sourceType = String(item?.sourceType || '')
                .trim()
                .toUpperCase();
            if (sessionType === 'GENERAL_CHAT' || sessionType === 'GENERAL_CHAT_V2') {
                const value = String(
                    this.agentIcon || this.assistantIcon || this.resolvedNewChatIcon || ''
                ).trim();
                return {
                    type: isImageSource(value) ? 'image' : 'icon',
                    value: value || 'smart_toy',
                    className: '',
                };
            }
            if (sessionType === 'CHANNEL_CHAT' || sourceType === 'CHANNEL') {
                return this.resolveChannelVisual(item);
            }
            if (sessionType === 'SKILL_CHAT' || sessionType === 'PUBLISHED_SKILL_CHAT') {
                return {
                    type: 'icon',
                    value: resolveSkillIcon(item?.sourceIcon),
                    className: getSkillIconGradientClass(item?.sourceIconColor),
                };
            }
            if (
                sessionType === 'EXPERT_SKILL_PACKAGE_CHAT' ||
                sourceType === 'EXPERT_SKILL_PACKAGE'
            ) {
                const icon = String(item?.sourceIcon || '').trim();
                return {
                    type: 'icon',
                    value: icon || 'psychology',
                    className: 'bg-primary',
                };
            }
            return {
                type: 'icon',
                value: this.resolveConversationIcon(item),
                className: '',
            };
        },
        resolveChannelVisual(item) {
            const channelLogo = resolveChannelLogo(item?.channelType, item?.sourceLabel);
            if (channelLogo) {
                return { type: 'channel-image', value: channelLogo, className: '' };
            }
            const type = String(item?.channelType || item?.sourceLabel || '')
                .trim()
                .toLowerCase();
            if (type === 'wecom' || type === 'wechat_work' || type === 'work_wechat') {
                return { type: 'brand', value: '企', className: 'bg-[#1AAD19]' };
            }
            if (type === 'dingtalk' || type === 'dingding') {
                return { type: 'brand', value: '钉', className: 'bg-[#1677ff]' };
            }
            if (type === 'weixin' || type === 'wechat') {
                return { type: 'brand', value: '微', className: 'bg-[#07c160]' };
            }
            if (type === 'webchat') {
                return { type: 'icon', value: 'chat', className: '' };
            }
            return { type: 'icon', value: 'hub', className: '' };
        },
        resolveConversationVisualValue(item) {
            return this.resolveConversationVisual(item).value;
        },
        resolveConversationVisualClass(item) {
            return this.resolveConversationVisual(item).className;
        },
        isConversationVisualImage(item) {
            const type = this.resolveConversationVisual(item).type;
            return type === 'image' || type === 'channel-image';
        },
        isConversationVisualChannelImage(item) {
            return this.resolveConversationVisual(item).type === 'channel-image';
        },
        isConversationVisualBrand(item) {
            return this.resolveConversationVisual(item).type === 'brand';
        },
        resolveConversationImageFrameClass(item) {
            if (this.isConversationVisualChannelImage(item)) {
                return '!rounded-none bg-transparent';
            }
            return '';
        },
        resolveConversationImageClass(item) {
            if (this.isConversationVisualChannelImage(item)) {
                return 'h-full w-full object-contain';
            }
            return 'h-full w-full rounded-full object-cover';
        },
        isConversationVisualDecoratedIcon(item) {
            const visual = this.resolveConversationVisual(item);
            return visual.type === 'icon' && Boolean(visual.className);
        },
        resolveConversationCompactIconClass(item) {
            const className = this.resolveConversationVisualClass(item);
            if (!className) {
                return item.active ? 'text-primary' : 'text-muted group-hover:text-body';
            }
            return [
                className,
                'inline-flex h-4 w-4 items-center justify-center rounded-full text-white',
            ];
        },
        resolveConversationVisualFrameClass(item) {
            const className = this.resolveConversationVisualClass(item);
            if (this.isConversationVisualChannelImage(item)) {
                return 'bg-transparent text-body';
            }
            if (this.isConversationVisualDecoratedIcon(item)) {
                return [className, 'text-white'];
            }
            return item.active
                ? 'bg-white text-primary'
                : 'bg-[#f7f9fc] text-muted group-hover:bg-white group-hover:text-body';
        },
        resolveConversationEditingVisualFrameClass(item) {
            const className = this.resolveConversationVisualClass(item);
            if (this.isConversationVisualChannelImage(item)) {
                return '!bg-transparent';
            }
            if (this.isConversationVisualDecoratedIcon(item)) {
                return [className, '!bg-transparent text-white'];
            }
            return '';
        },
        resolveConversationIcon(item) {
            const sessionType = String(item?.sessionType || '')
                .trim()
                .toUpperCase();
            const sourceType = String(item?.sourceType || '')
                .trim()
                .toUpperCase();
            if (sessionType === 'GENERAL_CHAT') {
                return 'bolt';
            }
            if (sessionType === 'GENERAL_CHAT_V2') {
                return 'task_alt';
            }
            if (sessionType === 'CHANNEL_CHAT' || sourceType === 'CHANNEL') {
                return 'hub';
            }
            if (sessionType === 'SKILL_CHAT' || sessionType === 'PUBLISHED_SKILL_CHAT') {
                return 'auto_awesome';
            }
            if (sessionType === 'DATASET_CHAT' || sourceType === 'DATASET') {
                return 'database';
            }
            if (sessionType === 'KNOWLEDGE_QA') {
                return 'menu_book';
            }
            if (
                sessionType === 'SKILL_STUDIO_PROJECT_CHAT' ||
                sessionType === 'SKILL_STUDIO_PROJECT_PREVIEW_CHAT'
            ) {
                return 'construction';
            }
            return 'forum';
        },
        resolveConversationMonogram(item) {
            const source = String(
                item?.sourceLabel ||
                    item?.scopeDisplayName ||
                    item?.titleSummary ||
                    item?.title ||
                    item?.name ||
                    ''
            ).trim();
            if (!source) {
                return '会';
            }

            const normalized = source.replace(/\s+/g, ' ');
            const firstChar = normalized[0];
            if (/[\u4e00-\u9fff]/.test(firstChar)) {
                return firstChar;
            }

            const initials = normalized
                .split(' ')
                .filter(Boolean)
                .slice(0, 2)
                .map(part => part[0])
                .join('')
                .toUpperCase();

            return initials || normalized.slice(0, 2).toUpperCase();
        },
        resolveCollapsedTitle(item) {
            const source = String(item?.titleSummary || item?.name || item?.title || '').trim();
            if (!source) {
                return '新对话';
            }
            // Show up to 6 characters for Chinese, 8 for English
            const maxLen = /[\u4e00-\u9fff]/.test(source[0]) ? 6 : 8;
            if (source.length <= maxLen) {
                return source;
            }
            return source.slice(0, maxLen) + '…';
        },
        resolveConversationDateGroup(item) {
            const date = this.parseConversationDate(item?.updatedAt);
            if (!date) {
                return {
                    key: 'unknown',
                    label: '未标记时间',
                    order: 999,
                };
            }

            const today = new Date();
            today.setHours(0, 0, 0, 0);
            const target = new Date(date);
            target.setHours(0, 0, 0, 0);
            const diffDays = Math.floor((today.getTime() - target.getTime()) / 86400000);

            if (diffDays <= 0) {
                return {
                    key: 'today',
                    label: '今天',
                    order: 0,
                };
            }
            if (diffDays === 1) {
                return {
                    key: 'yesterday',
                    label: '昨天',
                    order: 1,
                };
            }
            if (diffDays < 7) {
                return {
                    key: 'last-7-days',
                    label: '近 7 天',
                    order: 2,
                };
            }
            if (diffDays < 30) {
                return {
                    key: 'last-30-days',
                    label: '近 30 天',
                    order: 3,
                };
            }

            return {
                key: 'older',
                label: '更早',
                order: 4,
            };
        },
        parseConversationDate(value) {
            if (!value) {
                return null;
            }
            const date = new Date(value);
            if (isNaN(date.getTime())) {
                return null;
            }
            return date;
        },
        resolveRelativeTime(updatedAt) {
            if (!updatedAt) {
                return '';
            }
            try {
                const date = this.parseConversationDate(updatedAt);
                if (!date) {
                    return '';
                }
                const now = new Date();
                const diffMs = now.getTime() - date.getTime();
                const diffMins = Math.floor(diffMs / 60000);
                const diffHours = Math.floor(diffMs / 3600000);
                const diffDays = Math.floor(diffMs / 86400000);

                if (diffMins < 1) {
                    return '刚刚';
                }
                if (diffMins < 60) {
                    return diffMins + '分';
                }
                if (diffHours < 24) {
                    return diffHours + 'h';
                }
                if (diffDays === 1) {
                    return '昨天';
                }
                if (diffDays < 7) {
                    return diffDays + '天';
                }
                // Show date for older items
                const month = date.getMonth() + 1;
                const day = date.getDate();
                return month + '/' + day;
            } catch (e) {
                return '';
            }
        },
        resolveConversationListTime(updatedAt) {
            const date = this.parseConversationDate(updatedAt);
            if (!date) {
                return '';
            }

            const today = new Date();
            today.setHours(0, 0, 0, 0);
            const target = new Date(date);
            target.setHours(0, 0, 0, 0);
            const diffDays = Math.floor((today.getTime() - target.getTime()) / 86400000);

            if (diffDays <= 1) {
                const hours = String(date.getHours()).padStart(2, '0');
                const minutes = String(date.getMinutes()).padStart(2, '0');
                return `${hours}:${minutes}`;
            }

            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${month}-${day}`;
        },
    },
};
</script>
