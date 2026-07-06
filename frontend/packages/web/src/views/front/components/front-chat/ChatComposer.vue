<template>
    <div class="shrink-0 px-2 pb-5 pt-3 sm:px-3">
        <form
            class="chat-input-container relative w-full transition-all duration-300"
            @submit.prevent="$emit('submit')"
        >
            <!-- 已上传文件预览 -->
            <div v-if="showActions && pendingFiles.length" class="flex flex-wrap gap-2 px-4 pt-4">
                <div
                    v-for="file in pendingFiles"
                    :key="file.id"
                    class="inline-flex items-center gap-2 rounded-xl border px-2.5 py-1.5 text-xs"
                    :class="
                        file.status === 'uploading'
                            ? 'border-amber-200 bg-amber-50 text-amber-700'
                            : file.status === 'error'
                              ? 'border-rose-200 bg-rose-50 text-rose-700'
                              : 'border-blue-200 bg-blue-50 text-blue-700'
                    "
                >
                    <span
                        class="material-symbols-outlined text-sm"
                        :class="
                            file.status === 'uploading'
                                ? 'animate-spin text-amber-500'
                                : file.status === 'error'
                                  ? 'text-rose-500'
                                  : 'text-blue-500'
                        "
                    >
                        {{
                            file.status === 'uploading'
                                ? 'progress_activity'
                                : file.status === 'error'
                                  ? 'error'
                                  : 'description'
                        }}
                    </span>
                    <span class="font-medium">{{ file.name }}</span>
                    <span v-if="file.status === 'uploading'" class="text-[11px] text-amber-600">
                        {{ Math.max(0, Math.min(100, Math.round(file.progress || 0))) }}%
                    </span>
                    <span v-else-if="file.status === 'error'" class="text-[11px] text-rose-600">
                        上传失败
                    </span>
                    <button
                        v-if="file.status === 'ready' && file.id"
                        type="button"
                        class="ml-1 hover:opacity-80 text-blue-400 hover:text-blue-600"
                        title="预览"
                        @click="$emit('preview-pending-file', file)"
                    >
                        <span class="material-symbols-outlined text-sm">visibility</span>
                    </button>
                    <button
                        type="button"
                        class="ml-1 hover:opacity-80"
                        :class="
                            file.status === 'error'
                                ? 'text-rose-400 hover:text-rose-600'
                                : 'text-blue-400 hover:text-blue-600'
                        "
                        @click="$emit('remove-pending-file', file.id)"
                    >
                        <span class="material-symbols-outlined text-sm">close</span>
                    </button>
                </div>
            </div>

            <!-- 主输入区域 -->
            <div class="relative">
                <!-- 技能提及下拉面板 -->
                <div
                    v-if="showMentionPanel"
                    class="absolute bottom-[calc(100%+0.75rem)] left-0 z-30 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg backdrop-blur-xl"
                >
                    <button
                        v-for="(skill, index) in filteredMentionSkills"
                        :key="skill.id"
                        type="button"
                        :class="[
                            'flex w-full items-start gap-2 px-3 py-2.5 text-left text-sm transition-colors',
                            isMentionSkillActive(index) ? 'bg-accent-soft' : 'hover:bg-accent-soft',
                        ]"
                        :aria-selected="isMentionSkillActive(index)"
                        @mouseenter="setMentionSelection(index)"
                        @mousedown.prevent.stop="handleMentionSelection(skill)"
                    >
                        <span
                            class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl shadow-lg"
                            :class="getSkillPickerIconClass(skill)"
                        >
                            <span class="material-symbols-outlined fill-0 text-base text-white">
                                {{ getSkillPickerIcon(skill) }}
                            </span>
                        </span>
                        <span class="min-w-0 flex-1">
                            <span class="block truncate font-semibold text-strong">
                                {{ skill.displayName || skill.runtimeSkillName }}
                            </span>
                            <span
                                v-if="skill.description || skill.category"
                                class="mt-0.5 block truncate text-xs text-muted"
                            >
                                {{ skill.description || skill.category }}
                            </span>
                        </span>
                    </button>
                    <div
                        v-if="filteredMentionSkills.length === 0"
                        class="px-3 py-2.5 text-xs text-muted"
                    >
                        没有匹配的可用技能
                    </div>
                    <div
                        v-else
                        class="border-t border-slate-100 bg-slate-50 px-3 py-2 text-[11px] text-slate-500"
                    >
                        ↑↓ 选择，Tab / Enter 确认，Esc 关闭
                    </div>
                </div>

                <!-- MentionEditor 编辑器 -->
                <MentionEditor
                    ref="editorRef"
                    class="mention-editor--default"
                    :model-value="draft"
                    :placeholder="resolvedPlaceholder"
                    :disabled="isDisabled"
                    :parse-mentions="parseMentions"
                    @update:modelValue="$emit('update:draft', $event)"
                    @focus="handleFocus"
                    @blur="handleBlur"
                    @keydown="handleKeydown"
                />
            </div>

            <!-- 底部工具栏 -->
            <div class="flex items-center justify-between gap-3 px-4 pb-3 pt-2.5">
                <div class="flex items-center gap-2">
                    <!-- 附件按钮 -->
                    <button
                        v-if="showActions"
                        type="button"
                        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-transparent bg-transparent text-slate-500 transition-all hover:bg-slate-100 hover:text-slate-700"
                        title="上传附件"
                        :disabled="isDisabled"
                        @click="$emit('trigger-file-picker')"
                    >
                        <span class="material-symbols-outlined text-xl">attach_file</span>
                    </button>

                    <div
                        v-if="showSkillPicker"
                        ref="skillPickerRef"
                        class="relative"
                        @mousedown.stop
                        @click.stop
                    >
                        <button
                            type="button"
                            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-transparent bg-transparent text-slate-500 transition-all hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:cursor-not-allowed disabled:opacity-60"
                            :aria-expanded="skillPickerOpen"
                            aria-haspopup="menu"
                            aria-label="选择技能"
                            title="选择技能"
                            :disabled="isDisabled"
                            @click="toggleSkillPicker"
                        >
                            <span class="material-symbols-outlined text-xl">alternate_email</span>
                        </button>

                        <transition
                            enter-active-class="transition duration-150 ease-out"
                            enter-from-class="translate-y-1 opacity-0"
                            enter-to-class="translate-y-0 opacity-100"
                            leave-active-class="transition duration-100 ease-in"
                            leave-from-class="translate-y-0 opacity-100"
                            leave-to-class="translate-y-1 opacity-0"
                        >
                            <div
                                v-if="skillPickerOpen"
                                class="absolute bottom-[calc(100%+0.5rem)] left-0 z-40 w-72 max-w-[calc(100vw-2rem)] overflow-hidden rounded-2xl border border-slate-200 bg-white p-1.5 shadow-lg backdrop-blur-xl"
                                role="menu"
                                aria-label="技能选择"
                            >
                                <button
                                    v-for="option in skillPickerOptions"
                                    :key="option.value"
                                    type="button"
                                    class="flex w-full items-start gap-2 rounded-[18px] px-3 py-2.5 text-left text-sm transition-colors hover:bg-accent-soft"
                                    role="menuitem"
                                    @click="handleSkillPickerSelection(option.skill)"
                                >
                                    <span
                                        class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl shadow-lg"
                                        :class="getSkillPickerIconClass(option.skill)"
                                    >
                                        <span
                                            class="material-symbols-outlined fill-0 text-base text-white"
                                        >
                                            {{ getSkillPickerIcon(option.skill) }}
                                        </span>
                                    </span>
                                    <span class="min-w-0 flex-1">
                                        <span class="block truncate font-semibold text-strong">
                                            {{ option.label }}
                                        </span>
                                        <span
                                            v-if="option.description"
                                            class="mt-0.5 block truncate text-xs text-muted"
                                        >
                                            {{ option.description }}
                                        </span>
                                    </span>
                                </button>
                            </div>
                        </transition>
                    </div>

                    <!-- 模型选择器 (紧跟附件按钮) -->
                    <AppSelect
                        v-if="showModelSelector"
                        :model-value="modelValue"
                        :options="modelOptions"
                        size="sm"
                        menu-placement="top"
                        :full-width="false"
                        :disabled="isDisabled || modelDisabled || modelLoading"
                        :leading-icon="modelLeadingIcon"
                        :leading-icon-class="modelLeadingIconClass"
                        placeholder="模型"
                        :button-class="modelButtonClass"
                        :menu-class="modelMenuClass"
                        :option-class="modelOptionClass"
                        @update:model-value="$emit('update:modelValue', $event)"
                    />
                </div>

                <!-- 发送按钮 -->
                <button
                    type="submit"
                    class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-white shadow-none transition-all disabled:cursor-not-allowed hover:bg-primary-hover disabled:opacity-35"
                    :disabled="isDisabled || !canSubmit"
                    title="发送消息"
                >
                    <span v-if="sending" class="material-symbols-outlined text-lg animate-spin"
                        >progress_activity</span
                    >
                    <span v-else class="material-symbols-outlined text-lg">arrow_upward</span>
                </button>
            </div>
        </form>
        <p
            v-if="chatError"
            :class="[splitView ? 'max-w-none' : 'w-full', 'mt-2 text-xs text-danger']"
        >
            {{ chatError }}
        </p>
    </div>
