<template>
    <aside class="flex h-full flex-col bg-slate-50/50">
        <div class="flex h-full flex-col p-2">
            <button
                type="button"
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 shadow-sm transition hover:bg-slate-50 hover:text-slate-700"
                :class="collapsed ? 'self-center' : 'self-end'"
                :title="collapsed ? '展开对话历史和搜索' : '折叠对话历史和搜索'"
                @click="$emit('toggle-sidebar')"
            >
                <span
                    class="material-symbols-outlined fill-0 text-xl leading-none transition-transform"
                    :class="collapsed ? '' : 'rotate-180'"
                >
                    side_navigation
                </span>
            </button>

            <button
                type="button"
                class="mt-3 flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white text-slate-700 shadow-sm transition hover:bg-slate-50"
                :class="collapsed ? 'w-10 self-center' : 'w-full px-3 text-sm font-semibold'"
                title="新对话"
                @click="$emit('new-chat')"
            >
                <span class="material-symbols-outlined text-lg">add_comment</span>
                <span v-if="!collapsed">新对话</span>
            </button>

            <div v-if="!collapsed" class="relative mt-4">
                <span
                    class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-lg"
                    >search</span
                >
                <input
                    type="text"
                    :value="searchKeyword"
                    placeholder="搜索对话内容..."
                    class="w-full rounded-lg border border-slate-200 bg-white py-2 pl-10 pr-4 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary"
                    @input="$emit('update:searchKeyword', $event.target.value)"
                />
            </div>

            <div v-if="showKnowledgeSelect && !collapsed" class="mt-4 space-y-1">
                <label
                    class="px-1 text-[10px] font-bold uppercase tracking-widest text-slate-400"
                    >{{ selectLabel }}</label
                >
                <AppSelect
                    :model-value="selectedKnowledge"
                    :options="normalizedKnowledgeOptions"
                    placeholder="暂无知识库"
                    size="sm"
                    button-class="shadow-none"
                    @update:model-value="$emit('update:selectedKnowledge', $event)"
                />
            </div>

            <div v-if="!collapsed" class="mt-6 flex min-h-0 flex-1 flex-col">
                <div class="mb-4 flex items-center justify-between px-2">
                    <span class="text-xs font-bold uppercase tracking-widest text-slate-400">最近对话</span>
                    <button type="button" class="text-slate-400 hover:text-slate-600">
                        <span class="material-symbols-outlined text-lg">tune</span>
                    </button>
                </div>
                <div ref="conversationListRef" class="custom-scrollbar flex-1 space-y-1 overflow-y-auto pr-1">
                    <div
                        v-for="item in conversationItems"
                        :key="item.id || item.title"
                        class="group relative"
                    >
                        <div
                            v-if="isEditing(item.id)"
                            class="flex w-full items-start gap-3 rounded-xl border border-slate-100 bg-white px-3 py-3"
                        >
                            <div class="min-w-0 flex-1">
                                <input
                                    ref="editingInputRef"
                                    :value="editingConversationName"
                                    type="text"
                                    class="w-full rounded-lg border border-slate-200 bg-white px-2 py-1 text-sm font-medium text-slate-700 outline-none transition focus:border-primary focus:ring-1 focus:ring-primary"
                                    @input="editingConversationName = $event.target.value"
                                    @keydown.enter.prevent="commitInlineRename(item)"
                                    @keydown.esc.prevent="cancelInlineRename"
                                    @blur="commitInlineRename(item)"
                                />
                                <div class="mt-1 flex min-w-0 items-center gap-2 text-[11px] text-slate-400">
                                    <span v-if="item.lastMessage" class="min-w-0 flex-1 truncate">
                                        {{ item.lastMessage }}
                                    </span>
                                </div>
                            </div>
                        </div>
                        <button
                            v-else
                            type="button"
                            class="flex w-full items-start gap-3 rounded-xl border py-3 pl-3 pr-11 text-left transition-colors duration-150"
                            :class="
                                item.active
                                    ? 'border-slate-100 bg-white'
                                    : 'border-transparent hover:bg-white'
                            "
                            :title="item.title || ''"
                            @click="$emit('select-conversation', item)"
                        >
                            <div class="min-w-0 flex-1">
                                <div
                                    class="truncate text-sm"
                                    :class="item.active ? 'font-medium text-primary' : 'text-slate-700'"
                                    @dblclick.stop.prevent="startInlineRename(item)"
                                >
                                    {{ item.title }}
                                </div>
                                <div class="mt-1 flex min-w-0 items-center gap-2 text-[11px] text-slate-400">
                                    <span v-if="item.lastMessage" class="min-w-0 flex-1 truncate">
                                        {{ item.lastMessage }}
                                    </span>
                                </div>
                            </div>
                        </button>
                        <button
                            v-if="!isEditing(item.id)"
                            type="button"
                            class="absolute right-2 top-3 flex h-7 w-7 items-center justify-center rounded-md text-slate-400 opacity-0 transition hover:bg-slate-100 hover:text-slate-600 group-hover:opacity-100"
                            :class="isMenuOpenFor(item.id) ? 'opacity-100 bg-slate-100 text-slate-600' : ''"
                            title="更多"
                            @click.stop="toggleConversationMenu(item)"
                        >
                            <span class="material-symbols-outlined text-sm leading-none">more_horiz</span>
                        </button>

                        <div
                            v-if="isMenuOpenFor(item.id)"
                            class="absolute right-2 top-10 z-20 w-32 rounded-lg border border-slate-200 bg-white p-1 shadow-lg"
                        >
                            <button
                                type="button"
                                class="flex w-full items-center justify-center rounded-md px-2 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="isRenaming(item.id)"
                                @click.stop="handleRenameConversation(item)"
                            >
                                {{ isRenaming(item.id) ? '保存中...' : '重命名' }}
                            </button>
                            <button
                                type="button"
                                class="flex w-full items-center justify-center rounded-md px-2 py-1.5 text-xs font-medium text-rose-500 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="isDeleting(item.id)"
                                @click.stop="handleDeleteConversation(item)"
                            >
                                {{ isDeleting(item.id) ? '删除中...' : '删除' }}
                            </button>
                        </div>
                    </div>
                    <p
                        v-if="!conversationItems.length"
                        class="px-3 py-4 text-center text-xs text-slate-400"
                    >
                        暂无匹配对话
                    </p>
                </div>
            </div>
        </div>
    </aside>
</template>

<script>
import AppSelect from '@/components/AppSelect.vue';

export default {
    name: 'ChatSidebar',
    components: {
        AppSelect,
    },
    emits: [
        'update:selectedKnowledge',
        'update:searchKeyword',
        'toggle-sidebar',
        'new-chat',
        'delete-conversation',
        'rename-conversation',
        'select-conversation',
    ],
    props: {
        collapsed: {
            type: Boolean,
            default: false,
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
        };
    },
    computed: {
        normalizedKnowledgeOptions() {
            return this.knowledgeOptions
                .map(option => this.normalizeKnowledgeOption(option))
                .filter(option => option.value);
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
        toggleConversationMenu(item) {
            const itemId = item?.id ?? null;
            if (itemId === null) {
                return;
            }
            this.openedConversationMenuId =
                this.openedConversationMenuId === itemId ? null : itemId;
        },
        isMenuOpenFor(itemId) {
            return itemId !== null && itemId !== undefined && this.openedConversationMenuId === itemId;
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
    },
};
</script>
