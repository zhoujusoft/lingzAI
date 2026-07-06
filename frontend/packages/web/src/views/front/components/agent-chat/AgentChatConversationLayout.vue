<template>
    <section
        class="agent-chat-conversation agent-chat-conversation--default relative flex h-full min-h-0 w-full flex-1 overflow-hidden bg-transparent"
    >
        <div
            class="mx-auto flex min-h-0 w-full max-w-[768px] flex-col overflow-hidden bg-transparent"
        >
            <ChatMessageStream
                :messages="messages"
                :scroll-token="scrollToken"
                :should-auto-scroll="shouldAutoScroll"
                :empty-title="emptyTitle"
                :empty-description="emptyDescription"
                :empty-icon="emptyIcon"
                :assistant-icon="assistantIcon"
                :user-avatar-url="resolvedUserAvatarUrl"
                :welcome-actions="welcomeActions"
                :split-view="false"
                @toggle-segment="toggleSegment"
                @open-html-preview="openHtmlPreview"
                @open-citation="openCitationPreview"
                @frontend-render-action="handleFrontendRenderAction"
                @welcome-action="handleWelcomeAction"
            />

            <ChatComposer
                ref="composerRef"
                :draft="draft"
                :sending="sending"
                :pending-files="pendingFiles"
                :show-actions="enableAttachments"
                :placeholder="draftPlaceholder"
                :chat-error="chatError"
                :split-view="false"
                :skill-mention-options="skillMentionOptions"
                :show-model-selector="showChatModelSelector"
                :model-value="selectedChatModelId"
                :model-options="effectiveChatModelOptions"
                :model-loading="loadingChatModelOptions"
                :model-disabled="sending || updatingChatModel"
                :model-unavailable="selectedChatModelAvailable === false"
                @update:draft="handleDraftUpdate"
                @submit="sendMessage"
                @trigger-file-picker="triggerFilePicker"
                @remove-pending-file="removePendingFile"
                @select-skill-mention="selectSkillMention"
                @update:model-value="handleChatModelChange"
            />

            <input
                ref="fileInput"
                type="file"
                class="hidden"
                multiple
                :accept="chatUploadAccept"
                @change="handleFileChange"
            />
        </div>

        <ChatCitationModal :active-citation="activeCitation" @close="closeCitationPreview" />

        <div
            v-if="showFileDropOverlay"
            class="pointer-events-none absolute inset-0 z-50 flex items-center justify-center bg-strong/30 backdrop-blur-sm"
        >
            <div
                class="front-card mx-6 flex w-full max-w-xl flex-col items-center px-8 py-12 text-center"
            >
                <div
                    class="mb-5 flex h-20 w-20 items-center justify-center rounded-2xl bg-accent-soft text-primary"
                >
                    <span class="material-symbols-outlined text-[38px]">cloud_upload</span>
                </div>
                <h3 class="text-xl font-bold tracking-tight text-strong">拖拽文件以上传</h3>
                <p class="mt-3 text-sm leading-6 text-body">
                    支持图片、文档和 ZIP 压缩包。图片最大 10MB，PDF 最大 50MB，其他文档最大
                    20MB，ZIP 最大 100MB， 单次最多 10 个文件且总大小不超过 100MB。
                </p>
                <div class="mt-5 flex flex-wrap items-center justify-center gap-2">
                    <span
                        class="rounded-full border border-border-soft/75 bg-surface-alt/70 px-3 py-1 text-xs font-medium text-body"
                    >
                        PNG / JPG / WEBP / GIF
                    </span>
                    <span
                        class="rounded-full border border-border-soft/75 bg-surface-alt/70 px-3 py-1 text-xs font-medium text-body"
                    >
                        PDF / Word / Excel / TXT / Markdown / PPT
                    </span>
                    <span
                        class="rounded-full border border-border-soft/75 bg-surface-alt/70 px-3 py-1 text-xs font-medium text-body"
                    >
                        ZIP
                    </span>
                </div>
            </div>
        </div>
    </section>
</template>

<script>
import BaseFrontChatWorkspace from '@/views/front/components/front-chat/FrontChatWorkspace.vue';
import ChatCitationModal from '@/views/front/components/front-chat/ChatCitationModal.vue';
import ChatComposer from '@/views/front/components/front-chat/ChatComposer.vue';
import ChatMessageStream from '@/views/front/components/front-chat/ChatMessageStream.vue';

export default {
    name: 'AgentChatConversationLayout',
    extends: BaseFrontChatWorkspace,
    components: {
        ChatMessageStream,
        ChatComposer,
        ChatCitationModal,
    },
    props: {
        assistantIcon: {
            type: String,
            default: '',
        },
    },
};
</script>