</template>

<script>
import {
    buildSkillMentionLookup,
    getActiveSkillMentionContext,
    getSkillMentionAliases,
    hasSubstantiveSkillMessage,
    resolveSkillMentionState,
} from '@/utils/skillMention';
import { getSkillIconGradientClass, resolveSkillIcon } from '@/utils/skillVisuals';
import MentionEditor from './MentionEditor.vue';
import AppSelect from '@/components/AppSelect.vue';

export default {
    name: 'ChatComposer',
    components: {
        MentionEditor,
        AppSelect,
    },
    emits: [
        'submit',
        'trigger-file-picker',
        'remove-pending-file',
        'preview-pending-file',
        'update:draft',
        'select-skill-mention',
        'update:modelValue',
    ],
    props: {
        draft: {
            type: String,
            default: '',
        },
        sending: {
            type: Boolean,
            default: false,
        },
        disabled: {
            type: Boolean,
            default: false,
        },
        pendingFiles: {
            type: Array,
            default: () => [],
        },
        showActions: {
            type: Boolean,
            default: true,
        },
        placeholder: {
            type: String,
            default: '',
        },
        splitView: {
            type: Boolean,
            default: false,
        },
        chatError: {
            type: String,
            default: '',
        },
        skillMentionOptions: {
            type: Array,
            default: () => [],
        },
        // 模型选择器相关 props
        showModelSelector: {
            type: Boolean,
            default: false,
        },
        modelValue: {
            type: [String, Number, null],
            default: null,
        },
        modelOptions: {
            type: Array,
            default: () => [],
        },
        modelLoading: {
            type: Boolean,
            default: false,
        },
        modelDisabled: {
            type: Boolean,
            default: false,
        },
        modelUnavailable: {
            type: Boolean,
            default: false,
        },
    },
    data() {
        return {
            activeMentionIndex: -1,
            dismissedMentionToken: null,
            isEditorFocused: false,
            skillPickerOpen: false,
        };
    },
    mounted() {
        document.addEventListener('mousedown', this.handleDocumentMouseDown);
    },
    beforeUnmount() {
        document.removeEventListener('mousedown', this.handleDocumentMouseDown);
    },
    computed: {
        showSkillPicker() {
            return this.skillPickerOptions.length > 0;
        },
        skillPickerOptions() {
            return (Array.isArray(this.skillMentionOptions) ? this.skillMentionOptions : [])
                .filter(skill => skill?.id)
                .map(skill => ({
                    value: skill.id,
                    label:
                        skill.displayName ||
                        skill.runtimeSkillName ||
                        skill.category ||
                        `技能 #${skill.id}`,
                    description: skill.description || skill.category || '',
                    skill,
                }));
        },
        skillMentionLookup() {
            return buildSkillMentionLookup(this.skillMentionOptions);
        },
        mentionContext() {
            return getActiveSkillMentionContext(this.draft, this.skillMentionLookup);
        },
        mentionToken() {
            return this.mentionContext?.token || null;
        },
        mentionQuery() {
            return this.mentionContext?.query?.toLowerCase() ?? null;
        },
        filteredMentionSkills() {
            if (this.mentionQuery == null) {
                return [];
            }
            const query = this.mentionQuery;
            return (Array.isArray(this.skillMentionOptions) ? this.skillMentionOptions : [])
                .filter(skill => {
                    const label = [
                        getSkillMentionAliases(skill).join(' '),
                        skill?.displayName || '',
                        skill?.runtimeSkillName || '',
                        skill?.description || '',
                        skill?.category || '',
                    ].join(' ');
                    return !query || label.toLowerCase().includes(query);
                })
                .slice(0, 6);
        },
        showMentionPanel() {
            return (
                this.isEditorFocused &&
                this.mentionToken != null &&
                this.mentionToken !== this.dismissedMentionToken &&
                this.skillMentionOptions.length > 0
            );
        },
        normalizedActiveMentionIndex() {
            if (!this.filteredMentionSkills.length || this.activeMentionIndex < 0) {
                return -1;
            }
            return Math.max(
                0,
                Math.min(this.activeMentionIndex, this.filteredMentionSkills.length - 1)
            );
        },
        activeMentionSkill() {
            if (this.normalizedActiveMentionIndex < 0) {
                return null;
            }
            return this.filteredMentionSkills[this.normalizedActiveMentionIndex] || null;
        },
        modelButtonClass() {
            if (this.modelUnavailable) {
                return 'h-10 min-h-0 !w-[184px] rounded-full !border-transparent !bg-transparent px-3 pr-9 text-xs !text-warning !shadow-none hover:!border-transparent hover:!bg-warning/10';
            }
            return 'h-10 min-h-0 !w-[184px] rounded-full !border-transparent !bg-transparent px-3 pr-9 text-xs !text-body !shadow-none hover:!border-transparent hover:!bg-[#f0f4f9] hover:!text-strong';
        },
        modelLeadingIcon() {
            if (this.modelLoading) {
                return 'progress_activity';
            }
            if (this.modelUnavailable) {
                return 'warning';
            }
            return 'smart_toy';
        },
        modelLeadingIconClass() {
            if (this.modelLoading) {
                return 'animate-spin text-[#64748b]';
            }
            if (this.modelUnavailable) {
                return 'text-warning';
            }
            return 'text-[#64748b]';
        },
        modelMenuClass() {
            return 'w-80 max-w-[calc(100vw-2rem)] rounded-[24px] border-[#e5eaf1] bg-white/98 p-1.5 shadow-[0_20px_48px_-20px_rgba(15,23,42,0.28)] ring-0 backdrop-blur-xl';
        },
        modelOptionClass() {
            return 'rounded-[18px]';
        },
        isDisabled() {
            return this.sending || this.disabled;
        },
        resolvedPlaceholder() {
            if (this.disabled) {
                return '当前会话只读，请新建对话后继续输入';
            }
            return this.placeholder || '输入消息，Enter 发送，Ctrl+Enter 换行';
        },
        canSubmit() {
            if (this.isDisabled) {
                return false;
            }
            const hasReadyFiles = this.pendingFiles.some(
                file => file && file.status !== 'uploading' && file.status !== 'error' && file.id
            );
            const hasUploadingFiles = this.pendingFiles.some(
                file => file && file.status === 'uploading'
            );
            if (hasUploadingFiles) {
                return false;
            }
            return hasReadyFiles || hasSubstantiveSkillMessage(this.draft, this.skillMentionLookup);
        },
    },
    watch: {
        showSkillPicker(nextVisible) {
            if (!nextVisible) {
                this.skillPickerOpen = false;
            }
        },
        isDisabled(nextDisabled) {
            if (nextDisabled) {
                this.skillPickerOpen = false;
            }
        },
        mentionToken(nextToken, previousToken) {
            if (!nextToken) {
                this.activeMentionIndex = -1;
                return;
            }
            if (nextToken !== previousToken) {
                this.dismissedMentionToken = null;
            }
        },
        filteredMentionSkills(nextSkills) {
            const total = Array.isArray(nextSkills) ? nextSkills.length : 0;
            if (!total) {
                this.activeMentionIndex = -1;
                return;
            }
            const exactMatchIndex = nextSkills.findIndex(
                skill => skill?.id && skill.id === this.mentionContext?.skill?.id
            );
            if (exactMatchIndex >= 0) {
                this.activeMentionIndex = exactMatchIndex;
                return;
            }
            if (this.activeMentionIndex < 0) {
                this.activeMentionIndex = 0;
                return;
            }
            if (this.activeMentionIndex > total - 1) {
                this.activeMentionIndex = total - 1;
            }
        },
    },
    methods: {
        /**
         * 解析文本中的 mention，供 MentionEditor 使用
         */
        parseMentions(text) {
            return resolveSkillMentionState(text, this.skillMentionLookup).mentions;
        },
        getSkillPickerIcon(skill) {
            return resolveSkillIcon(skill?.icon);
        },
        getSkillPickerIconClass(skill) {
            return getSkillIconGradientClass(skill?.iconColor);
        },
        isMentionSkillActive(index) {
            return index === this.normalizedActiveMentionIndex;
        },
        moveMentionSelection(offset) {
            const total = this.filteredMentionSkills.length;
            if (!total) return;

            if (this.normalizedActiveMentionIndex < 0) {
                this.activeMentionIndex = offset > 0 ? 0 : total - 1;
                return;
            }
            const currentIndex = this.normalizedActiveMentionIndex;
            this.activeMentionIndex = (currentIndex + offset + total) % total;
        },
        setMentionSelection(index) {
            if (index < 0 || index >= this.filteredMentionSkills.length) {
                this.activeMentionIndex = -1;
                return;
            }
            this.activeMentionIndex = index;
        },
        toggleSkillPicker() {
            if (this.isDisabled || !this.showSkillPicker) {
                return;
            }
            if (!this.skillPickerOpen && this.showMentionPanel) {
                this.closeMentionPanel();
            }
            this.skillPickerOpen = !this.skillPickerOpen;
        },
        handleSkillPickerSelection(skill) {
            if (!skill?.id) {
                return;
            }
            this.skillPickerOpen = false;
            this.$emit('select-skill-mention', skill);
            this.$nextTick(() => {
                this.focusInput();
            });
        },
        handleDocumentMouseDown(event) {
            if (!this.skillPickerOpen) {
                return;
            }
            const root = this.$refs.skillPickerRef;
            if (root && !root.contains(event.target)) {
                this.skillPickerOpen = false;
            }
        },
        handleMentionSelection(skill = null) {
            const targetSkill = skill || this.activeMentionSkill || this.filteredMentionSkills[0];
            if (!targetSkill?.id) return false;

            this.dismissedMentionToken = this.mentionToken;
            this.activeMentionIndex = -1;
            this.$emit('select-skill-mention', targetSkill);
            return true;
        },
        handleFocus() {
            this.isEditorFocused = true;
        },
        handleBlur() {
            this.isEditorFocused = false;
            this.activeMentionIndex = -1;
        },
        handleKeydown(event) {
            if (event.key === 'Escape' && this.skillPickerOpen) {
                event.preventDefault();
                this.skillPickerOpen = false;
                return;
            }

            if (this.showMentionPanel) {
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    this.moveMentionSelection(1);
                    return;
                }
                if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    this.moveMentionSelection(-1);
                    return;
                }
                if (event.key === 'Tab') {
                    event.preventDefault();
                    if (this.filteredMentionSkills.length) {
                        this.handleMentionSelection();
                    } else {
                        this.closeMentionPanel();
                    }
                    return;
                }
                if (event.key === 'Escape') {
                    event.preventDefault();
                    this.closeMentionPanel();
                    return;
                }
                if (
                    event.key === 'Enter' &&
                    !event.shiftKey &&
                    !event.ctrlKey &&
                    !event.metaKey &&
                    this.filteredMentionSkills.length
                ) {
                    event.preventDefault();
                    this.handleMentionSelection();
                    return;
                }
            }

            if (event.key !== 'Enter') return;

            if (event.shiftKey || event.ctrlKey || event.metaKey) {
                document.execCommand('insertLineBreak');
                return;
            }

            // 单独 Enter → 发送
            if (!this.canSubmit) {
                event.preventDefault();
                return;
            }
            event.preventDefault();
            this.$emit('submit');
        },
        closeMentionPanel() {
            if (!this.mentionToken) return;
            this.dismissedMentionToken = this.mentionToken;
        },
        clearInput() {
            this.isEditorFocused = false;
            this.activeMentionIndex = -1;
            this.dismissedMentionToken = null;
            this.skillPickerOpen = false;
            this.$refs.editorRef?.clear?.();
        },
        focusInput() {
            this.$refs.editorRef?.focus?.();
        },
    },
};
</script>

<style scoped>
.chat-composer-form {
    background: #fbfcfd;
    border: 1px solid #e7ebef;
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.9),
        0 14px 26px -28px rgba(15, 23, 42, 0.08);
}

.chat-composer-form:focus-within {
    background: #ffffff;
    border-color: rgb(var(--color-accent) / 0.18);
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.94),
        0 14px 26px -28px rgba(15, 23, 42, 0.08),
        0 0 0 4px rgb(var(--color-accent) / 0.05);
}

.chat-composer-form--default {
    background: rgba(255, 255, 255, 0.96);
    border-color: #e5eaf1;
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.94),
        0 14px 32px -24px rgba(15, 23, 42, 0.14);
    backdrop-filter: blur(18px);
}

.chat-composer-form--default:focus-within {
    border-color: rgb(var(--color-accent) / 0.22);
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.98),
        0 18px 36px -28px rgba(15, 23, 42, 0.16),
        0 0 0 4px rgb(var(--color-accent) / 0.05);
}
</style>
