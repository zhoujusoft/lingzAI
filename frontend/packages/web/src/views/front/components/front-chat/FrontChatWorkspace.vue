<template>
    <section
        :class="[
            'chat-workspace relative flex h-full min-h-0 w-full flex-1 overflow-hidden bg-transparent',
        ]"
    >
        <Transition name="history-pane-motion">
            <div
                v-if="showSidebar && showHistorySidebar && shouldRenderHistorySidebar"
                :class="[
                    'history-pane relative z-10 shrink-0 overflow-hidden transition-[width] duration-200',
                    'border-r border-[#eef2f6]',
                    effectiveSidebarCollapsed ? 'w-[80px]' : 'w-[304px]',
                ]"
            >
                <ChatSidebar
                    :collapsed="effectiveSidebarCollapsed"
                    :show-toggle-button="showSidebarToggle"
                    :show-assistant-summary="showSidebarAssistantHeader"
                    :assistant-title="assistantTitle"
                    :assistant-status-text="effectiveHeaderStatusText"
                    :assistant-status-tone="effectiveHeaderStatusTone"
                    :assistant-icon="assistantIcon"
                    :agent-icon="assistantAvatarUrl || assistantIcon"
                    :new-chat-label="newChatLabel"
                    :new-chat-icon="newChatIcon"
                    :show-assistant-edit-button="showAssistantEditButton"
                    :show-knowledge-select="showKnowledgeSelect"
                    :selected-knowledge="selectedKnowledge"
                    :search-keyword="conversationSearchKeyword"
                    :knowledge-options="knowledgeOptions"
                    :select-label="selectLabel"
                    :conversation-items="filteredConversationItems"
                    :conversation-total="conversationTotal"
                    :has-more-conversations="hasMoreConversations"
                    :loading-more-conversations="loadingMoreConversations"
                    :deleting-conversation-ids="deletingConversationIds"
                    :renaming-conversation-ids="renamingConversationIds"
                    @update:selectedKnowledge="selectedKnowledge = $event"
                    @update:searchKeyword="conversationSearchKeyword = $event"
                    @toggle-sidebar="toggleSidebar"
                    @open-assistant-config="openAssistantConfig"
                    @new-chat="handleAssistantSummaryClick"
                    @delete-conversation="deleteConversation"
                    @rename-conversation="renameConversation"
                    @select-conversation="selectConversation"
                    @load-more-conversations="loadMoreConversationItems"
                />
            </div>
        </Transition>

        <div
            :class="[
                'conversation-layout relative z-10 flex min-w-0 flex-1 overflow-hidden bg-transparent',
            ]"
        >
            <div
                ref="conversationPaneRef"
                :class="[
                    'conversation-pane order-1 flex min-w-0 flex-col overflow-hidden transition-[basis] duration-200 ease-in-out',
                    'bg-transparent',
                    isPreviewPanelVisible
                        ? 'basis-full md:basis-[680px] xl:basis-[768px] flex-none'
                        : 'basis-full md:basis-[680px] xl:basis-[768px] flex-none mx-auto',
                ]"
                :style="conversationPaneStyle"
            >
                <ChatHeaderBar
                    v-if="showHeader"
                    :title="headerTitle"
                    :status-text="effectiveHeaderStatusText"
                    :status-tone="effectiveHeaderStatusTone"
                    :icon="headerIcon"
                    :show-file-panel-button="showFileListPanel"
                    :file-panel-open="isFilePanelOpen"
                    :show-agent-settings-button="showAgentSettingsButton"
                    :agent-settings-open="agentSettingsPanelOpen"
                    :agent-settings-label="agentSettingsPanelLabel"
                    @toggle-file-panel="toggleFilePanel"
                    @toggle-agent-settings="toggleAgentSettingsPanel"
                />

                <div class="flex min-h-0 flex-1 flex-col">
                    <div
                        class="flex min-h-0 flex-1 flex-col pt-2"
                        :class="[
                            shouldCenterConversationContent ? 'mx-auto' : '',
                            conversationDividerClass,
                        ]"
                        :style="conversationContentStyle"
                    >
                        <ChatMessageStream
                            :messages="messages"
                            :scroll-token="scrollToken"
                            :should-auto-scroll="shouldAutoScroll"
                            :empty-title="emptyTitle"
                            :empty-description="emptyDescription"
                            :empty-icon="emptyIcon"
                            :assistant-avatar-url="assistantAvatarUrl"
                            :assistant-icon="assistantIcon"
                            :user-avatar-url="resolvedUserAvatarUrl"
                            :welcome-actions="welcomeActions"
                            :split-view="isPreviewPanelVisible"
                            :loading="loadingConversation"
                            :loading-message-count="6"
                            :loading-older="loadingOlderMessages"
                            :has-older-messages="hasOlderMessages"
                            @toggle-segment="toggleSegment"
                            @open-html-preview="openHtmlPreview"
                            @open-attachment-preview="previewAttachmentFile"
                            @open-citation="openCitationPreview"
                            @frontend-render-action="handleFrontendRenderAction"
                            @welcome-action="handleWelcomeAction"
                            @load-older="loadOlderConversationMessages"
                        />

                        <ChatComposer
                            ref="composerRef"
                            :draft="draft"
                            :sending="sending"
                            :disabled="selectedConversationInputDisabled"
                            :pending-files="pendingFiles"
                            :show-actions="enableAttachments"
                            :placeholder="draftPlaceholder"
                            :chat-error="chatError"
                            :split-view="isPreviewPanelVisible"
                            :skill-mention-options="skillMentionOptions"
                            :show-model-selector="showChatModelSelector"
                            :model-value="selectedChatModelId"
                            :model-options="effectiveChatModelOptions"
                            :model-loading="loadingChatModelOptions"
                            :model-disabled="
                                selectedConversationInputDisabled || sending || updatingChatModel
                            "
                            :model-unavailable="selectedChatModelAvailable === false"
                            @update:draft="handleDraftUpdate"
                            @submit="sendMessage"
                            @trigger-file-picker="triggerFilePicker"
                            @remove-pending-file="removePendingFile"
                            @preview-pending-file="previewPendingFile"
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
                </div>
            </div>

            <ChatPreviewTabContainer
                :active-html="activeHtml"
                :session-id="sessionId"
                :show-file-list="showFileListPanel && isFilePanelOpen"
                @close="closePreview"
                @open-external="openHtmlPreviewInNewWindow"
            />

            <transition name="preview-slide">
                <aside
                    v-if="agentSettingsPanelOpen"
                    class="order-2 flex h-full min-w-0 flex-1 flex-col overflow-hidden border-l border-border-soft bg-surface"
                >
                    <div
                        class="flex h-10 shrink-0 items-center justify-between border-b border-border-soft px-3"
                    >
                        <div class="flex items-center gap-2">
                            <span class="material-symbols-outlined text-base text-muted">tune</span>
                            <h3 class="text-sm font-semibold text-strong">
                                {{ agentSettingsPanelLabel }}
                            </h3>
                        </div>
                        <button
                            type="button"
                            class="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                            title="关闭"
                            @click="closePreview"
                        >
                            <span class="material-symbols-outlined text-base">close</span>
                        </button>
                    </div>
                    <AgentSettingsPane
                        :model-value="agentSettingsName"
                        :readonly="isReadonlyAgentSettings"
                        :display-name="agentSettingsDisplayName"
                        :description="agentSettingsDescription"
                        :avatar-visual="agentSettingsAvatarVisual"
                        :show-channels="showAgentSettingsChannels"
                        :skills="agentSettingsSkills"
                        :tools="agentSettingsTools"
                        :avatar-uploading="agentSettingsAvatarUploading"
                        :avatar-error-message="agentSettingsAvatarError"
                        :saving="agentSettingsSaving"
                        :error-message="agentSettingsError"
                        :channel-status-message="agentSettingsChannelStatus"
                        :bound-channels="agentSettingsBoundChannels"
                        :closing-channel-ids="agentSettingsClosingChannelIds"
                        class="min-h-0 flex-1"
                        @trigger-avatar-upload="triggerAgentSettingsAvatarSelect"
                        @authorize-channel="authorizeAgentSettingsChannel"
                        @close-channel="closeAgentSettingsChannel"
                        @update:model-value="agentSettingsName = $event"
                        @save="saveAgentSettings"
                    />
                    <input
                        ref="agentSettingsAvatarInput"
                        type="file"
                        accept="image/png,image/jpeg"
                        class="hidden"
                        @change="handleAgentSettingsAvatarChange"
                    />
                </aside>
            </transition>
        </div>
        <BaseModal
            :open="agentSettingsWechatQrModalOpen"
            panel-class="max-w-[360px]"
            @close="closeAgentSettingsWechatQrModal"
        >
            <template #header>
                <div
                    class="flex items-center justify-between border-b border-border-soft px-4 py-3"
                >
                    <h3 class="text-sm font-semibold text-strong">微信授权</h3>
                    <button
                        type="button"
                        class="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                        @click="closeAgentSettingsWechatQrModal"
                    >
                        <span class="material-symbols-outlined text-base">close</span>
                    </button>
                </div>
            </template>
            <template #content>
                <div class="flex flex-col items-center px-6 py-6 text-center">
                    <img
                        v-if="agentSettingsWechatQrCode"
                        :src="agentSettingsWechatQrCode"
                        alt="微信授权二维码"
                        class="h-48 w-48 rounded-xl border border-border-soft bg-white object-contain"
                    />
                    <div
                        v-else
                        class="flex h-48 w-48 items-center justify-center rounded-xl border border-border-soft bg-white px-5 text-sm font-semibold text-muted"
                    >
                        正在生成授权码...
                    </div>
                    <p class="mt-4 text-sm text-strong">
                        {{
                            agentSettingsWechatQrCode
                                ? '请使用微信扫码完成授权'
                                : '正在生成微信授权二维码'
                        }}
                    </p>
                    <p class="mt-2 text-xs leading-5 text-muted">
                        {{
                            agentSettingsWechatQrCode
                                ? '授权成功后会自动绑定。'
                                : '请稍候，二维码马上出现。'
                        }}
                    </p>
                </div>
            </template>
        </BaseModal>
        <BaseModal
            :open="agentSettingsDingtalkQrModalOpen"
            panel-class="max-w-[360px]"
            @close="closeAgentSettingsDingtalkQrModal"
        >
            <template #header>
                <div
                    class="flex items-center justify-between border-b border-border-soft px-4 py-3"
                >
                    <h3 class="text-sm font-semibold text-strong">钉钉授权</h3>
                    <button
                        type="button"
                        class="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                        @click="closeAgentSettingsDingtalkQrModal"
                    >
                        <span class="material-symbols-outlined text-base">close</span>
                    </button>
                </div>
            </template>
            <template #content>
                <div class="flex flex-col items-center px-6 py-6 text-center">
                    <img
                        v-if="agentSettingsDingtalkQrCode"
                        :src="agentSettingsDingtalkQrCode"
                        alt="钉钉授权二维码"
                        class="h-48 w-48 rounded-xl border border-border-soft bg-white object-contain"
                    />
                    <div
                        v-else
                        class="flex h-48 w-48 items-center justify-center rounded-xl border border-border-soft bg-white px-5 text-sm font-semibold text-muted"
                    >
                        正在生成授权码...
                    </div>
                    <p class="mt-4 text-sm text-strong">
                        {{
                            agentSettingsDingtalkQrCode
                                ? '请使用钉钉扫码完成授权'
                                : '正在生成钉钉授权二维码'
                        }}
                    </p>
                    <p class="mt-2 text-xs leading-5 text-muted">
                        {{
                            agentSettingsDingtalkQrCode
                                ? '授权成功后会自动绑定。'
                                : '请稍候，二维码马上出现。'
                        }}
                    </p>
                </div>
            </template>
        </BaseModal>
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
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import BaseModal from '@/components/feedback/BaseModal.vue';
import { ChatConversationBean } from '@/model/bean';
import {
    ChatMessageKind,
    ChatMessageRender,
    ChatSegmentType,
    ChatStreamEventType,
    ChatToolStatus,
} from '@/model/front-chat';
import {
    beginDingtalkRegister,
    beginWechatLogin,
    createChannelConfig,
    getDingtalkRegisterStatus,
    getMyChannelBinding,
    getWechatLoginStatus,
    getWecomRuntimeStatus,
    listChannelConfigs,
    listMyChannelBindings,
    closeMyChannelBinding,
    saveMyChannelBinding,
    saveMyWecomBinding,
    updateChannelConfig,
} from '@/api/channels';
import {
    getCurrentUserAgentSkillDetails,
    updateCurrentUserAgentProfile,
    uploadCurrentUserAgentAvatar,
} from '@/api/user-agent-config';
import defaultAvatarSrc from '@/assets/images/default-avatar.svg';
import {
    agentConfigState,
    fetchAgentConfig,
    getAgentDisplayName,
    getAgentIcon,
} from '@/composables/useAgentConfig';
import { useChatLayoutWidth } from '@/composables/useChatLayoutWidth';
import { currentUserState, ensureCurrentUserLoaded } from '@/composables/useCurrentUser';
import { useElementWidth } from '@/composables/useElementWidth';
import { alert, confirm, prompt } from '@/composables/useModal';
import { showToast } from '@/composables/useToast';
import {
    appendSkillMentionMessage,
    applySkillMentionSelection,
    buildSkillMentionLookup,
    hasSubstantiveSkillMessage,
    resolveSkillMentionState,
} from '@/utils/skillMention';
import { ROUTE_PATHS } from '@/router/routePaths';
import { CHANNEL_LOGO_SOURCES } from '@/utils/channelVisuals';
import { validateImageFile } from '@/utils/imageEditor';
import { isImageSource, isMaterialSymbolName } from '@/utils/iconDisplay';
import { openImageEditorModal } from '@/utils/openImageEditorModal';
import { resolveUserAvatarUrl } from '@/utils/userAvatar';
import AgentSettingsPane from './AgentSettingsPane.vue';
import ChatCitationModal from './ChatCitationModal.vue';
import ChatComposer from './ChatComposer.vue';
import ChatHeaderBar from './ChatHeaderBar.vue';
import ChatHtmlPreview from './ChatHtmlPreview.vue';
import ChatMessageStream from './ChatMessageStream.vue';
import ChatPreviewTabContainer from './ChatPreviewTabContainer.vue';
import ChatSidebar from './ChatSidebar.vue';

const CHAT_IMAGE_EXTENSIONS = new Set(['.png', '.jpg', '.jpeg', '.webp', '.gif']);
const CHAT_ARCHIVE_EXTENSIONS = new Set(['.zip']);
const CHAT_DOCUMENT_EXTENSIONS = new Set([
    '.pdf',
    '.doc',
    '.docx',
    '.xls',
    '.xlsx',
    '.csv',
    '.txt',
    '.md',
    '.ppt',
    '.pptx',
]);
const CHAT_ALLOWED_EXTENSIONS = new Set([
    ...CHAT_IMAGE_EXTENSIONS,
    ...CHAT_ARCHIVE_EXTENSIONS,
    ...CHAT_DOCUMENT_EXTENSIONS,
]);
const CHAT_UPLOAD_ACCEPT = Array.from(CHAT_ALLOWED_EXTENSIONS).join(',');
const CHAT_MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const CHAT_MAX_PDF_BYTES = 50 * 1024 * 1024;
const CHAT_MAX_DOCUMENT_BYTES = 20 * 1024 * 1024;
const CHAT_MAX_ARCHIVE_BYTES = 100 * 1024 * 1024;
const CHAT_MAX_BATCH_FILES = 10;
const CHAT_MAX_BATCH_BYTES = 100 * 1024 * 1024;
const CHAT_SESSION_PAGE_SIZE = 20;
const CHAT_HISTORY_PAGE_SIZE = 10;
const WECOM_SDK_URL = 'https://wwcdn.weixin.qq.com/node/wework/js/wecom-aibot-sdk@0.1.0.min.js';
const WECOM_SOURCE = 'lingzhou-agent';
const AGENT_SETTINGS_CHANNEL_DEFAULTS = Object.freeze({
    weixin: {
        name: '微信 iLink',
        channelType: 'weixin',
        description: '微信渠道接入配置',
        configJson:
            '{\n  "autoPoll": true,\n  "pollIntervalMs": 3000,\n  "heartbeatEnabled": true\n}',
    },
    wecom: {
        name: '企业微信机器人',
        channelType: 'wecom',
        description: '企业微信渠道接入配置',
        configJson: '{\n  "welcome_text": "",\n  "max_reconnect_attempts": -1\n}',
    },
    dingtalk: {
        name: '钉钉机器人',
        channelType: 'dingtalk',
        description: '钉钉渠道接入配置',
        configJson:
            '{\n  "connection_mode": "stream",\n  "clientId": "",\n  "clientSecret": "",\n  "robotCode": "",\n  "replyTitle": "灵洲智能体",\n  "consumeThreads": 2,\n  "connectTimeoutMs": 30000,\n  "duplicateTtlMs": 600000,\n  "replyUnsupported": false\n}',
    },
});
let wecomSdkLoaded = false;

export default {
    name: 'FrontChatWorkspace',
    components: {
        AgentSettingsPane,
        BaseModal,
        ChatSidebar,
        ChatHeaderBar,
        ChatMessageStream,
        ChatComposer,
        ChatHtmlPreview,
        ChatCitationModal,
        ChatPreviewTabContainer,
    },
    emits: [
        'unauthorized',
        'conversation-context-change',
        'reset-chat-context',
        'skill-context-change',
        'request-finished',
        'render-payload',
        'artifact-preview',
        'welcome-action',
        'assistant-summary-click',
        'update:sidebarCollapsed',
    ],
    setup(props) {
        const router = useRouter();
        const conversationPaneRef = ref(null);
        const conversationPaneWidth = useElementWidth(conversationPaneRef);
        const { chatWidthPx } = useChatLayoutWidth({
            minWidth: 500,
            maxWidth: Number(props.chatMaxWidth || 768),
            pagePadding: 0,
        });

        const conversationContentWidthPx = computed(() => {
            const paneWidth = Number(conversationPaneWidth.value || 0);
            if (paneWidth <= 0) {
                return chatWidthPx.value;
            }
            return Math.min(chatWidthPx.value, paneWidth);
        });

        const conversationContentStyle = computed(() => ({
            width: `${conversationContentWidthPx.value}px`,
            maxWidth: '100%',
        }));

        const conversationPaneStyle = computed(() => {
            const maxWidth = Math.max(320, Number(props.chatMaxWidth || 768));
            return {
                flexBasis: `min(100%, ${maxWidth}px)`,
                maxWidth: '100%',
            };
        });

        const shouldCenterConversationContent = computed(() => {
            const paneWidth = Number(conversationPaneWidth.value || 0);
            return paneWidth > 0 && paneWidth > conversationContentWidthPx.value;
        });

        return {
            router,
            conversationPaneRef,
            conversationContentStyle,
            conversationPaneStyle,
            shouldCenterConversationContent,
        };
    },
    props: {
        adapter: {
            type: Object,
            required: true,
        },
        historyAdapter: {
            type: Object,
            default: null,
        },
        headerTitle: {
            type: String,
            default: '',
        },
        headerStatusText: {
            type: String,
            default: '',
        },
        headerIcon: {
            type: String,
            default: 'description',
        },
        useRuntimeHeaderStatus: {
            type: Boolean,
            default: false,
        },
        emptyTitle: {
            type: String,
            default: '',
        },
        emptyDescription: {
            type: String,
            default: '',
        },
        emptyIcon: {
            type: String,
            default: 'inventory_2',
        },
        assistantAvatarUrl: {
            type: String,
            default: '',
        },
        assistantIcon: {
            type: String,
            default: 'psychology',
        },
        chatMaxWidth: {
            type: Number,
            default: 768,
        },
        newChatLabel: {
            type: String,
            default: '',
        },
        newChatIcon: {
            type: String,
            default: '',
        },
        showAssistantEditButton: {
            type: Boolean,
            default: true,
        },
        welcomeActions: {
            type: Array,
            default: () => [],
        },
        draftPlaceholder: {
            type: String,
            default: '',
        },
        showKnowledgeSelect: {
            type: Boolean,
            default: false,
        },
        enableAttachments: {
            type: Boolean,
            default: true,
        },
        showFileListPanel: {
            type: Boolean,
            default: true,
        },
        knowledgeOptions: {
            type: Array,
            default: () => [],
        },
        selectLabel: {
            type: String,
            default: '知识库选择',
        },
        defaultKnowledge: {
            type: String,
            default: '',
        },
        sessionStorageKey: {
            type: String,
            default: '',
        },
        restoreInitialSession: {
            type: Boolean,
            default: true,
        },
        initialSidebarCollapsed: {
            type: Boolean,
            default: true,
        },
        sidebarCollapsed: {
            type: [Boolean, null],
            default: null,
        },
        hideSidebarWhenCollapsed: {
            type: Boolean,
            default: false,
        },
        showSidebarToggle: {
            type: Boolean,
            default: true,
        },
        showSidebar: {
            type: Boolean,
            default: true,
        },
        showHeader: {
            type: Boolean,
            default: true,
        },
        showSidebarAssistantHeader: {
            type: Boolean,
            default: false,
        },
        showHistorySidebar: {
            type: Boolean,
            default: true,
        },
        showConversationDivider: {
            type: Boolean,
            default: false,
        },
        enableSkillMention: {
            type: Boolean,
            default: false,
        },
        activeSkillContext: {
            type: Object,
            default: null,
        },
        agentSettingsContext: {
            type: Object,
            default: () => ({ type: 'hidden' }),
        },
        requestOptions: {
            type: Object,
            default: () => ({}),
        },
        resetOnMount: {
            type: Boolean,
            default: false,
        },
        previewMode: {
            type: String,
            default: 'internal',
            validator: value => ['internal', 'external'].includes(value),
        },
    },
    data() {
        return {
            draft: '',
            sessionId: '',
            sending: false,
            chatError: '',
            messages: [],
            toolEventIndex: {},
            seenToolEvents: {},
            seenCitationEvents: {},
            activeHtml: null,
            filePanelOpen: false,
            agentSettingsPanelOpen: false,
            agentSettingsName: '',
            agentSettingsSaving: false,
            agentSettingsError: '',
            agentSettingsAvatarUploading: false,
            agentSettingsAvatarError: '',
            agentSettingsAvatarPreviewUrl: '',
            agentSettingsChannelConfigs: [],
            agentSettingsBoundChannels: [],
            agentSettingsClosingChannelIds: [],
            agentSettingsChannelStatus: '',
            agentSettingsWechatQrModalOpen: false,
            agentSettingsWechatQrCode: '',
            agentSettingsWechatStatusTimer: null,
            agentSettingsDingtalkQrModalOpen: false,
            agentSettingsDingtalkQrCode: '',
            agentSettingsDingtalkRegisterStatus: '',
            agentSettingsDingtalkRegisterTimer: null,
            agentSettingsWecomStatusTimer: null,
            activeCitation: null,
            activeAssistantIndex: null,
            activeAssistantByRequest: {},
            activeRequestId: '',
            streamRequestContexts: {},
            conversationViewVersion: 0,
            activeRunCode: '',
            activeStreamController: null,
            shouldAutoScroll: true,
            scrollToken: 0,
            pendingFiles: [],
            selectedKnowledge: this.defaultKnowledge,
            isSidebarCollapsed: this.initialSidebarCollapsed,
            sidebarMediaQuery: null,
            conversationItems: [],
            conversationPageNo: 0,
            conversationTotal: 0,
            hasMoreConversations: false,
            loadingMoreConversations: false,
            deletingConversationIds: [],
            renamingConversationIds: [],
            selectedConversationId: null,
            conversationSearchKeyword: '',
            mentionSkills: [],
            chatModelOptions: [],
            selectedChatModelId: null,
            selectedChatModelDisplayName: '',
            selectedChatModelAvailable: true,
            loadingChatModelOptions: false,
            updatingChatModel: false,
            showFileDropOverlay: false,
            loadingConversation: false,
            loadingConversationId: null,
            historyPageNo: 0,
            hasOlderMessages: false,
            loadingOlderMessages: false,
            previewClosingTimer: null,
        };
    },
    computed: {
        assistantTitle() {
            return getAgentDisplayName();
        },
        assistantIcon() {
            return getAgentIcon();
        },
        chatUploadAccept() {
            return CHAT_UPLOAD_ACCEPT;
        },
        resolvedUserAvatarUrl() {
            return resolveUserAvatarUrl(currentUserState.profile, defaultAvatarSrc);
        },
        currentUserTokenQuota() {
            return currentUserState.profile?.tokenQuota || null;
        },
        skillMentionOptions() {
            return this.enableSkillMention ? this.mentionSkills : [];
        },
        skillMentionLookup() {
            return buildSkillMentionLookup(this.skillMentionOptions);
        },
        resolvedMentionState() {
            return resolveSkillMentionState(this.draft, this.skillMentionLookup);
        },
        selectedMentionSkill() {
            return this.resolvedMentionState.primarySkill || null;
        },
        showChatModelSelector() {
            return (
                Boolean(this.adapter) && typeof this.adapter.fetchChatModelOptions === 'function'
            );
        },
        effectiveHistoryAdapter() {
            return this.historyAdapter || this.adapter;
        },
        selectedConversation() {
            if (!this.selectedConversationId) {
                return null;
            }
            return (
                this.conversationItems.find(item => item.id === this.selectedConversationId) || null
            );
        },
        selectedConversationInputDisabled() {
            const sessionType = String(this.selectedConversation?.sessionType || '')
                .trim()
                .toUpperCase();
            return sessionType === 'CHANNEL_CHAT' || sessionType === 'GENERAL_CHAT_V2';
        },
        effectiveChatModelOptions() {
            const options = Array.isArray(this.chatModelOptions) ? [...this.chatModelOptions] : [];
            if (this.selectedChatModelAvailable !== false || this.selectedChatModelId == null) {
                return options;
            }
            const exists = options.some(
                option =>
                    this.normalizeChatModelId(option?.value ?? option?.id) ===
                    this.selectedChatModelId
            );
            if (exists) {
                return options;
            }
            return [
                {
                    value: this.selectedChatModelId,
                    label: this.selectedChatModelDisplayName || `模型 #${this.selectedChatModelId}`,
                    description: '当前会话绑定模型已不可用，请重新选择',
                    disabled: true,
                },
                ...options,
            ];
        },
        filteredConversationItems() {
            const keyword = (this.conversationSearchKeyword || '').trim().toLowerCase();
            if (!keyword) {
                return this.conversationItems;
            }
            return this.conversationItems.filter(item => {
                const searchText = [
                    item?.title,
                    item?.name,
                    item?.subtitle,
                    item?.titleSummary,
                    item?.sessionTypeLabel,
                    item?.scopeDisplayName,
                    item?.sourceLabel,
                ]
                    .filter(Boolean)
                    .join(' ')
                    .toLowerCase();
                return searchText.includes(keyword);
            });
        },
        isFilePanelOpen() {
            return this.filePanelOpen;
        },
        resolvedConversationScopeId() {
            if (this.isExpertPackageAgentSettings) {
                return String(this.defaultKnowledge || '').trim();
            }
            if (this.showKnowledgeSelect) {
                return String(this.selectedKnowledge || '').trim();
            }
            return String(this.defaultKnowledge || this.selectedKnowledge || '').trim();
        },
        isPreviewPanelVisible() {
            return (
                Boolean(this.activeHtml) ||
                (this.showFileListPanel && this.filePanelOpen) ||
                this.agentSettingsPanelOpen
            );
        },
        isSkillAgentSettings() {
            return Boolean(this.activeSkillContext?.id);
        },
        normalizedAgentSettingsType() {
            const type = String(this.agentSettingsContext?.type || '').trim();
            if (['agent', 'expert-package'].includes(type)) {
                return type;
            }
            return 'hidden';
        },
        showAgentSettingsButton() {
            return this.normalizedAgentSettingsType !== 'hidden';
        },
        isExpertPackageAgentSettings() {
            return this.normalizedAgentSettingsType === 'expert-package';
        },
        isReadonlyAgentSettings() {
            return this.isSkillAgentSettings || this.isExpertPackageAgentSettings;
        },
        showAgentSettingsChannels() {
            return this.normalizedAgentSettingsType === 'agent';
        },
        agentSettingsPanelLabel() {
            return this.isExpertPackageAgentSettings ? '专家包信息' : 'Agent 设置';
        },
        agentSettingsDisplayName() {
            if (this.isExpertPackageAgentSettings) {
                return this.agentSettingsContext?.displayName || this.headerTitle || '专家技能包';
            }
            if (this.isSkillAgentSettings) {
                return (
                    this.activeSkillContext?.displayName ||
                    this.activeSkillContext?.runtimeSkillName ||
                    this.headerTitle ||
                    '技能对话'
                );
            }
            return this.headerTitle || getAgentDisplayName();
        },
        agentSettingsDescription() {
            if (this.isExpertPackageAgentSettings) {
                return this.agentSettingsContext?.description || this.headerStatusText || '';
            }
            if (this.isSkillAgentSettings) {
                return this.activeSkillContext?.description || this.headerStatusText || '';
            }
            return this.headerStatusText || '';
        },
        agentSettingsAvatarVisual() {
            if (this.isExpertPackageAgentSettings) {
                const icon = String(
                    this.agentSettingsContext?.icon || this.headerIcon || ''
                ).trim();
                if (isImageSource(icon)) {
                    return { type: 'image', value: icon };
                }
                if (isMaterialSymbolName(icon)) {
                    return { type: 'material', value: icon };
                }
                if (icon) {
                    return { type: 'emoji', value: icon };
                }
                return { type: 'material', value: 'psychology' };
            }
            const preview = String(this.agentSettingsAvatarPreviewUrl || '').trim();
            if (preview) {
                return { type: 'image', value: preview };
            }
            const template = agentConfigState.template || {};
            const customAvatarUrl = String(template.avatarUrl || '').trim();
            if (customAvatarUrl) {
                return { type: 'image', value: customAvatarUrl };
            }
            const templateIcon = String(template.icon || '').trim();
            if (isImageSource(templateIcon)) {
                return { type: 'image', value: templateIcon };
            }
            if (isMaterialSymbolName(templateIcon)) {
                return { type: 'material', value: templateIcon };
            }
            if (templateIcon) {
                return { type: 'emoji', value: templateIcon };
            }
            return { type: 'image', value: '/general-chat-assistant-logo.png' || defaultAvatarSrc };
        },
        agentSettingsSkills() {
            return Array.isArray(this.agentSettingsContext?.skills)
                ? this.agentSettingsContext.skills
                : [];
        },
        agentSettingsTools() {
            return Array.isArray(this.agentSettingsContext?.tools)
                ? this.agentSettingsContext.tools
                : [];
        },
        effectiveHeaderStatusText() {
            if (!this.useRuntimeHeaderStatus) {
                return this.headerStatusText;
            }
            const tokenQuota = this.currentUserTokenQuota;
            const remainingTokens = Number(tokenQuota?.remainingTokens || 0);
            if (tokenQuota?.enabled && !tokenQuota?.unlimited && remainingTokens <= 0) {
                return 'Token 额度已用尽';
            }
            if (this.selectedChatModelAvailable === false) {
                return '当前模型不可用，请重新选择';
            }
            if (this.loadingChatModelOptions) {
                return '正在检查模型状态';
            }
            if (this.showChatModelSelector && this.chatModelOptions.length === 0) {
                return '未配置可用模型';
            }
            if (tokenQuota?.enabled && !tokenQuota?.unlimited) {
                return `剩余 ${this.formatTokenCount(remainingTokens)} Tokens`;
            }
            return this.headerStatusText || '在线可用';
        },
        effectiveHeaderStatusTone() {
            if (!this.useRuntimeHeaderStatus) {
                return 'success';
            }
            const tokenQuota = this.currentUserTokenQuota;
            const remainingTokens = Number(tokenQuota?.remainingTokens || 0);
            if (tokenQuota?.enabled && !tokenQuota?.unlimited && remainingTokens <= 0) {
                return 'danger';
            }
            if (this.selectedChatModelAvailable === false) {
                return 'warning';
            }
            if (this.loadingChatModelOptions) {
                return 'loading';
            }
            if (this.showChatModelSelector && this.chatModelOptions.length === 0) {
                return 'warning';
            }
            return 'success';
        },
        conversationDividerClass() {
            if (!this.showConversationDivider) {
                return '';
            }
            return 'border-x border-[#e9eef5]';
        },
        isSidebarCollapseControlled() {
            return this.sidebarCollapsed !== null;
        },
        effectiveSidebarCollapsed() {
            return this.isSidebarCollapseControlled
                ? Boolean(this.sidebarCollapsed)
                : this.isSidebarCollapsed;
        },
        shouldRenderHistorySidebar() {
            return !(this.hideSidebarWhenCollapsed && this.effectiveSidebarCollapsed);
        },
    },
    mounted() {
        this.setupSidebarResponsive();
        this.setupGlobalFileDrop();
        this.ensureRuntimeHeaderStatusContext();
        this.initializeSessionId();
        this.loadChatModelOptions();
        this.loadConversationItems();
        // 如果配置了 resetOnMount，页面加载时清除缓存的会话，强制从新对话开始
        if (this.resetOnMount) {
            this.startNewChat();
        }
    },
    beforeUnmount() {
        this.teardownSidebarResponsive();
        this.teardownGlobalFileDrop();
        this.setAgentSettingsAvatarPreviewUrl('');
        this.stopAgentSettingsWechatPolling();
        this.stopAgentSettingsDingtalkRegisterPolling();
        this.stopAgentSettingsWecomPolling();
    },
    watch: {
        selectedKnowledge() {
            if (!this.showKnowledgeSelect) {
                return;
            }
            this.loadConversationItems();
        },
        knowledgeOptions: {
            immediate: true,
            handler(options) {
                if (!this.showKnowledgeSelect) {
                    return;
                }
                const list = Array.isArray(options) ? options : [];
                const values = list
                    .map(option => this.extractKnowledgeOptionValue(option))
                    .filter(Boolean);
                if (!values.length) {
                    this.selectedKnowledge = '';
                    return;
                }
                const selectedValue = String(this.selectedKnowledge || '').trim();
                if (!selectedValue || !values.includes(selectedValue)) {
                    this.selectedKnowledge = values[0];
                }
            },
        },
        defaultKnowledge: {
            immediate: true,
            handler(value) {
                if (this.showKnowledgeSelect) {
                    return;
                }
                this.selectedKnowledge = String(value || '').trim();
            },
        },
        enableSkillMention: {
            immediate: true,
            handler(enabled) {
                if (!enabled) {
                    this.mentionSkills = [];
                    return;
                }
                this.loadMentionSkills();
            },
        },
        adapter: {
            handler() {
                this.loadChatModelOptions();
            },
        },
        selectedMentionSkill(skill) {
            if (!skill?.id) {
                return;
            }
            this.emitSkillContextChange(skill);
        },
        selectedConversationId() {
            this.syncSelectedChatModelFromCurrentContext();
        },
        headerTitle: {
            immediate: true,
            handler() {
                this.syncAgentSettingsName();
            },
        },
        activeSkillContext() {
            this.syncAgentSettingsName();
        },
        agentSettingsContext: {
            deep: true,
            handler() {
                if (!this.showAgentSettingsButton) {
                    if (this.agentSettingsPanelOpen) {
                        this.closePreview();
                    }
                    return;
                }
                this.syncAgentSettingsName();
            },
        },
    },
    methods: {
        openAssistantConfig() {
            this.router.push(ROUTE_PATHS.frontAgentConfig);
        },
        handleAssistantSummaryClick() {
            this.$emit('assistant-summary-click');
            this.startNewChat();
        },
        syncAgentSettingsName() {
            if (this.isReadonlyAgentSettings) {
                this.agentSettingsName = this.agentSettingsDisplayName;
                return;
            }
            this.agentSettingsName = this.headerTitle || getAgentDisplayName();
        },
        setAgentSettingsAvatarPreviewUrl(nextUrl = '') {
            if (
                this.agentSettingsAvatarPreviewUrl &&
                this.agentSettingsAvatarPreviewUrl.startsWith('blob:')
            ) {
                URL.revokeObjectURL(this.agentSettingsAvatarPreviewUrl);
            }
            this.agentSettingsAvatarPreviewUrl = nextUrl;
        },
        triggerAgentSettingsAvatarSelect() {
            if (this.isReadonlyAgentSettings || this.agentSettingsAvatarUploading) {
                return;
            }
            this.$refs.agentSettingsAvatarInput?.click();
        },
        validateAgentSettingsAvatarFile(file) {
            return validateImageFile(file, {
                maxBytes: 2 * 1024 * 1024,
            });
        },
        async handleAgentSettingsAvatarChange(event) {
            const input = event?.target;
            const file = input?.files?.[0];
            if (input) {
                input.value = '';
            }
            if (!file || this.agentSettingsAvatarUploading || this.isReadonlyAgentSettings) {
                return;
            }
            const validationError = this.validateAgentSettingsAvatarFile(file);
            if (validationError) {
                this.agentSettingsAvatarError = validationError;
                return;
            }
            this.agentSettingsAvatarError = '';
            const editedImage = await openImageEditorModal({
                title: '调整助手头像',
                confirmText: '应用头像',
                file,
                aspectRatio: 1,
                cropShape: 'circle',
                outputSize: 512,
                preferredMimeType: 'image/jpeg',
                preferredExtension: '.jpg',
                maxOutputBytes: 400 * 1024,
                initialQuality: 0.88,
                minQuality: 0.7,
                minZoom: 0.7,
                maxZoom: 3.2,
                fileNameStem: 'assistant-avatar',
                helperText: '拖动图片调整显示区域，系统会导出为 512 x 512 的头像。',
            });
            if (!editedImage?.file) {
                return;
            }
            this.setAgentSettingsAvatarPreviewUrl(
                URL.createObjectURL(editedImage.blob || editedImage.file)
            );
            this.agentSettingsAvatarUploading = true;
            try {
                await uploadCurrentUserAgentAvatar(editedImage.file, () =>
                    this.$emit('unauthorized')
                );
                this.setAgentSettingsAvatarPreviewUrl('');
                await fetchAgentConfig({
                    force: true,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                showToast('助手头像已更新');
            } catch (error) {
                this.setAgentSettingsAvatarPreviewUrl('');
                this.agentSettingsAvatarError = error?.message || '头像上传失败，请稍后重试';
            } finally {
                this.agentSettingsAvatarUploading = false;
            }
        },
        toggleAgentSettingsPanel() {
            if (!this.showAgentSettingsButton) {
                return;
            }
            if (this.agentSettingsPanelOpen) {
                this.closePreview();
                return;
            }
            if (this.previewClosingTimer) {
                clearTimeout(this.previewClosingTimer);
                this.previewClosingTimer = null;
            }
            this.activeCitation = null;
            this.filePanelOpen = false;
            this.activeHtml = null;
            this.agentSettingsPanelOpen = true;
            this.agentSettingsError = '';
            this.agentSettingsAvatarError = '';
            this.agentSettingsChannelStatus = '';
            this.agentSettingsWechatQrModalOpen = false;
            this.agentSettingsWechatQrCode = '';
            this.agentSettingsDingtalkQrModalOpen = false;
            this.agentSettingsDingtalkQrCode = '';
            this.agentSettingsDingtalkRegisterStatus = '';
            this.stopAgentSettingsDingtalkRegisterPolling();
            this.syncAgentSettingsName();
            if (this.showAgentSettingsChannels) {
                this.loadAgentSettingsChannels();
            } else {
                this.agentSettingsChannelConfigs = [];
                this.agentSettingsBoundChannels = [];
            }
        },
        async saveAgentSettings() {
            if (this.isReadonlyAgentSettings || this.agentSettingsSaving) {
                return;
            }
            const agentName = String(this.agentSettingsName || '').trim();
            this.agentSettingsSaving = true;
            this.agentSettingsError = '';
            try {
                await updateCurrentUserAgentProfile({ agentName }, () =>
                    this.$emit('unauthorized')
                );
                await fetchAgentConfig({
                    force: true,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                this.syncAgentSettingsName();
                showToast('Agent 设置已保存');
            } catch (error) {
                this.agentSettingsError = error?.message || '保存失败，请稍后重试';
            } finally {
                this.agentSettingsSaving = false;
            }
        },
        async loadAgentSettingsChannels() {
            try {
                const [data, bindingData] = await Promise.all([
                    listChannelConfigs(() => this.$emit('unauthorized')),
                    listMyChannelBindings(() => this.$emit('unauthorized')),
                ]);
                const records = Array.isArray(data?.items)
                    ? data.items
                    : Array.isArray(data)
                      ? data
                      : [];
                this.agentSettingsChannelConfigs = records.filter(item =>
                    ['weixin', 'wecom', 'dingtalk'].includes(String(item?.channelType || ''))
                );
                const bindings = Array.isArray(bindingData?.items) ? bindingData.items : [];
                this.agentSettingsBoundChannels = bindings
                    .filter(item =>
                        ['weixin', 'wecom', 'dingtalk'].includes(String(item?.channelType || ''))
                    )
                    .map(item => this.normalizeAgentSettingsBoundChannel(item));
            } catch (error) {
                this.agentSettingsChannelConfigs = [];
                this.agentSettingsBoundChannels = [];
            }
        },
        normalizeAgentSettingsBoundChannel(item) {
            const type = String(item?.channelType || '');
            const labels = {
                weixin: '微信',
                wecom: '企业微信',
                dingtalk: '钉钉',
            };
            const logos = {
                weixin: CHANNEL_LOGO_SOURCES.weixin,
                wecom: CHANNEL_LOGO_SOURCES.wecom,
                dingtalk: CHANNEL_LOGO_SOURCES.dingtalk,
            };
            return {
                channelId: item?.channelId,
                channelType: type,
                label: labels[type] || item?.name || type,
                logo: logos[type] || '',
                connected: Boolean(item?.connected),
                statusLabel: String(item?.statusLabel || (item?.connected ? '正常' : '未连接')),
                routeLabel: item?.routeType === 'GENERAL_CHAT' ? '通用聊天' : '已绑定',
            };
        },
        async closeAgentSettingsChannel(channel) {
            const channelId = channel?.channelId;
            if (!channelId || this.agentSettingsClosingChannelIds.includes(channelId)) {
                return;
            }
            const confirmed = await confirm({
                title: `关闭${channel?.label || '渠道'}？`,
                message: '关闭后将停止接收该渠道消息，再次使用需要重新授权。',
                confirmText: '关闭',
                cancelText: '取消',
                destructive: true,
            });
            if (!confirmed) {
                return;
            }
            this.agentSettingsClosingChannelIds.push(channelId);
            try {
                await closeMyChannelBinding(channelId, () => this.$emit('unauthorized'));
                await this.loadAgentSettingsChannels();
                showToast(`${channel?.label || '渠道'}已关闭`);
            } catch (error) {
                showToast(error?.message || '渠道关闭失败', 'error');
            } finally {
                this.agentSettingsClosingChannelIds = this.agentSettingsClosingChannelIds.filter(
                    id => id !== channelId
                );
            }
        },
        findAgentSettingsChannel(type) {
            return (
                this.agentSettingsChannelConfigs.find(
                    item => String(item?.channelType || '') === type
                ) || null
            );
        },
        async ensureAgentSettingsChannel(type) {
            let channel = this.findAgentSettingsChannel(type);
            if (channel?.id) {
                return channel;
            }
            await this.loadAgentSettingsChannels();
            channel = this.findAgentSettingsChannel(type);
            if (channel?.id) {
                return channel;
            }
            const preset = AGENT_SETTINGS_CHANNEL_DEFAULTS[type];
            if (!preset) {
                throw new Error('暂不支持该渠道授权');
            }
            this.agentSettingsChannelStatus =
                type === 'wecom' ? '正在准备企业微信扫码授权...' : '正在初始化渠道配置...';
            const created = await createChannelConfig(
                {
                    ...preset,
                    routeType: 'GENERAL_CHAT',
                    routeTargetId: null,
                    botPrefix: null,
                    enabled: true,
                },
                () => this.$emit('unauthorized')
            );
            await this.loadAgentSettingsChannels();
            return created;
        },
        async authorizeAgentSettingsChannel(type) {
            if (type === 'weixin') {
                await this.authorizeAgentSettingsWechat();
                return;
            }
            if (type === 'wecom') {
                await this.authorizeAgentSettingsWecom();
                return;
            }
            if (type === 'dingtalk') {
                await this.authorizeAgentSettingsDingtalk();
            }
        },
        normalizeWechatQrCode(raw) {
            const value = String(raw || '').trim();
            if (!value) {
                return '';
            }
            if (
                value.startsWith('http://') ||
                value.startsWith('https://') ||
                value.startsWith('data:')
            ) {
                return value;
            }
            return `data:image/png;base64,${value}`;
        },
        async authorizeAgentSettingsWechat() {
            this.stopAgentSettingsWechatPolling();
            this.stopAgentSettingsDingtalkRegisterPolling();
            this.agentSettingsDingtalkQrModalOpen = false;
            this.agentSettingsDingtalkQrCode = '';
            this.agentSettingsWechatQrCode = '';
            this.agentSettingsWechatQrModalOpen = true;
            this.agentSettingsChannelStatus = '正在生成微信授权二维码...';
            try {
                const channel = await this.ensureAgentSettingsChannel('weixin');
                await saveMyChannelBinding(
                    channel.id,
                    { routeType: 'GENERAL_CHAT', routeTargetId: null },
                    () => this.$emit('unauthorized')
                );
                await this.loadAgentSettingsChannels();
                const data = await beginWechatLogin(channel.id, () => this.$emit('unauthorized'));
                const imgContent =
                    data?.qrcodeImageContent || data?.qrcode_img_content || data?.qrcode_img || '';
                this.agentSettingsWechatQrCode = this.normalizeWechatQrCode(imgContent);
                this.agentSettingsChannelStatus = this.agentSettingsWechatQrCode
                    ? '请使用微信扫码授权，授权完成后会自动绑定到通用聊天。'
                    : '微信二维码生成失败，请稍后重试。';
                this.startAgentSettingsWechatPolling(channel.id);
            } catch (error) {
                this.agentSettingsChannelStatus = error?.message || '微信授权二维码获取失败';
                this.agentSettingsWechatQrModalOpen = false;
            }
        },
        startAgentSettingsWechatPolling(channelId) {
            this.stopAgentSettingsWechatPolling();
            this.agentSettingsWechatStatusTimer = window.setInterval(async () => {
                try {
                    const data = await getWechatLoginStatus(channelId, () =>
                        this.$emit('unauthorized')
                    );
                    if (data?.loggedIn) {
                        this.agentSettingsWechatQrModalOpen = false;
                        this.agentSettingsWechatQrCode = '';
                        this.agentSettingsChannelStatus = '';
                        await this.loadAgentSettingsChannels();
                        showToast('微信已授权，并已绑定到通用聊天。');
                        this.stopAgentSettingsWechatPolling();
                    }
                } catch (error) {
                    this.agentSettingsChannelStatus = error?.message || '微信授权状态检查失败';
                    this.stopAgentSettingsWechatPolling();
                }
            }, 2500);
        },
        stopAgentSettingsWechatPolling() {
            if (this.agentSettingsWechatStatusTimer) {
                window.clearInterval(this.agentSettingsWechatStatusTimer);
                this.agentSettingsWechatStatusTimer = null;
            }
        },
        closeAgentSettingsWechatQrModal() {
            this.agentSettingsWechatQrModalOpen = false;
            this.agentSettingsWechatQrCode = '';
            this.stopAgentSettingsWechatPolling();
        },
        parseChannelConfigJson(configJson) {
            const raw = String(configJson || '').trim();
            if (!raw) {
                return {};
            }
            try {
                const parsed = JSON.parse(raw);
                return parsed && typeof parsed === 'object' ? parsed : {};
            } catch (error) {
                return {};
            }
        },
        async authorizeAgentSettingsDingtalk() {
            this.stopAgentSettingsDingtalkRegisterPolling();
            this.agentSettingsWechatQrModalOpen = false;
            this.agentSettingsWechatQrCode = '';
            this.agentSettingsDingtalkQrCode = '';
            this.agentSettingsDingtalkRegisterStatus = 'waiting';
            this.agentSettingsDingtalkQrModalOpen = true;
            this.agentSettingsChannelStatus = '正在生成钉钉授权二维码...';
            try {
                const channel = await this.ensureAgentSettingsChannel('dingtalk');
                await saveMyChannelBinding(
                    channel.id,
                    { routeType: 'GENERAL_CHAT', routeTargetId: null },
                    () => this.$emit('unauthorized')
                );
                await this.loadAgentSettingsChannels();
                const data = await beginDingtalkRegister(channel.id, () =>
                    this.$emit('unauthorized')
                );
                const sessionId = data?.sessionId || data?.session_id || '';
                if (!sessionId) {
                    throw new Error('钉钉注册会话为空');
                }
                await this.pollAgentSettingsDingtalkRegister(channel.id, sessionId);
                this.agentSettingsDingtalkRegisterTimer = window.setInterval(() => {
                    this.pollAgentSettingsDingtalkRegister(channel.id, sessionId);
                }, 2000);
            } catch (error) {
                this.agentSettingsChannelStatus = error?.message || '钉钉授权二维码获取失败';
                this.agentSettingsDingtalkQrModalOpen = false;
            }
        },
        async pollAgentSettingsDingtalkRegister(channelId, sessionId) {
            if (!channelId || !sessionId) {
                this.stopAgentSettingsDingtalkRegisterPolling();
                return;
            }
            try {
                const data = await getDingtalkRegisterStatus(channelId, sessionId, () =>
                    this.$emit('unauthorized')
                );
                const status = String(data?.status || 'waiting').toLowerCase();
                this.agentSettingsDingtalkRegisterStatus = status;
                const qrCode = data?.qrcode_img || data?.qrcode_url || '';
                if (qrCode) {
                    this.agentSettingsDingtalkQrCode = this.normalizeWechatQrCode(qrCode);
                    this.agentSettingsChannelStatus = '';
                }
                if (status === 'confirmed') {
                    this.stopAgentSettingsDingtalkRegisterPolling();
                    const clientId = data?.clientId || data?.client_id || '';
                    const clientSecret = data?.clientSecret || data?.client_secret || '';
                    if (!clientId || !clientSecret) {
                        throw new Error('钉钉授权未返回有效应用凭证');
                    }
                    await this.saveAgentSettingsDingtalkCredential(
                        channelId,
                        clientId,
                        clientSecret
                    );
                    this.agentSettingsDingtalkQrModalOpen = false;
                    this.agentSettingsDingtalkQrCode = '';
                    this.agentSettingsChannelStatus = '';
                    showToast('钉钉已授权，并已绑定到通用聊天。');
                    return;
                }
                if (status === 'expired' || status === 'denied') {
                    this.stopAgentSettingsDingtalkRegisterPolling();
                    this.agentSettingsDingtalkQrModalOpen = false;
                    this.agentSettingsChannelStatus =
                        status === 'expired' ? '钉钉授权二维码已过期。' : '钉钉授权已取消。';
                }
            } catch (error) {
                this.agentSettingsChannelStatus = error?.message || '钉钉授权状态检查失败';
                this.stopAgentSettingsDingtalkRegisterPolling();
            }
        },
        async saveAgentSettingsDingtalkCredential(channelId, clientId, clientSecret) {
            const channel =
                this.findAgentSettingsChannel('dingtalk') ||
                (await this.ensureAgentSettingsChannel('dingtalk'));
            const existingConfig = this.parseChannelConfigJson(channel?.configJson);
            const config = {
                ...existingConfig,
                robotCode: String(
                    existingConfig?.robotCode || existingConfig?.robot_code || ''
                ).trim(),
                clientId: String(clientId || '').trim(),
                clientSecret: String(clientSecret || '').trim(),
            };
            if (!String(config.replyTitle || '').trim()) {
                config.replyTitle = '灵洲智能体';
            }
            const payload = {
                name: channel?.name || AGENT_SETTINGS_CHANNEL_DEFAULTS.dingtalk.name,
                channelType: 'dingtalk',
                routeType: 'GENERAL_CHAT',
                routeTargetId: null,
                botPrefix: channel?.botPrefix || null,
                enabled: true,
                description:
                    channel?.description || AGENT_SETTINGS_CHANNEL_DEFAULTS.dingtalk.description,
                configJson: JSON.stringify(config),
            };
            await updateChannelConfig(channelId, payload, () => this.$emit('unauthorized'));
            await saveMyChannelBinding(
                channelId,
                { routeType: 'GENERAL_CHAT', routeTargetId: null },
                () => this.$emit('unauthorized')
            );
            await this.loadAgentSettingsChannels();
        },
        stopAgentSettingsDingtalkRegisterPolling() {
            if (this.agentSettingsDingtalkRegisterTimer) {
                window.clearInterval(this.agentSettingsDingtalkRegisterTimer);
                this.agentSettingsDingtalkRegisterTimer = null;
            }
        },
        closeAgentSettingsDingtalkQrModal() {
            this.agentSettingsDingtalkQrModalOpen = false;
            this.agentSettingsDingtalkQrCode = '';
            this.stopAgentSettingsDingtalkRegisterPolling();
        },
        loadWecomSDK() {
            return new Promise((resolve, reject) => {
                if (window.WecomAIBotSDK || wecomSdkLoaded) {
                    resolve();
                    return;
                }
                const script = document.createElement('script');
                script.src = WECOM_SDK_URL;
                script.async = true;
                script.onload = () => {
                    wecomSdkLoaded = true;
                    resolve();
                };
                script.onerror = () => reject(new Error('企业微信授权 SDK 加载失败'));
                document.body.appendChild(script);
            });
        },
        async authorizeAgentSettingsWecom() {
            this.agentSettingsWechatQrModalOpen = false;
            this.agentSettingsWechatQrCode = '';
            this.agentSettingsDingtalkQrModalOpen = false;
            this.agentSettingsDingtalkQrCode = '';
            this.stopAgentSettingsDingtalkRegisterPolling();
            this.agentSettingsChannelStatus = '正在打开企业微信授权窗口...';
            try {
                const channel = await this.ensureAgentSettingsChannel('wecom');
                await saveMyChannelBinding(
                    channel.id,
                    { routeType: 'GENERAL_CHAT', routeTargetId: null },
                    () => this.$emit('unauthorized')
                );
                await this.loadAgentSettingsChannels();
                await this.loadWecomSDK();
                const sdk = window.WecomAIBotSDK;
                if (!sdk || typeof sdk.openBotInfoAuthWindow !== 'function') {
                    throw new Error('未检测到可用的企业微信授权 SDK');
                }
                const result = sdk.openBotInfoAuthWindow({ source: WECOM_SOURCE });
                if (!result || typeof result.then !== 'function') {
                    return;
                }
                result.then(
                    async bot => {
                        try {
                            if (!bot?.botid || !bot?.secret) {
                                throw new Error('企业微信授权未返回有效 bot_id/secret');
                            }
                            await saveMyWecomBinding(
                                channel.id,
                                {
                                    botId: String(bot.botid || '').trim(),
                                    secret: String(bot.secret || '').trim(),
                                    source: WECOM_SOURCE,
                                },
                                () => this.$emit('unauthorized')
                            );
                            await getMyChannelBinding(channel.id, () => this.$emit('unauthorized'));
                            await this.loadAgentSettingsChannels();
                            this.agentSettingsChannelStatus = '';
                            showToast('企业微信已授权，并已绑定到通用聊天。');
                            this.startAgentSettingsWecomPolling(channel.id);
                        } catch (error) {
                            this.agentSettingsChannelStatus =
                                error?.message || '企业微信绑定失败，请重试。';
                        }
                    },
                    error => {
                        if (error?.code === 'WINDOW_BLOCKED') {
                            this.agentSettingsChannelStatus = '授权窗口被拦截，请允许弹窗后重试。';
                            return;
                        }
                        if (error?.code === 'CANCELLED') {
                            this.agentSettingsChannelStatus = '企业微信授权已取消。';
                            return;
                        }
                        this.agentSettingsChannelStatus =
                            error?.message || error?.code || '企业微信授权失败';
                    }
                );
            } catch (error) {
                this.agentSettingsChannelStatus = error?.message || '企业微信授权失败';
            }
        },
        startAgentSettingsWecomPolling(channelId) {
            this.stopAgentSettingsWecomPolling();
            this.agentSettingsWecomStatusTimer = window.setInterval(async () => {
                try {
                    const data = await getWecomRuntimeStatus(channelId, () =>
                        this.$emit('unauthorized')
                    );
                    if (data?.runtimeBound) {
                        if (data?.connected || data?.authenticated) {
                            this.agentSettingsChannelStatus = '';
                            await this.loadAgentSettingsChannels();
                            showToast('企业微信已连接。');
                            this.stopAgentSettingsWecomPolling();
                        }
                    }
                } catch (error) {
                    this.stopAgentSettingsWecomPolling();
                }
            }, 3000);
        },
        stopAgentSettingsWecomPolling() {
            if (this.agentSettingsWecomStatusTimer) {
                window.clearInterval(this.agentSettingsWecomStatusTimer);
                this.agentSettingsWecomStatusTimer = null;
            }
        },
        waitForDelay(duration = 0) {
            const normalizedDuration = Number(duration) || 0;
            if (normalizedDuration <= 0) {
                return Promise.resolve();
            }
            return new Promise(resolve => {
                window.setTimeout(resolve, normalizedDuration);
            });
        },
        parseStreamErrorResponse(response) {
            const status = Number(response?.status) || 0;
            const payload = response && response.data !== undefined ? response.data : '';
            const fallback = this.streamStatusFallback(status);
            if (payload && typeof payload === 'object') {
                return (
                    payload.message || payload?.data?.message || payload?.data?.error || fallback
                );
            }
            const text = String(payload || '').trim();
            if (!text) {
                return fallback;
            }
            try {
                const parsed = JSON.parse(text);
                if (parsed && typeof parsed === 'object') {
                    return (
                        parsed.message || parsed?.data?.message || parsed?.data?.error || fallback
                    );
                }
            } catch (error) {
                // ignore and fallback to raw text below
            }
            return text || fallback;
        },
        streamStatusFallback(status) {
            if (status === 400) {
                return '请求参数不正确，请检查输入内容或当前配置。';
            }
            if (status === 401) {
                return '登录已过期或模型服务认证失败，请重新登录或检查鉴权配置。';
            }
            if (status === 403) {
                return '当前请求被拒绝，请检查权限或模型服务访问策略。';
            }
            if (status === 404) {
                return '请求的接口不存在，请检查服务地址或接口路径配置。';
            }
            if (status === 429) {
                return '请求过于频繁，请稍后重试。';
            }
            if (status >= 500) {
                return '服务暂时不可用，请稍后重试。';
            }
            return '请求失败';
        },
        getSessionStorageKey() {
            return this.sessionStorageKey || this.adapter?.sessionStorageKey || '';
        },
        async ensureRuntimeHeaderStatusContext() {
            if (!this.useRuntimeHeaderStatus) {
                return;
            }
            try {
                await ensureCurrentUserLoaded({
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
            } catch (error) {
                // Ignore profile loading failure here and keep chat usable.
            }
        },
        formatTokenCount(value) {
            const number = Number(value);
            if (!Number.isFinite(number)) {
                return '0';
            }
            return new Intl.NumberFormat('zh-CN').format(Math.max(0, Math.trunc(number)));
        },
        setupGlobalFileDrop() {
            if (typeof window === 'undefined') {
                return;
            }
            window.addEventListener('dragenter', this.handleWindowDragEnter);
            window.addEventListener('dragover', this.handleWindowDragOver);
            window.addEventListener('dragleave', this.handleWindowDragLeave);
            window.addEventListener('drop', this.handleWindowDrop);
        },
        teardownGlobalFileDrop() {
            if (typeof window === 'undefined') {
                return;
            }
            window.removeEventListener('dragenter', this.handleWindowDragEnter);
            window.removeEventListener('dragover', this.handleWindowDragOver);
            window.removeEventListener('dragleave', this.handleWindowDragLeave);
            window.removeEventListener('drop', this.handleWindowDrop);
        },
        isFileDragEvent(event) {
            const types = Array.from(event?.dataTransfer?.types || []);
            return types.includes('Files');
        },
        resetFileDropState() {
            this.showFileDropOverlay = false;
        },
        handleWindowDragEnter(event) {
            if (!this.enableAttachments || !this.isFileDragEvent(event)) {
                return;
            }
            event.preventDefault();
            this.showFileDropOverlay = true;
        },
        handleWindowDragOver(event) {
            if (!this.enableAttachments || !this.isFileDragEvent(event)) {
                return;
            }
            event.preventDefault();
            if (event.dataTransfer) {
                event.dataTransfer.dropEffect = 'copy';
            }
            this.showFileDropOverlay = true;
        },
        handleWindowDragLeave(event) {
            if (!this.enableAttachments || !this.isFileDragEvent(event)) {
                return;
            }
            event.preventDefault();
            const leavingViewport =
                event.clientX <= 0 ||
                event.clientY <= 0 ||
                event.clientX >= window.innerWidth ||
                event.clientY >= window.innerHeight;
            if (leavingViewport) {
                this.resetFileDropState();
            }
        },
        async handleWindowDrop(event) {
            if (!this.enableAttachments || !this.isFileDragEvent(event)) {
                return;
            }
            event.preventDefault();
            this.resetFileDropState();
            const files = Array.from(event.dataTransfer?.files || []);
            await this.handleSelectedFiles(files);
        },
        normalizeUploadExtension(fileName) {
            const normalized = String(fileName || '')
                .trim()
                .toLowerCase();
            const dotIndex = normalized.lastIndexOf('.');
            if (dotIndex < 0) {
                return '';
            }
            return normalized.slice(dotIndex);
        },
        formatUploadSize(bytes) {
            const size = Number(bytes || 0);
            if (size <= 0) {
                return '0 B';
            }
            if (size < 1024) {
                return `${size} B`;
            }
            if (size < 1024 * 1024) {
                return `${(size / 1024).toFixed(1)} KB`;
            }
            return `${(size / (1024 * 1024)).toFixed(1)} MB`;
        },
        resolveUploadLimitBytes(extension) {
            if (CHAT_IMAGE_EXTENSIONS.has(extension)) {
                return CHAT_MAX_IMAGE_BYTES;
            }
            if (extension === '.pdf') {
                return CHAT_MAX_PDF_BYTES;
            }
            if (CHAT_ARCHIVE_EXTENSIONS.has(extension)) {
                return CHAT_MAX_ARCHIVE_BYTES;
            }
            return CHAT_MAX_DOCUMENT_BYTES;
        },
        resolveUploadLimitLabel(extension) {
            if (CHAT_IMAGE_EXTENSIONS.has(extension)) {
                return '10MB';
            }
            if (extension === '.pdf') {
                return '50MB';
            }
            if (CHAT_ARCHIVE_EXTENSIONS.has(extension)) {
                return '100MB';
            }
            return '20MB';
        },
        validateSelectedFiles(files) {
            const safeFiles = Array.isArray(files) ? files.filter(Boolean) : [];
            const errors = [];
            if (!safeFiles.length) {
                return { validFiles: [], errors };
            }
            if (safeFiles.length > CHAT_MAX_BATCH_FILES) {
                errors.push(`单次最多上传 ${CHAT_MAX_BATCH_FILES} 个文件`);
                return { validFiles: [], errors };
            }
            const totalBytes = safeFiles.reduce((sum, file) => sum + Number(file?.size || 0), 0);
            if (totalBytes > CHAT_MAX_BATCH_BYTES) {
                errors.push(
                    `单次上传总大小不能超过 ${this.formatUploadSize(CHAT_MAX_BATCH_BYTES)}`
                );
                return { validFiles: [], errors };
            }
            const validFiles = [];
            safeFiles.forEach(file => {
                const extension = this.normalizeUploadExtension(file?.name);
                if (!CHAT_ALLOWED_EXTENSIONS.has(extension)) {
                    errors.push(`${file?.name || '未命名文件'} 类型不支持，仅支持图片、文档和 ZIP`);
                    return;
                }
                const limitBytes = this.resolveUploadLimitBytes(extension);
                if (Number(file?.size || 0) > limitBytes) {
                    errors.push(
                        `${file?.name || '未命名文件'} 超过大小限制，${extension
                            .replace('.', '')
                            .toUpperCase()} 文件最大 ${this.resolveUploadLimitLabel(extension)}`
                    );
                    return;
                }
                validFiles.push(file);
            });
            return { validFiles, errors };
        },
        async handleSelectedFiles(files) {
            if (!this.enableAttachments) {
                return;
            }
            const { validFiles, errors } = this.validateSelectedFiles(files);
            if (errors.length) {
                this.chatError = errors.join('；');
            }
            if (!validFiles.length) {
                return;
            }
            for (const file of validFiles) {
                await this.uploadFile(file);
            }
        },
        extractKnowledgeOptionValue(option) {
            if (option && typeof option === 'object') {
                const rawValue = option.value ?? option.id ?? option.kbId ?? '';
                const text = String(rawValue ?? '').trim();
                return text;
            }
            return String(option ?? '').trim();
        },
        async loadMentionSkills() {
            if (!this.enableSkillMention) {
                this.mentionSkills = [];
                return;
            }
            try {
                const skills = await getCurrentUserAgentSkillDetails(() =>
                    this.$emit('unauthorized')
                );
                this.mentionSkills = (Array.isArray(skills) ? skills : []).filter(
                    skill => skill?.id && skill?.runtimeSkillName
                );
            } catch (error) {
                this.mentionSkills = [];
            }
        },
        normalizeSkillContext(skill) {
            const rawId = skill?.id ?? skill?.scopeId ?? null;
            const normalizedId =
                typeof rawId === 'number' && Number.isFinite(rawId)
                    ? rawId
                    : String(rawId || '').trim();
            if (normalizedId == null || normalizedId === '') {
                return null;
            }
            return {
                id: normalizedId,
                displayName:
                    skill?.displayName || skill?.scopeDisplayName || skill?.runtimeSkillName || '',
                description: skill?.description || '',
                runtimeSkillName: skill?.runtimeSkillName || '',
            };
        },
        emitSkillContextChange(skill) {
            const normalized = this.normalizeSkillContext(skill);
            if (!normalized) {
                return;
            }
            this.$emit('skill-context-change', normalized);
        },
        normalizeRequestOptions(options) {
            if (!options || typeof options !== 'object' || Array.isArray(options)) {
                return {};
            }
            return { ...options };
        },
        buildRequestOptions(extraOptions = {}) {
            const mergedOptions = {
                ...this.normalizeRequestOptions(this.requestOptions),
                ...this.normalizeRequestOptions(extraOptions),
            };
            if (mergedOptions.personalAgent == null) {
                mergedOptions.personalAgent = false;
            }
            return mergedOptions;
        },
        resolveEffectiveSkillContext() {
            const resolvedMentionSkill = this.resolvedMentionState?.primarySkill || null;
            return (
                this.normalizeSkillContext(resolvedMentionSkill) ||
                this.normalizeSkillContext(this.selectedMentionSkill) ||
                this.normalizeSkillContext(this.activeSkillContext)
            );
        },
        normalizeChatModelId(value) {
            if (value == null) {
                return null;
            }
            if (typeof value === 'number' && Number.isFinite(value)) {
                return value;
            }
            const text = String(value || '').trim();
            if (!text || !/^\d+$/.test(text)) {
                return null;
            }
            return Number(text);
        },
        findChatModelOption(modelId) {
            if (modelId == null) {
                return null;
            }
            return (
                (Array.isArray(this.chatModelOptions) ? this.chatModelOptions : []).find(
                    option => this.normalizeChatModelId(option?.value ?? option?.id) === modelId
                ) || null
            );
        },
        getDefaultChatModelOption() {
            const options = Array.isArray(this.chatModelOptions) ? this.chatModelOptions : [];
            return options.find(option => option?.defaultModel) || options[0] || null;
        },
        applySelectedChatModel({ modelId = null, displayName = '', available = true } = {}) {
            this.selectedChatModelId = modelId;
            this.selectedChatModelDisplayName = String(displayName || '').trim();
            this.selectedChatModelAvailable = available !== false;
        },
        resetDraftChatModelSelection() {
            const defaultOption = this.getDefaultChatModelOption();
            this.applySelectedChatModel({
                modelId: this.normalizeChatModelId(defaultOption?.value ?? defaultOption?.id),
                displayName: defaultOption?.label || '',
                available: true,
            });
        },
        syncSelectedChatModelFromConversation(item) {
            const modelId = this.normalizeChatModelId(item?.chatModelId);
            if (modelId == null) {
                this.resetDraftChatModelSelection();
                return;
            }
            const option = this.findChatModelOption(modelId);
            this.applySelectedChatModel({
                modelId,
                displayName: item?.chatModelDisplayName || option?.label || '',
                available: item?.chatModelAvailable !== false,
            });
        },
        syncSelectedChatModelFromCurrentContext() {
            const currentConversation =
                this.conversationItems.find(item => item.id === this.selectedConversationId) ||
                null;
            if (currentConversation) {
                this.syncSelectedChatModelFromConversation(currentConversation);
                return;
            }
            this.resetDraftChatModelSelection();
        },
        async loadChatModelOptions() {
            if (!this.showChatModelSelector) {
                this.chatModelOptions = [];
                this.applySelectedChatModel({
                    modelId: null,
                    displayName: '',
                    available: true,
                });
                return;
            }
            this.loadingChatModelOptions = true;
            try {
                const options = await this.adapter.fetchChatModelOptions({
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                this.chatModelOptions = Array.isArray(options) ? options : [];
                this.syncSelectedChatModelFromCurrentContext();
            } catch (error) {
                this.chatModelOptions = [];
                this.syncSelectedChatModelFromCurrentContext();
            } finally {
                this.loadingChatModelOptions = false;
            }
        },
        updateConversationItemChatModel(conversationId, payload = {}) {
            if (!conversationId) {
                return;
            }
            this.conversationItems = this.conversationItems.map(item => {
                if (item.id !== conversationId) {
                    return item;
                }
                return ChatConversationBean.from({
                    ...item,
                    chatModelId: payload.chatModelId ?? null,
                    chatModelDisplayName: payload.chatModelDisplayName || '',
                    chatModelAvailable: payload.chatModelAvailable !== false,
                });
            });
        },
        async handleChatModelChange(value) {
            if (this.selectedConversationInputDisabled || this.sending || this.updatingChatModel) {
                return;
            }
            const nextModelId = this.normalizeChatModelId(value);
            const nextOption = this.findChatModelOption(nextModelId);
            if (nextModelId == null || !nextOption || nextOption.disabled) {
                return;
            }
            if (
                nextModelId === this.selectedChatModelId &&
                this.selectedChatModelAvailable !== false
            ) {
                return;
            }
            const previousSelection = {
                modelId: this.selectedChatModelId,
                displayName: this.selectedChatModelDisplayName,
                available: this.selectedChatModelAvailable,
            };
            if (!this.selectedConversationId) {
                this.applySelectedChatModel({
                    modelId: nextModelId,
                    displayName: nextOption.label || '',
                    available: true,
                });
                return;
            }
            if (!this.adapter || typeof this.adapter.updateConversationModel !== 'function') {
                return;
            }
            const currentConversation =
                this.conversationItems.find(item => item.id === this.selectedConversationId) ||
                null;
            this.applySelectedChatModel({
                modelId: nextModelId,
                displayName: nextOption.label || '',
                available: true,
            });
            this.updatingChatModel = true;
            try {
                const result = await this.adapter.updateConversationModel({
                    conversationId: this.selectedConversationId,
                    sessionType: currentConversation?.sessionType,
                    scopeId: currentConversation?.scopeId,
                    modelId: nextModelId,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const resolvedModelId =
                    this.normalizeChatModelId(result?.chatModelId) ?? nextModelId;
                const resolvedDisplayName = String(
                    result?.chatModelDisplayName || nextOption.label || ''
                ).trim();
                const resolvedAvailable = result?.chatModelAvailable !== false;
                this.applySelectedChatModel({
                    modelId: resolvedModelId,
                    displayName: resolvedDisplayName,
                    available: resolvedAvailable,
                });
                this.updateConversationItemChatModel(this.selectedConversationId, {
                    chatModelId: resolvedModelId,
                    chatModelDisplayName: resolvedDisplayName,
                    chatModelAvailable: resolvedAvailable,
                });
            } catch (error) {
                this.applySelectedChatModel(previousSelection);
                this.chatError = error?.message || '切换模型失败';
            } finally {
                this.updatingChatModel = false;
            }
        },
        handleDraftUpdate(value) {
            this.draft = String(value || '');
        },
        waitForNextFrame() {
            return new Promise(resolve => {
                if (
                    typeof window === 'undefined' ||
                    typeof window.requestAnimationFrame !== 'function'
                ) {
                    resolve();
                    return;
                }
                window.requestAnimationFrame(() => resolve());
            });
        },
        clearComposerDraft() {
            this.draft = '';
            this.$nextTick(() => {
                this.$refs.composerRef?.clearInput?.();
            });
        },
        focusComposerInput() {
            this.$nextTick(() => {
                requestAnimationFrame(() => {
                    this.$refs.composerRef?.focusInput?.();
                });
            });
        },
        resolveWelcomePrompt(action) {
            return String(action?.prompt ?? action?.label ?? '').trim();
        },
        async sendWelcomePrompt(action) {
            if (this.selectedConversationInputDisabled || this.sending) {
                return;
            }
            const promptText = this.resolveWelcomePrompt(action);
            if (!promptText) {
                return;
            }
            this.handleDraftUpdate(promptText);
            this.focusComposerInput();
            await this.$nextTick();
            await this.waitForNextFrame();
            await this.waitForDelay(180);
            await this.sendMessage();
        },
        async handleWelcomeAction(action) {
            const actionType = String(action?.actionType || '')
                .trim()
                .toLowerCase();
            if (actionType === 'send_prompt') {
                await this.sendWelcomePrompt(action);
                return;
            }
            this.$emit('welcome-action', action);
        },
        selectSkillMention(skill) {
            if (this.selectedConversationInputDisabled || !skill?.id) {
                return;
            }
            this.draft = applySkillMentionSelection(this.draft, skill);
            this.emitSkillContextChange(skill);
            this.focusComposerInput();
        },
        appendInsightMessage(payload) {
            if (this.selectedConversationInputDisabled) {
                return;
            }
            const skill = payload?.skill || null;
            if (!skill?.id) {
                return;
            }
            this.draft = appendSkillMentionMessage(this.draft, skill, payload?.messageContent);
            this.emitSkillContextChange(skill);
            this.focusComposerInput();
        },
        toggleSidebar() {
            this.setSidebarCollapsed(!this.effectiveSidebarCollapsed);
        },
        setSidebarCollapsed(collapsed) {
            const nextValue = Boolean(collapsed);
            this.isSidebarCollapsed = nextValue;
            this.$emit('update:sidebarCollapsed', nextValue);
        },
        handleSidebarMediaChange(event) {
            if (event?.matches) {
                this.setSidebarCollapsed(true);
            }
        },
        setupSidebarResponsive() {
            if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
                return;
            }
            const mediaQuery = window.matchMedia('(max-width: 1279px)');
            this.sidebarMediaQuery = mediaQuery;
            if (mediaQuery.matches) {
                this.setSidebarCollapsed(true);
            }
            if (typeof mediaQuery.addEventListener === 'function') {
                mediaQuery.addEventListener('change', this.handleSidebarMediaChange);
                return;
            }
            if (typeof mediaQuery.addListener === 'function') {
                mediaQuery.addListener(this.handleSidebarMediaChange);
            }
        },
        teardownSidebarResponsive() {
            const mediaQuery = this.sidebarMediaQuery;
            if (!mediaQuery) {
                return;
            }
            if (typeof mediaQuery.removeEventListener === 'function') {
                mediaQuery.removeEventListener('change', this.handleSidebarMediaChange);
            } else if (typeof mediaQuery.removeListener === 'function') {
                mediaQuery.removeListener(this.handleSidebarMediaChange);
            }
            this.sidebarMediaQuery = null;
        },
        applyConversationContext(item) {
            const sessionType = String(item?.sessionType || '').trim();
            const scopeId = item?.scopeId ?? null;
            const scopeDisplayName = String(item?.scopeDisplayName || '').trim();
            if (this.showKnowledgeSelect && scopeId != null) {
                this.selectedKnowledge = String(scopeId);
            } else if (!this.showKnowledgeSelect) {
                this.selectedKnowledge = String(this.defaultKnowledge || '').trim();
            }
            this.$emit('conversation-context-change', {
                sessionType,
                scopeId,
                scopeDisplayName,
            });
        },
        startNewChat() {
            this.invalidateActiveStreamView();
            this.messages = [];
            this.pendingFiles = [];
            this.clearComposerDraft();
            this.chatError = '';
            this.activeHtml = null;
            this.filePanelOpen = false;
            this.activeCitation = null;
            this.toolEventIndex = {};
            this.seenToolEvents = {};
            this.seenCitationEvents = {};
            this.activeAssistantIndex = null;
            this.activeAssistantByRequest = {};
            this.resetHistoryPaging();
            this.shouldAutoScroll = true;
            this.selectedConversationId = null;
            this.selectedKnowledge = this.showKnowledgeSelect
                ? this.selectedKnowledge
                : String(this.defaultKnowledge || '').trim();
            this.conversationItems = this.conversationItems.map(item =>
                ChatConversationBean.from({
                    ...item,
                    active: false,
                })
            );
            this.sessionId = '';
            const storageKey = this.getSessionStorageKey();
            if (storageKey) {
                window.localStorage.removeItem(storageKey);
            }
            this.$emit('reset-chat-context');
            this.requestScrollToLatest();
        },
        initializeSessionId() {
            if (!this.restoreInitialSession) {
                this.sessionId = '';
                this.selectedConversationId = null;
                return;
            }
            const storageKey = this.getSessionStorageKey();
            if (!storageKey) {
                this.sessionId = '';
                this.selectedConversationId = null;
                return;
            }
            const cachedSessionId = String(window.localStorage.getItem(storageKey) || '').trim();
            if (!cachedSessionId) {
                this.sessionId = '';
                this.selectedConversationId = null;
                return;
            }
            this.sessionId = cachedSessionId;
            this.selectedConversationId = cachedSessionId;
        },
        async loadConversationItems(options = {}) {
            const reloadMessages = options.reloadMessages !== false;
            const append = options.append === true;
            const pageNo = append ? this.conversationPageNo + 1 : 1;
            const adapter = this.effectiveHistoryAdapter;
            if (!adapter || typeof adapter.fetchConversationList !== 'function') {
                this.conversationItems = [];
                this.resetConversationPaging();
                return;
            }
            if (append && (!this.hasMoreConversations || this.loadingMoreConversations)) {
                return;
            }
            if (append) {
                this.loadingMoreConversations = true;
            }
            try {
                const { data } = await adapter.fetchConversationList({
                    selectedKnowledge: this.resolvedConversationScopeId,
                    pageNo,
                    pageSize: CHAT_SESSION_PAGE_SIZE,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const list = Array.isArray(data?.items) ? data.items : [];
                const normalizedItems = list.map(item => ChatConversationBean.fromApi(item));
                const nextItems = append
                    ? this.mergeConversationItems(this.conversationItems, normalizedItems)
                    : normalizedItems;
                const currentActiveExists = nextItems.some(
                    item => item.id === this.selectedConversationId
                );
                if (currentActiveExists) {
                    // keep current selected id
                } else {
                    // 保护：如果当前有活跃会话且有消息，不要清空（可能是新创建的会话还没同步到列表）
                    if (this.sessionId && this.messages.length > 0) {
                        console.log(
                            '[FrontChatWorkspace] 当前会话不在列表中但保持状态:',
                            this.sessionId
                        );
                    } else {
                        this.selectedConversationId = null;
                    }
                }
                const activeConversation =
                    nextItems.find(item => item.id === this.selectedConversationId) || null;
                if (activeConversation) {
                    this.applyConversationContext(activeConversation);
                }
                this.applyConversationPaging(data, normalizedItems.length, pageNo);
                this.conversationItems = nextItems.map(item =>
                    ChatConversationBean.from({
                        ...item,
                        active:
                            this.selectedConversationId !== null &&
                            item.id === this.selectedConversationId,
                    })
                );
                if (this.selectedConversationId) {
                    this.sessionId = this.selectedConversationId;
                    const storageKey = this.getSessionStorageKey();
                    if (storageKey) {
                        window.localStorage.setItem(storageKey, this.sessionId);
                    }
                    if (reloadMessages) {
                        await this.loadConversationMessages(this.selectedConversationId);
                    }
                } else {
                    // 保护：如果当前有活跃会话且有消息，不要清空（可能是新创建的会话还没同步到列表）
                    if (this.sessionId && this.messages.length > 0) {
                        console.log(
                            '[FrontChatWorkspace] 保持当前会话状态，不清空:',
                            this.sessionId
                        );
                    } else {
                        this.sessionId = '';
                        this.messages = [];
                        const storageKey = this.getSessionStorageKey();
                        if (storageKey) {
                            window.localStorage.removeItem(storageKey);
                        }
                    }
                }
            } catch (error) {
                if (!append) {
                    this.conversationItems = [];
                    this.resetConversationPaging();
                }
                // 保护：错误时也不要清空活跃会话
                if (!(this.sessionId && this.messages.length > 0)) {
                    this.selectedConversationId = null;
                    this.sessionId = '';
                    this.messages = [];
                }
            } finally {
                if (append) {
                    this.loadingMoreConversations = false;
                }
            }
        },
        async loadMoreConversationItems() {
            await this.loadConversationItems({ append: true, reloadMessages: false });
        },
        resetConversationPaging() {
            this.conversationPageNo = 0;
            this.conversationTotal = 0;
            this.hasMoreConversations = false;
            this.loadingMoreConversations = false;
        },
        applyConversationPaging(data = {}, itemCount = 0, fallbackPageNo = 1) {
            const pageNo = Number(data?.pageNo || fallbackPageNo || 1);
            this.conversationPageNo =
                Number.isFinite(pageNo) && pageNo > 0 ? pageNo : fallbackPageNo;
            this.conversationTotal = Number(data?.total || 0);
            if (data?.hasMore !== undefined) {
                this.hasMoreConversations = Boolean(data.hasMore);
                return;
            }
            const pageSize = Number(data?.pageSize || CHAT_SESSION_PAGE_SIZE);
            this.hasMoreConversations = itemCount >= pageSize;
        },
        mergeConversationItems(currentItems = [], nextItems = []) {
            const seen = new Set();
            const merged = [];
            [...currentItems, ...nextItems].forEach(item => {
                const key = String(item?.id || '').trim();
                if (key && seen.has(key)) {
                    return;
                }
                if (key) {
                    seen.add(key);
                }
                merged.push(item);
            });
            return merged;
        },
        async selectConversation(item) {
            const conversationId = item?.id ?? null;
            if (!conversationId || conversationId === this.selectedConversationId) {
                return;
            }
            this.invalidateActiveStreamView();
            this.applyConversationContext(item);
            this.selectedConversationId = conversationId;
            this.sessionId = conversationId;
            const storageKey = this.getSessionStorageKey();
            if (storageKey) {
                window.localStorage.setItem(storageKey, this.sessionId);
            }
            this.conversationItems = this.conversationItems.map(current =>
                ChatConversationBean.from({
                    ...current,
                    active: current.id === conversationId,
                })
            );
            // 开始 loading，清空旧消息
            this.loadingConversation = true;
            this.loadingConversationId = conversationId;
            this.messages = [];
            try {
                await this.loadConversationMessages(conversationId);
            } finally {
                // 确保 loading 状态被清除
                this.loadingConversation = false;
                this.loadingConversationId = null;
                this.requestScrollToLatest();
            }
        },
        async deleteConversation(item) {
            const conversationId = item?.id;
            if (!conversationId || this.deletingConversationIds.includes(conversationId)) {
                return;
            }
            const conversationTitle = String(item?.title || item?.name || '').trim();
            const confirmed = await confirm({
                title: '确认删除最近对话？',
                message: conversationTitle
                    ? `“${conversationTitle}” 会话记录删除后将无法恢复，请谨慎操作。`
                    : '会话记录删除后将无法恢复，请谨慎操作。',
                confirmText: '删除',
                cancelText: '取消',
                destructive: true,
            });
            if (!confirmed) {
                return;
            }
            this.deletingConversationIds.push(conversationId);
            try {
                const adapter = this.effectiveHistoryAdapter;
                if (adapter && typeof adapter.deleteConversation === 'function') {
                    await adapter.deleteConversation({
                        conversationId,
                        selectedKnowledge: this.resolvedConversationScopeId,
                        sessionType: item?.sessionType,
                        scopeId: item?.scopeId,
                        onUnauthorized: () => this.$emit('unauthorized'),
                    });
                }
                this.removeConversationById(conversationId);
            } catch (error) {
                this.chatError = error?.message || '删除会话失败';
            } finally {
                this.deletingConversationIds = this.deletingConversationIds.filter(
                    id => id !== conversationId
                );
            }
        },
        async renameConversation(payload) {
            const item = payload?.item || payload;
            const requestedName = payload?.item ? payload?.name : null;
            const conversationId = item?.id;
            if (!conversationId || this.renamingConversationIds.includes(conversationId)) {
                return;
            }

            const initialValue = String(item?.name || item?.title || '').trim();
            let normalizedName = requestedName == null ? '' : String(requestedName).trim();
            if (requestedName == null) {
                const nextName = await prompt({
                    title: '重命名会话',
                    placeholder: '输入会话名称',
                    confirmText: '保存',
                    initialValue,
                });
                if (nextName === false) {
                    return;
                }
                normalizedName = String(nextName || '').trim();
            }
            if (!normalizedName) {
                await alert({
                    title: '会话名称不能为空',
                    message: '请输入新的会话名称后再保存。',
                    confirmText: '知道了',
                });
                return;
            }
            if (normalizedName === initialValue) {
                return;
            }

            this.renamingConversationIds.push(conversationId);
            try {
                const adapter = this.effectiveHistoryAdapter;
                if (adapter && typeof adapter.renameConversation === 'function') {
                    await adapter.renameConversation({
                        conversationId,
                        selectedKnowledge: this.resolvedConversationScopeId,
                        sessionType: item?.sessionType,
                        scopeId: item?.scopeId,
                        name: normalizedName,
                        onUnauthorized: () => this.$emit('unauthorized'),
                    });
                }
                await this.loadConversationItems({ reloadMessages: false });
            } catch (error) {
                this.chatError = error?.message || '重命名会话失败';
            } finally {
                this.renamingConversationIds = this.renamingConversationIds.filter(
                    id => id !== conversationId
                );
            }
        },
        removeConversationById(conversationId) {
            const removedItem = this.conversationItems.find(item => item.id === conversationId);
            const nextRawList = this.conversationItems.filter(item => item.id !== conversationId);

            if (removedItem && removedItem.active) {
                this.selectedConversationId = nextRawList[0]?.id ?? null;
            }
            this.conversationItems = nextRawList.map(item =>
                ChatConversationBean.from({
                    ...item,
                    active:
                        this.selectedConversationId !== null &&
                        item.id === this.selectedConversationId,
                })
            );
            if (!this.selectedConversationId) {
                this.sessionId = '';
                this.messages = [];
                const storageKey = this.getSessionStorageKey();
                if (storageKey) {
                    window.localStorage.removeItem(storageKey);
                }
                this.$emit('reset-chat-context');
            } else {
                this.sessionId = this.selectedConversationId;
                const storageKey = this.getSessionStorageKey();
                if (storageKey) {
                    window.localStorage.setItem(storageKey, this.sessionId);
                }
                const nextItem =
                    nextRawList.find(item => item.id === this.selectedConversationId) || null;
                this.applyConversationContext(nextItem);
                this.loadConversationMessages(this.selectedConversationId);
            }
        },
        async sendMessage() {
            if (this.selectedConversationInputDisabled) {
                this.chatError = '当前会话只读，请新建对话后继续输入。';
                return;
            }
            const normalizedDraft = this.draft.trim();
            const pendingFilesSnapshot = this.pendingFiles.filter(
                file => file && file.status !== 'uploading' && file.status !== 'error' && file.id
            );
            const pendingFileIds = pendingFilesSnapshot.map(file => file.id);
            const mentionedSkillContext = this.resolveEffectiveSkillContext();
            const mentionedSkillId = mentionedSkillContext?.id || null;
            if (!this.canContinueSelectedConversation()) {
                this.chatError = '当前会话来自其他入口，请新建对话后再继续发送。';
                return;
            }
            const hasAttachments = pendingFilesSnapshot.length > 0;
            const hasSubstantiveMessage = hasSubstantiveSkillMessage(
                this.draft,
                this.skillMentionLookup
            );
            const hasUploadingAttachments = this.pendingFiles.some(
                file => file && file.status === 'uploading'
            );
            if (hasUploadingAttachments) {
                this.chatError = '附件仍在上传中，请等待上传完成后再发送';
                return;
            }
            if (this.sending || (!hasSubstantiveMessage && !hasAttachments)) {
                return;
            }
            this.activeAssistantIndex = null;
            const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
            const requestStartedAt = new Date();
            const displayText =
                normalizedDraft || this.buildAttachmentOnlyMessage(pendingFilesSnapshot);
            const userMessage = this.buildMessage(
                '你',
                displayText,
                ChatMessageRender.plain,
                ChatMessageKind.user,
                {
                    attachments: pendingFilesSnapshot,
                    rawText: normalizedDraft,
                    time: this.formatDisplayTime(requestStartedAt),
                }
            );
            this.messages.push(userMessage);
            this.requestScrollToLatest();
            this.chatError = '';
            this.clearComposerDraft();
            this.pendingFiles = [];
            this.sending = true;
            const assistantMessage = this.getOrCreateAssistantMessage(requestId);
            assistantMessage.message.pending = true;
            this.requestScrollToLatest();

            let requestFinishedInCurrentView = false;
            try {
                requestFinishedInCurrentView = await this.performStreamRequest({
                    requestId,
                    assistantMessage: assistantMessage?.message,
                    sendPayload: {
                        message: normalizedDraft,
                        fileIds: pendingFileIds,
                        sessionId: this.sessionId,
                        selectedKnowledge: this.resolvedConversationScopeId,
                        mentionedSkillId,
                        options: this.buildRequestOptions(),
                        onUnauthorized: () => this.$emit('unauthorized'),
                    },
                    afterSuccess: async ({ isCurrentView, sessionId }) => {
                        if (!isCurrentView) {
                            return;
                        }
                        this.touchMessages();
                        // 有错误时跳过会话列表刷新，避免清空错误消息
                        if (this.chatError) {
                            return;
                        }
                        await this.loadConversationItems({ reloadMessages: false });
                        if (sessionId && sessionId === String(this.sessionId || '').trim()) {
                            await this.loadConversationMessages(sessionId);
                        }
                    },
                    errorMessage: '请求失败',
                });
            } finally {
                await this.refreshCurrentUserTokenQuota(
                    requestFinishedInCurrentView && !this.chatError
                );
                const requestContext = this.streamRequestContexts[requestId] || {};
                this.$emit('request-finished', {
                    requestId,
                    sessionId: requestContext.sessionId || this.sessionId,
                    success: requestFinishedInCurrentView && !this.chatError,
                });
            }
        },
        canContinueSelectedConversation() {
            if (!this.selectedConversationId) {
                return true;
            }
            const allowedTypes = Array.isArray(this.adapter?.continuationSessionTypes)
                ? this.adapter.continuationSessionTypes
                      .map(type =>
                          String(type || '')
                              .trim()
                              .toUpperCase()
                      )
                      .filter(Boolean)
                : [];
            if (!allowedTypes.length) {
                return true;
            }
            const currentConversation =
                this.conversationItems.find(item => item.id === this.selectedConversationId) ||
                null;
            const currentType = String(currentConversation?.sessionType || '')
                .trim()
                .toUpperCase();
            return !currentType || allowedTypes.includes(currentType);
        },
        async performStreamRequest({
            requestId,
            assistantMessage = null,
            sendPayload,
            afterSuccess = null,
            errorMessage = '请求失败',
        } = {}) {
            const streamContext = this.createStreamRequestContext(requestId, sendPayload);
            let finishedInCurrentView = false;
            try {
                this.activeRequestId = streamContext.requestId;
                const response = await this.adapter.sendStream(sendPayload || {});
                const ok = response.status >= 200 && response.status < 300;
                if (!ok) {
                    throw new Error(this.parseStreamErrorResponse(response));
                }
                await this.consumeSseStream(response, requestId);
                finishedInCurrentView = this.isStreamRequestCurrent(requestId);
                this.finalizeAssistantRequest(requestId, assistantMessage);
                if (typeof afterSuccess === 'function') {
                    const latestContext = this.streamRequestContexts[requestId] || streamContext;
                    await afterSuccess({
                        requestId: streamContext.requestId,
                        sessionId: latestContext.sessionId || streamContext.sessionId,
                        isCurrentView: finishedInCurrentView,
                    });
                }
            } catch (error) {
                if (this.isStreamRequestCurrent(requestId)) {
                    finishedInCurrentView = true;
                    this.chatError = error?.message || errorMessage;
                }
            } finally {
                this.finalizeAssistantRequest(requestId, assistantMessage);
            }
            return finishedInCurrentView;
        },
        createStreamRequestContext(requestId, sendPayload = {}) {
            const normalizedRequestId = String(requestId || '').trim();
            const sessionId = String(sendPayload?.sessionId || this.sessionId || '').trim();
            const context = {
                requestId: normalizedRequestId,
                sessionId,
                selectedConversationId: String(this.selectedConversationId || sessionId).trim(),
                viewVersion: this.conversationViewVersion,
                active: true,
            };
            if (normalizedRequestId) {
                this.streamRequestContexts = {
                    ...this.streamRequestContexts,
                    [normalizedRequestId]: context,
                };
            }
            return context;
        },
        invalidateActiveStreamView() {
            this.conversationViewVersion += 1;
            this.activeRequestId = '';
            this.sending = false;
            this.activeAssistantIndex = null;
            this.activeAssistantByRequest = {};
        },
        isStreamRequestCurrent(requestId) {
            const normalizedRequestId = String(requestId || '').trim();
            if (!normalizedRequestId) {
                return false;
            }
            const context = this.streamRequestContexts[normalizedRequestId];
            if (!context?.active) {
                return false;
            }
            if (context.viewVersion !== this.conversationViewVersion) {
                return false;
            }
            if (String(this.activeRequestId || '').trim() !== normalizedRequestId) {
                return false;
            }
            const currentSessionId = String(this.sessionId || '').trim();
            const currentConversationId = String(this.selectedConversationId || '').trim();
            return (
                (!context.sessionId || context.sessionId === currentSessionId) &&
                (!context.selectedConversationId ||
                    context.selectedConversationId === currentConversationId)
            );
        },
        triggerFilePicker() {
            if (this.selectedConversationInputDisabled || !this.enableAttachments) {
                return;
            }
            const input = this.$refs.fileInput;
            if (input) {
                input.click();
            }
        },
        async handleFileChange(event) {
            if (this.selectedConversationInputDisabled || !this.enableAttachments) {
                event.target.value = '';
                return;
            }
            const files = Array.from(event.target.files || []);
            if (!files.length) {
                return;
            }
            await this.handleSelectedFiles(files);
            event.target.value = '';
        },
        async uploadFile(file) {
            const localFileId = `uploading:${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
            const localPendingFile = {
                id: localFileId,
                name: file?.name || '未命名文件',
                size: Number(file?.size || 0),
                status: 'uploading',
                progress: 0,
            };
            this.pendingFiles.push(localPendingFile);
            try {
                const uploadedFile = await this.adapter.uploadFile({
                    file,
                    sessionId: this.sessionId,
                    onProgress: progress => {
                        const nextProgress = Number(progress || 0);
                        this.pendingFiles = this.pendingFiles.map(item =>
                            item.id === localFileId
                                ? {
                                      ...item,
                                      status: 'uploading',
                                      progress: Math.max(
                                          Number(item.progress || 0),
                                          Math.min(100, Math.round(nextProgress))
                                      ),
                                  }
                                : item
                        );
                    },
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                this.pendingFiles = this.pendingFiles.map(item =>
                    item.id === localFileId
                        ? {
                              ...uploadedFile,
                              status: 'ready',
                              progress: 100,
                          }
                        : item
                );
            } catch (error) {
                this.pendingFiles = this.pendingFiles.map(item =>
                    item.id === localFileId
                        ? {
                              ...item,
                              status: 'error',
                              progress: 0,
                              errorMessage: error?.message || '上传失败',
                          }
                        : item
                );
                this.chatError = error.message || '上传失败';
            }
        },
        removePendingFile(id) {
            this.pendingFiles = this.pendingFiles.filter(file => file.id !== id);
        },
        previewPendingFile(file) {
            if (!file || file.status !== 'ready') {
                return;
            }
            this.previewAttachmentFile(file);
        },
        previewAttachmentFile(file) {
            if (!file) return;
            const fileName = String(file.name || file.fileName || '预览').trim();
            const previewUrl = this.resolveAttachmentPreviewUrl(file);
            if (!previewUrl) {
                this.chatError = '该文件暂不支持预览';
                return;
            }
            this.activeCitation = null;
            this.filePanelOpen = true;
            this.agentSettingsPanelOpen = false;
            this.activeHtml = {
                title: fileName,
                fileName,
                size: file.size || '',
                src: previewUrl,
                html: '',
                downloadUrl: file.downloadUrl || '',
                contentType: file.contentType || '',
            };
        },
        toggleSegment(segment) {
            segment.open = !segment.open;
            this.shouldAutoScroll = false;
            this.touchMessages();
        },
        async handleFrontendRenderAction(action) {
            const renderId = String(action?.renderId || '').trim();
            const eventMessage = this.buildFrontendActionMessage(action);
            const normalizedMessageType =
                String(action?.messageType || 'event')
                    .trim()
                    .toLowerCase() || 'event';
            if (!renderId && !eventMessage) {
                return;
            }
            if (renderId) {
                this.updateRenderPayloadState(renderId, action?.state || {});
                this.touchMessages();
            }
            if (!this.adapter || typeof this.adapter.sendStream !== 'function') {
                return;
            }
            if (this.sending) {
                return;
            }
            const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
            const userMessage = this.buildMessage(
                '你',
                eventMessage,
                ChatMessageRender.plain,
                ChatMessageKind.user,
                {
                    rawText: eventMessage,
                    messageType: normalizedMessageType,
                    eventPayload: action,
                }
            );
            this.messages.push(userMessage);
            this.requestScrollToLatest();
            this.chatError = '';
            this.sending = true;
            const mentionedSkillId = this.resolveEffectiveSkillContext()?.id || null;
            const assistantMessage = this.getOrCreateAssistantMessage(requestId);
            assistantMessage.message.pending = true;
            this.requestScrollToLatest();
            let requestFinishedInCurrentView = false;
            try {
                requestFinishedInCurrentView = await this.performStreamRequest({
                    requestId,
                    assistantMessage: assistantMessage?.message,
                    sendPayload: {
                        message: String(action?.message || eventMessage),
                        fileIds: [],
                        sessionId: this.sessionId,
                        selectedKnowledge: this.selectedKnowledge,
                        messageType: normalizedMessageType,
                        eventPayload: action,
                        mentionedSkillId,
                        options: this.buildRequestOptions(),
                        onUnauthorized: () => this.$emit('unauthorized'),
                    },
                    afterSuccess: async ({ isCurrentView, sessionId }) => {
                        if (!isCurrentView) {
                            return;
                        }
                        this.touchMessages();
                        // 有错误时跳过会话列表刷新，避免清空错误消息
                        if (this.chatError) {
                            return;
                        }
                        await this.loadConversationItems({ reloadMessages: false });
                        if (sessionId && sessionId === String(this.sessionId || '').trim()) {
                            await this.loadConversationMessages(sessionId);
                        }
                    },
                    errorMessage: '同步卡片状态失败',
                });
            } finally {
                await this.refreshCurrentUserTokenQuota(
                    requestFinishedInCurrentView && !this.chatError
                );
                const requestContext = this.streamRequestContexts[requestId] || {};
                this.$emit('request-finished', {
                    requestId,
                    sessionId: requestContext.sessionId || this.sessionId,
                    success: requestFinishedInCurrentView && !this.chatError,
                });
            }
        },
        async refreshCurrentUserTokenQuota(success) {
            if (!success) {
                return;
            }
            try {
                await ensureCurrentUserLoaded({
                    force: true,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
            } catch (error) {
                // Keep chat flow stable if profile refresh fails.
            }
        },
        finalizeAssistantRequest(requestId, assistantMessage = null) {
            const streamVisible = this.isStreamRequestCurrent(requestId);
            if (streamVisible) {
                this.sending = false;
            }
            if (streamVisible && assistantMessage && typeof assistantMessage === 'object') {
                assistantMessage.pending = false;
            }
            if (streamVisible) {
                const active = this.getActiveAssistantMessage(requestId);
                if (active?.message && typeof active.message === 'object') {
                    active.message.pending = false;
                }
            }
            if (
                streamVisible &&
                this.runtimePhasePayload &&
                String(this.runtimePhasePayload.requestId || '').trim() ===
                    String(requestId || '').trim()
            ) {
                this.runtimePhasePayload = null;
                this.$emit('runtime-phase-change', null);
            }
            if (String(requestId || '').trim() === this.activeRequestId) {
                this.activeRequestId = '';
                this.activeRunCode = '';
                this.activeStreamController = null;
            }
            if (this.streamRequestContexts[requestId]) {
                this.streamRequestContexts = {
                    ...this.streamRequestContexts,
                    [requestId]: {
                        ...this.streamRequestContexts[requestId],
                        active: false,
                    },
                };
            }
            delete this.activeAssistantByRequest[requestId];
        },
        buildFrontendActionMessage(action) {
            return String(action?.message || '').trim();
        },
        buildRequestOptions() {
            if (!this.requestOptions || typeof this.requestOptions !== 'object') {
                return {};
            }
            return { ...this.requestOptions };
        },
        updateRenderPayloadState(renderId, nextState) {
            if (!renderId) {
                return false;
            }
            let updated = false;
            this.messages.forEach(message => {
                if (!Array.isArray(message?.segments)) {
                    return;
                }
                message.segments.forEach(segment => {
                    if (segment?.type !== ChatSegmentType.tool || !segment?.renderPayload) {
                        return;
                    }
                    if (String(segment.renderPayload?.renderId || '').trim() !== renderId) {
                        return;
                    }
                    segment.renderPayload = {
                        ...segment.renderPayload,
                        state: this.mergeRenderState(segment.renderPayload?.state, nextState),
                    };
                    updated = true;
                });
            });
            return updated;
        },
        mergeRenderState(base, override) {
            const baseValue = base && typeof base === 'object' && !Array.isArray(base) ? base : {};
            const overrideValue =
                override && typeof override === 'object' && !Array.isArray(override)
                    ? override
                    : {};
            const merged = {
                ...baseValue,
                ...overrideValue,
            };
            const baseActionStates =
                baseValue.actionStates && typeof baseValue.actionStates === 'object'
                    ? baseValue.actionStates
                    : {};
            const overrideActionStates =
                overrideValue.actionStates && typeof overrideValue.actionStates === 'object'
                    ? overrideValue.actionStates
                    : {};
            merged.actionStates = {
                ...baseActionStates,
                ...overrideActionStates,
            };
            return merged;
        },
        async consumeSseStream(response, requestId) {
            const stream = response && response.data ? response.data : response.body;
            if (!stream || typeof stream.getReader !== 'function') {
                throw new Error('服务响应不支持流式读取');
            }
            const reader = stream.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';
            let assistantMessage = null;
            let htmlBuffer = '';
            let pendingEventName = '';

            while (true) {
                const { value, done } = await reader.read();
                if (value) {
                    buffer += decoder.decode(value, { stream: true });
                }
                const lines = buffer.split(/\r?\n/);
                const tail = lines.pop() || '';
                buffer = done ? '' : tail;
                const chunkResult = this.consumeSseLines(
                    lines,
                    requestId,
                    assistantMessage,
                    htmlBuffer,
                    pendingEventName
                );
                assistantMessage = chunkResult.assistantMessage;
                htmlBuffer = chunkResult.htmlBuffer;
                pendingEventName = chunkResult.pendingEventName;
                if (chunkResult.finished) {
                    return;
                }
                if (done) {
                    if (tail.trim()) {
                        const tailResult = this.consumeSseLines(
                            [tail],
                            requestId,
                            assistantMessage,
                            htmlBuffer,
                            pendingEventName
                        );
                        if (tailResult.finished) {
                            return;
                        }
                    }
                    break;
                }
            }
        },
        consumeSseLines(lines, requestId, assistantMessage, htmlBuffer, pendingEventName = '') {
            let nextAssistantMessage = assistantMessage;
            let nextHtmlBuffer = htmlBuffer;
            let nextPendingEventName = pendingEventName;
            for (const line of lines) {
                const streamVisible = this.isStreamRequestCurrent(requestId);
                const trimmed = String(line || '').trim();
                if (!trimmed) {
                    continue;
                }
                if (trimmed.startsWith('event:')) {
                    nextPendingEventName = trimmed.slice(6).trim();
                    continue;
                }
                if (!trimmed.startsWith('data:')) {
                    continue;
                }
                const data = trimmed.slice(5).trim();
                const parsed = this.adapter.parseEventPayload(data);
                const eventName = String(nextPendingEventName || parsed.eventName || '').trim();
                nextPendingEventName = '';
                if (parsed.type === ChatStreamEventType.done) {
                    return {
                        assistantMessage: nextAssistantMessage,
                        htmlBuffer: nextHtmlBuffer,
                        pendingEventName: '',
                        finished: true,
                    };
                }
                if (parsed.type === ChatStreamEventType.meta) {
                    if (streamVisible) {
                        this.handleMetaEvent(parsed.content, requestId);
                    }
                    continue;
                }
                if (
                    parsed.type === ChatStreamEventType.tool ||
                    parsed.type === ChatStreamEventType.skill ||
                    parsed.type === ChatStreamEventType.result
                ) {
                    if (!streamVisible) {
                        continue;
                    }
                    this.handleToolEvent(parsed.type, parsed.content, requestId);
                    const active = this.getActiveAssistantMessage(requestId);
                    nextAssistantMessage = active ? active.message : null;
                    nextHtmlBuffer = '';
                    this.touchMessages();
                    continue;
                }
                if (parsed.type === ChatStreamEventType.citation) {
                    if (!streamVisible) {
                        continue;
                    }
                    this.handleCitationEvent(parsed.content, requestId);
                    const active = this.getActiveAssistantMessage(requestId);
                    nextAssistantMessage = active ? active.message : null;
                    this.touchMessages();
                    continue;
                }
                if (parsed.type === ChatStreamEventType.fallbackNotice) {
                    if (!streamVisible) {
                        continue;
                    }
                    this.handleFallbackNoticeEvent(parsed.content, requestId);
                    const active = this.getActiveAssistantMessage(requestId);
                    nextAssistantMessage = active ? active.message : null;
                    this.touchMessages();
                    continue;
                }
                if (parsed.type === ChatStreamEventType.error) {
                    if (streamVisible) {
                        this.chatError = parsed.content || '';
                    }
                    continue;
                }
                if (parsed.type === ChatStreamEventType.message && eventName === 'phase') {
                    if (streamVisible) {
                        this.handlePhaseProgressEvent(parsed.content, requestId);
                    }
                    continue;
                }
                if (!streamVisible) {
                    continue;
                }
                // 检测 message 类型中的错误消息（如 token 额度耗尽）
                if (
                    parsed.type === ChatStreamEventType.message ||
                    parsed.type === ChatStreamEventType.answer
                ) {
                    const content = typeof parsed.content === 'string' ? parsed.content : '';
                    if (content.includes('token 额度已用完') || content.includes('额度已用尽')) {
                        this.chatError = content;
                    }
                }
                const messageChunk =
                    parsed.type === ChatStreamEventType.message ||
                    parsed.type === ChatStreamEventType.answer
                        ? this.resolveMessageChunk(parsed.content, data)
                        : data;
                const chunkResult = this.applyAssistantChunk(
                    messageChunk,
                    nextAssistantMessage,
                    nextHtmlBuffer,
                    requestId
                );
                nextAssistantMessage = chunkResult.assistantMessage;
                nextHtmlBuffer = chunkResult.htmlBuffer;
                this.touchMessages();
            }
            return {
                assistantMessage: nextAssistantMessage,
                htmlBuffer: nextHtmlBuffer,
                pendingEventName: nextPendingEventName,
                finished: false,
            };
        },
        handleMetaEvent(content, requestId) {
            let payload = content;
            if (typeof payload === 'string') {
                try {
                    payload = JSON.parse(payload);
                } catch (error) {
                    payload = null;
                }
            }
            if (!payload || typeof payload !== 'object') {
                return;
            }
            const nextSessionId = String(payload.sessionId || '').trim();
            if (!nextSessionId) {
                return;
            }
            this.bindStreamRequestSession(requestId, nextSessionId);
            this.sessionId = nextSessionId;
            this.selectedConversationId = nextSessionId;
            const storageKey = this.getSessionStorageKey();
            if (storageKey) {
                window.localStorage.setItem(storageKey, this.sessionId);
            }
            this.applyConversationContext({
                sessionType: payload.sessionType || '',
                scopeId: payload.scopeId || null,
                scopeDisplayName: payload.scopeDisplayName || '',
            });

            const active = this.getActiveAssistantMessage(requestId);
            if (!active) {
                return;
            }
            active.message.answerMode = String(payload.answerMode || '').trim();
            active.message.routeReason = String(payload.routeReason || '').trim();
            active.message.fallbackReason = String(payload.fallbackReason || '').trim();
        },
        bindStreamRequestSession(requestId, sessionId) {
            const normalizedRequestId = String(requestId || '').trim();
            const normalizedSessionId = String(sessionId || '').trim();
            if (!normalizedRequestId || !normalizedSessionId) {
                return;
            }
            const context = this.streamRequestContexts[normalizedRequestId];
            if (!context) {
                return;
            }
            this.streamRequestContexts = {
                ...this.streamRequestContexts,
                [normalizedRequestId]: {
                    ...context,
                    sessionId: context.sessionId || normalizedSessionId,
                    selectedConversationId: context.selectedConversationId || normalizedSessionId,
                },
            };
        },
        handlePhaseProgressEvent(content, requestId) {
            let payload = content;
            if (typeof payload === 'string') {
                try {
                    payload = JSON.parse(payload);
                } catch (error) {
                    payload = null;
                }
            }
            if (!payload || typeof payload !== 'object') {
                return;
            }
            const nextPayload = {
                sessionId: String(payload.sessionId || this.sessionId || '').trim(),
                runCode: String(payload.runCode || '').trim(),
                phase: String(payload.phase || '')
                    .trim()
                    .toUpperCase(),
                mode: String(payload.mode || '')
                    .trim()
                    .toUpperCase(),
                iterationCount: Number(payload.iterationCount || 0),
                llmCallCount: Number(payload.llmCallCount || 0),
                toolCallCount: Number(payload.toolCallCount || 0),
                decisionRepairCount: Number(payload.decisionRepairCount || 0),
                finishReason: String(payload.finishReason || '')
                    .trim()
                    .toUpperCase(),
                subStage: String(payload.subStage || '')
                    .trim()
                    .toUpperCase(),
                subStageLabel: String(payload.subStageLabel || '').trim(),
                progressMessage: String(payload.progressMessage || '').trim(),
                at: String(payload.at || '').trim(),
                requestId: String(requestId || '').trim(),
            };
            if (!nextPayload.phase) {
                return;
            }
            this.runtimePhasePayload = nextPayload;
            this.upsertRuntimeEngineSegmentFromPhase(nextPayload, requestId);
            this.applyActiveRunPayload(nextPayload, requestId);
            this.$emit('runtime-phase-change', nextPayload);
        },
        applyActiveRunPayload(payload, requestId) {
            if (!payload || typeof payload !== 'object') {
                return;
            }
            if (String(requestId || '').trim() !== String(this.activeRequestId || '').trim()) {
                return;
            }
            const runCode = String(payload.runCode || '').trim();
            if (runCode) {
                this.activeRunCode = runCode;
            }
        },
        handleRuntimeEngineEvent(content, requestId) {
            let payload = content;
            if (typeof payload === 'string') {
                try {
                    payload = JSON.parse(payload);
                } catch (error) {
                    payload = null;
                }
            }
            if (!payload || typeof payload !== 'object') {
                return;
            }
            const key = `runtime-engine:${requestId || 'global'}`;
            const state =
                payload.state && typeof payload.state === 'object' && !Array.isArray(payload.state)
                    ? payload.state
                    : {};
            const historyEntry = {
                sequence: Number(payload.sequence || 0),
                node: String(payload.node || '').trim(),
                kind: String(payload.kind || '').trim(),
                status: String(payload.status || '')
                    .trim()
                    .toLowerCase(),
                phase: String(state.phase || '')
                    .trim()
                    .toUpperCase(),
                at: String(payload.at || '').trim(),
            };
            const previous =
                this.runtimeEngineStateMap[key] &&
                typeof this.runtimeEngineStateMap[key] === 'object'
                    ? this.runtimeEngineStateMap[key]
                    : null;
            const history = Array.isArray(previous?.history) ? previous.history.slice() : [];
            const lastHistory = history.length ? history[history.length - 1] : null;
            if (
                historyEntry.node ||
                historyEntry.kind === 'completed' ||
                historyEntry.sequence > 0
            ) {
                if (
                    !lastHistory ||
                    lastHistory.sequence !== historyEntry.sequence ||
                    lastHistory.node !== historyEntry.node ||
                    lastHistory.status !== historyEntry.status
                ) {
                    history.push(historyEntry);
                }
            }
            const runtimeEnginePayload = {
                type: ChatSegmentType.runtimeEngine,
                key,
                requestId: String(requestId || '').trim(),
                engine: String(payload.engine || previous?.engine || '').trim(),
                preview: payload.preview !== false,
                stream: payload.stream !== false,
                status: historyEntry.status || previous?.status || 'running',
                currentNode: historyEntry.node || previous?.currentNode || '',
                currentPhase: historyEntry.phase || previous?.currentPhase || '',
                state,
                history,
                nodes: Array.isArray(payload.nodes) ? payload.nodes : previous?.nodes || [],
                updatedAt: historyEntry.at || previous?.updatedAt || '',
                open: false,
            };
            this.runtimeEngineStateMap[key] = runtimeEnginePayload;
            const target = this.getOrCreateAssistantMessage(requestId);
            target.message.segments = target.message.segments || [];
            this.upsertRuntimeEngineSegment(target.message, runtimeEnginePayload);
            this.activeAssistantIndex = target.index;
            if (requestId) {
                this.activeAssistantByRequest[requestId] = target.index;
            }
            this.$emit('runtime-engine-change', runtimeEnginePayload);
            this.handlePhaseProgressEvent(
                {
                    sessionId: this.sessionId,
                    phase: state.phase || '',
                    mode: state.mode || '',
                    iterationCount: state.iterationCount || 0,
                    llmCallCount: state.llmCallCount || 0,
                    toolCallCount: state.toolCallCount || 0,
                    finishReason: state.finishReason || '',
                    at: payload.at || '',
                },
                requestId
            );
            this.shouldAutoScroll = true;
        },
        handleApprovalEvent(content, requestId) {
            let payload = content;
            if (typeof payload === 'string') {
                try {
                    payload = JSON.parse(payload);
                } catch (error) {
                    payload = null;
                }
            }
            if (!payload || typeof payload !== 'object') {
                return;
            }
            const approvalPayload = {
                approvalCode: String(payload.approvalCode || '').trim(),
                runCode: String(payload.runCode || '').trim(),
                runId: payload.runId ?? null,
                sessionId: payload.sessionId ?? null,
                assistantMessageId: payload.assistantMessageId ?? null,
                toolCallId: String(payload.toolCallId || '').trim(),
                toolName: String(payload.toolName || '').trim(),
                toolDisplayName: String(payload.toolDisplayName || '').trim(),
                toolArguments:
                    payload.toolArguments && typeof payload.toolArguments === 'object'
                        ? payload.toolArguments
                        : {},
                approvalStatus: String(payload.approvalStatus || 'PENDING')
                    .trim()
                    .toUpperCase(),
                executionStatus: String(payload.executionStatus || 'NOT_STARTED')
                    .trim()
                    .toUpperCase(),
                riskLevel: String(payload.riskLevel || '')
                    .trim()
                    .toUpperCase(),
                triggerReason: String(payload.triggerReason || '').trim(),
                analysis:
                    payload.analysis && typeof payload.analysis === 'object'
                        ? payload.analysis
                        : {},
                requestedBy: payload.requestedBy ?? null,
                decidedBy: payload.decidedBy ?? null,
                decisionComment: String(payload.decisionComment || '').trim(),
                toolResult: payload.toolResult ?? '',
                requestId: String(requestId || '').trim(),
            };
            if (!approvalPayload.approvalCode) {
                return;
            }
            this.runtimeApprovalPayload = approvalPayload;
            this.applyActiveRunPayload(approvalPayload, requestId);
            const target = this.getOrCreateAssistantMessage(requestId);
            target.message.segments = target.message.segments || [];
            const nextSegment = {
                type: ChatSegmentType.approval,
                key: `approval:${approvalPayload.approvalCode}`,
                ...approvalPayload,
                status: this.normalizeApprovalSegmentStatus(approvalPayload.approvalStatus),
            };
            const existingIndex = target.message.segments.findIndex(
                segment =>
                    segment?.type === ChatSegmentType.approval &&
                    String(segment?.approvalCode || '').trim() === approvalPayload.approvalCode
            );
            if (existingIndex >= 0) {
                target.message.segments.splice(existingIndex, 1, {
                    ...target.message.segments[existingIndex],
                    ...nextSegment,
                });
            } else {
                target.message.segments.push(nextSegment);
            }
            this.activeAssistantIndex = target.index;
            if (requestId) {
                this.activeAssistantByRequest[requestId] = target.index;
            }
            this.shouldAutoScroll = true;
        },
        async loadConversationMessages(conversationId) {
            if (
                !conversationId ||
                !this.effectiveHistoryAdapter ||
                typeof this.effectiveHistoryAdapter.fetchMessages !== 'function'
            ) {
                this.messages = [];
                this.resetHistoryPaging();
                return;
            }
            try {
                const adapter = this.effectiveHistoryAdapter;
                const conversation =
                    this.conversationItems.find(item => item.id === conversationId) || null;
                const { data } = await adapter.fetchMessages({
                    conversationId,
                    selectedKnowledge: this.resolvedConversationScopeId,
                    sessionType: conversation?.sessionType,
                    scopeId: conversation?.scopeId,
                    pageNo: 1,
                    pageSize: CHAT_HISTORY_PAGE_SIZE,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const rows = Array.isArray(data?.items) ? data.items : [];
                this.applyHistoryPaging(data, rows.length, 1);
                // 保存当前的runtimeEngine segments，避免被历史消息覆盖
                const runtimeEngineSegments = this.extractRuntimeEngineSegments(this.messages);
                const historicalMessages = this.hydrateMessagesFromHistory(rows);
                // 合并runtimeEngine segments到历史消息
                this.messages = this.mergeRuntimeEngineSegments(
                    historicalMessages,
                    runtimeEngineSegments
                );
                this.emitLatestRenderPayload();
                this.chatError = '';
                this.pendingFiles = [];
                this.activeCitation = null;
                this.toolEventIndex = {};
                this.seenToolEvents = {};
                this.seenCitationEvents = {};
                this.activeAssistantIndex = null;
                this.activeAssistantByRequest = {};
                this.shouldAutoScroll = true;
                this.requestScrollToLatest();
            } catch (error) {
                this.chatError = error?.message || '加载历史消息失败';
            }
        },
        async loadOlderConversationMessages() {
            if (
                !this.sessionId ||
                !this.hasOlderMessages ||
                this.loadingOlderMessages ||
                this.loadingConversation ||
                !this.effectiveHistoryAdapter ||
                typeof this.effectiveHistoryAdapter.fetchMessages !== 'function'
            ) {
                return;
            }
            this.loadingOlderMessages = true;
            this.shouldAutoScroll = false;
            try {
                const adapter = this.effectiveHistoryAdapter;
                const conversation =
                    this.conversationItems.find(item => item.id === this.sessionId) || null;
                const nextPageNo = this.historyPageNo + 1;
                const { data } = await adapter.fetchMessages({
                    conversationId: this.sessionId,
                    selectedKnowledge: this.resolvedConversationScopeId,
                    sessionType: conversation?.sessionType,
                    scopeId: conversation?.scopeId,
                    pageNo: nextPageNo,
                    pageSize: CHAT_HISTORY_PAGE_SIZE,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const rows = Array.isArray(data?.items) ? data.items : [];
                if (rows.length) {
                    const olderMessages = this.hydrateMessagesFromHistory(rows);
                    this.messages = this.mergeHistoryPages(olderMessages, this.messages);
                }
                this.applyHistoryPaging(data, rows.length, nextPageNo);
                this.emitLatestRenderPayload();
            } catch (error) {
                this.chatError = error?.message || '加载更早消息失败';
            } finally {
                this.loadingOlderMessages = false;
            }
        },
        resetHistoryPaging() {
            this.historyPageNo = 0;
            this.hasOlderMessages = false;
            this.loadingOlderMessages = false;
        },
        applyHistoryPaging(data = {}, itemCount = 0, fallbackPageNo = 1) {
            const pageNo = Number(data?.pageNo || fallbackPageNo || 1);
            this.historyPageNo = Number.isFinite(pageNo) && pageNo > 0 ? pageNo : fallbackPageNo;
            if (data?.hasMore !== undefined) {
                this.hasOlderMessages = Boolean(data.hasMore);
                return;
            }
            const pageSize = Number(data?.pageSize || CHAT_HISTORY_PAGE_SIZE);
            this.hasOlderMessages = itemCount >= pageSize;
        },
        mergeHistoryPages(olderMessages = [], currentMessages = []) {
            const seen = new Set();
            const merged = [];
            [...olderMessages, ...currentMessages].forEach(message => {
                const key = this.resolveHistoryMessageKey(message);
                if (key && seen.has(key)) {
                    return;
                }
                if (key) {
                    seen.add(key);
                }
                merged.push(message);
            });
            return merged;
        },
        resolveHistoryMessageKey(message) {
            return String(message?.id || message?.messageCode || '').trim();
        },
        hydrateMessagesFromHistory(rows = []) {
            const output = [];
            rows.forEach(row => {
                const role = String(row?.role || '')
                    .trim()
                    .toUpperCase();
                const content = String(row?.content || '').trim();
                const status = String(row?.status || '')
                    .trim()
                    .toLowerCase();
                const errorMessage = String(row?.errorMessage || '').trim();
                const createdAt = this.normalizeMessageTime(row?.createdAt);
                const updatedAt = this.normalizeMessageTime(
                    row?.completedAt || row?.updatedAt || row?.createdAt
                );
                const params = this.parseJsonObject(row?.paramsJson);
                const attachments = this.parseJsonArray(row?.attachmentsJson).map(file => ({
                    id: file?.id || file?.path || file?.name || `${row?.id || 'file'}`,
                    name: file?.name || file?.fileName || '未命名文件',
                    size: Number(file?.size || 0),
                    path: file?.path || '',
                    objectName: file?.objectName || '',
                    contentType: file?.contentType || '',
                    downloadUrl: file?.downloadUrl || '',
                    previewUrl: file?.previewUrl || '',
                }));
                const messageType =
                    String(params?.messageType || '')
                        .trim()
                        .toLowerCase() || 'normal';

                if (role === 'USER' && content) {
                    output.push(
                        this.buildMessage(
                            '你',
                            content,
                            ChatMessageRender.plain,
                            ChatMessageKind.user,
                            {
                                id: row?.id,
                                messageCode: row?.messageCode || '',
                                time: createdAt,
                                attachments,
                                messageType,
                            }
                        )
                    );
                    return;
                }

                if (role === 'ASSISTANT') {
                    const assistant = this.buildMessage(
                        '助手',
                        '',
                        ChatMessageRender.markdown,
                        ChatMessageKind.assistant,
                        {
                            id: row?.id,
                            messageCode: row?.messageCode || '',
                            segments: [],
                            pending: status === 'pending' || status === 'streaming',
                            time: updatedAt,
                            answerMode: '',
                            routeReason: '',
                            fallbackReason: '',
                        }
                    );

                    // 优先使用 segmentsJson，如果没有则从 toolEvents 重建
                    const historySegments = this.normalizeHistorySegments(row?.segmentsJson);
                    if (historySegments.length) {
                        assistant.segments.push(...historySegments);
                    } else {
                        assistant.segments.push(
                            ...this.buildToolSegmentsFromHistory(params?.toolEvents)
                        );
                    }
                    const historyRuntimeEnginePayload =
                        this.extractHistoryRuntimeEnginePayload(params);
                    if (
                        historyRuntimeEnginePayload &&
                        !this.messageHasSegmentType(assistant, ChatSegmentType.runtimeEngine)
                    ) {
                        assistant.segments.unshift(historyRuntimeEnginePayload);
                    }

                    const parsedAnswer = this.extractFallbackNoticeFromAnswer(content);
                    if (!historySegments.length && parsedAnswer.body) {
                        assistant.segments.push({
                            type: ChatSegmentType.text,
                            text: parsedAnswer.body,
                        });
                    }
                    if (
                        parsedAnswer.notice &&
                        !this.messageHasSegmentType(assistant, ChatSegmentType.fallbackNotice)
                    ) {
                        assistant.segments.push({
                            type: ChatSegmentType.fallbackNotice,
                            text: parsedAnswer.notice,
                        });
                    }

                    const artifactSegment = this.extractHistoryArtifactSegment(
                        row?.artifactSummaryJson,
                        params
                    );
                    if (!this.messageHasSegmentType(assistant, ChatSegmentType.artifact)) {
                        this.appendArtifactSegment(
                            assistant,
                            artifactSegment ||
                                this.extractArtifactSegmentFromHistoryToolEvents(params?.toolEvents)
                        );
                    }

                    const documents = this.extractHistoryDocuments(row?.artifactSummaryJson);
                    if (!this.messageHasSegmentType(assistant, ChatSegmentType.citation)) {
                        documents.forEach((doc, index) => {
                            assistant.segments.push({
                                type: ChatSegmentType.citation,
                                ref: doc?.ref ?? index + 1,
                                fileName: doc?.fileName || 'unknown',
                                indexId: doc?.indexId || '',
                                snippet: doc?.snippet || '',
                                score:
                                    typeof doc?.score === 'number'
                                        ? doc.score.toFixed(4)
                                        : Number(doc?.score || 0).toFixed(4),
                            });
                        });
                    }

                    if (status === ChatStreamEventType.error && errorMessage) {
                        assistant.segments.push({
                            type: ChatSegmentType.text,
                            text: `错误：${errorMessage}`,
                        });
                    } else if (status === ChatToolStatus.interrupted && !content) {
                        assistant.segments.push({
                            type: ChatSegmentType.text,
                            text: '回答已中断。',
                        });
                    }

                    if (parsedAnswer.notice) {
                        assistant.answerMode = 'LLM_FALLBACK';
                    } else if (documents.length > 0) {
                        assistant.answerMode = 'KB_QA';
                    }

                    if (
                        assistant.segments.length > 0 ||
                        status === 'pending' ||
                        status === 'streaming'
                    ) {
                        output.push(assistant);
                    }
                    return;
                }
            });
            return output;
        },
        extractLatestHistoryRuntimeEnginePayload(rows = []) {
            if (!Array.isArray(rows) || rows.length === 0) {
                return null;
            }
            for (let index = rows.length - 1; index >= 0; index -= 1) {
                const row = rows[index];
                const role = String(row?.role || '')
                    .trim()
                    .toUpperCase();
                if (role !== 'ASSISTANT') {
                    continue;
                }
                const payload = this.extractHistoryRuntimeEnginePayload(
                    this.parseJsonObject(row?.paramsJson)
                );
                if (payload) {
                    return payload;
                }
            }
            return null;
        },
        extractRuntimeEngineSegments(messages = []) {
            const segments = [];
            if (!Array.isArray(messages)) {
                return segments;
            }
            messages.forEach(message => {
                if (!message || !Array.isArray(message.segments)) {
                    return;
                }
                message.segments.forEach(segment => {
                    if (segment && segment.type === ChatSegmentType.runtimeEngine) {
                        segments.push({
                            messageId: message.id,
                            segment: { ...segment },
                        });
                    }
                });
            });
            return segments;
        },
        mergeRuntimeEngineSegments(historicalMessages = [], runtimeEngineSegments = []) {
            if (!Array.isArray(historicalMessages)) {
                return [];
            }
            if (!Array.isArray(runtimeEngineSegments) || runtimeEngineSegments.length === 0) {
                return historicalMessages;
            }
            return historicalMessages.map(message => {
                if (!message) {
                    return message;
                }
                const matchingSegments = runtimeEngineSegments.filter(
                    item => item.messageId === message.id
                );
                if (matchingSegments.length === 0) {
                    return message;
                }
                const existingTypes = new Set((message.segments || []).map(s => s?.type));
                const newSegments = matchingSegments
                    .filter(item => !existingTypes.has(item.segment?.type))
                    .map(item => item.segment);
                return {
                    ...message,
                    segments: [...(message.segments || []), ...newSegments],
                };
            });
        },
        extractHistoryRuntimeEnginePayload(params = {}) {
            if (!params || typeof params !== 'object') {
                return null;
            }
            const engine = String(params.runtimeV2Engine || '').trim();
            const graphState =
                params.graphState && typeof params.graphState === 'object'
                    ? params.graphState
                    : null;
            if (!engine || !graphState) {
                return null;
            }
            const phaseTrace = Array.isArray(params.phaseTrace) ? params.phaseTrace : [];
            const history = this.buildRuntimeHistoryFromPhaseTrace(phaseTrace);
            const phase = String(graphState.phase || '')
                .trim()
                .toUpperCase();
            const node = this.resolveRuntimeNodeFromPhase(phase);
            return {
                type: ChatSegmentType.runtimeEngine,
                key: `runtime-engine-history:${engine}:${phase}:${graphState.iterationCount || 0}`,
                requestId: '',
                engine,
                preview: params.graphPreview !== false,
                stream: false,
                status: 'completed',
                currentNode: node,
                currentPhase: phase,
                state: graphState,
                history:
                    history.length > 0
                        ? history
                        : node
                          ? [
                                {
                                    sequence: Number(graphState.iterationCount || 0),
                                    node,
                                    kind: 'completed',
                                    status: 'completed',
                                    phase,
                                    at: '',
                                },
                            ]
                          : [],
                nodes: [],
                updatedAt: '',
                open: false,
            };
        },
        extractHistoryApprovalSegment(params = {}) {
            const approval =
                params?.approval &&
                typeof params.approval === 'object' &&
                !Array.isArray(params.approval)
                    ? params.approval
                    : null;
            if (!approval || !approval.approvalCode) {
                return null;
            }
            const status = this.normalizeApprovalSegmentStatus(approval.approvalStatus);
            return {
                type: ChatSegmentType.approval,
                key: `approval:${approval.approvalCode}`,
                approvalCode: String(approval.approvalCode || '').trim(),
                runCode: String(approval.runCode || params.runCode || '').trim(),
                runId: approval.runId ?? params.runId ?? null,
                sessionId: approval.sessionId ?? null,
                assistantMessageId: approval.assistantMessageId ?? null,
                toolCallId: String(approval.toolCallId || '').trim(),
                toolName: String(approval.toolName || '').trim(),
                toolDisplayName: String(approval.toolDisplayName || '').trim(),
                toolArguments: approval.toolArguments || {},
                approvalStatus: status,
                executionStatus: String(approval.executionStatus || '').trim(),
                riskLevel: String(approval.riskLevel || '').trim(),
                triggerReason: String(approval.triggerReason || '').trim(),
                analysis:
                    approval.analysis && typeof approval.analysis === 'object'
                        ? approval.analysis
                        : {},
                requestedBy: approval.requestedBy ?? null,
                decidedBy: approval.decidedBy ?? null,
                decisionComment: String(approval.decisionComment || '').trim(),
                toolResult: approval.toolResult || '',
                status,
            };
        },
        buildRuntimePhasePayloadFromGraphPayload(payload, requestId = '') {
            if (!payload || typeof payload !== 'object') {
                return null;
            }
            const state =
                payload.state && typeof payload.state === 'object' && !Array.isArray(payload.state)
                    ? payload.state
                    : {};
            return {
                sessionId: String(this.sessionId || '').trim(),
                phase: String(payload.currentPhase || state.phase || '')
                    .trim()
                    .toUpperCase(),
                mode: String(state.mode || '')
                    .trim()
                    .toUpperCase(),
                iterationCount: Number(state.iterationCount || 0),
                llmCallCount: Number(state.llmCallCount || 0),
                toolCallCount: Number(state.toolCallCount || 0),
                decisionRepairCount: Number(state.decisionRepairCount || 0),
                finishReason: String(state.finishReason || '')
                    .trim()
                    .toUpperCase(),
                subStage: '',
                subStageLabel: '',
                progressMessage: '',
                at: String(payload.updatedAt || '').trim(),
                requestId: String(requestId || payload.requestId || '').trim(),
            };
        },
        extractHistoryDocuments(rawValue) {
            const asArray = this.parseJsonArray(rawValue);
            if (asArray.length > 0) {
                return asArray;
            }
            const asObject = this.parseJsonObject(rawValue);
            if (Array.isArray(asObject?.documents)) {
                return asObject.documents;
            }
            if (Array.isArray(asObject?.citations)) {
                return asObject.citations;
            }
            return [];
        },
        extractHistoryArtifactSegment(rawValue, params = {}) {
            const artifact = this.resolveHistoryArtifactPayload(rawValue, params);
            if (!this.hasArtifactPayload(artifact)) {
                return null;
            }
            return this.buildArtifactSegment(artifact);
        },
        resolveHistoryArtifactPayload(rawValue, params = {}) {
            const primaryArtifact = this.parseArtifactSummary(rawValue);
            if (this.hasArtifactPayload(primaryArtifact)) {
                return primaryArtifact;
            }
            const fallbackArtifact = this.parseArtifactSummary(
                params?.final_artifact ?? params?.finalArtifact
            );
            if (this.hasArtifactPayload(fallbackArtifact)) {
                return fallbackArtifact;
            }
            return {};
        },
        parseJsonArray(value) {
            if (Array.isArray(value)) {
                return value;
            }
            if (typeof value !== 'string' || !value.trim()) {
                return [];
            }
            try {
                const parsed = JSON.parse(value);
                return Array.isArray(parsed) ? parsed : [];
            } catch (error) {
                return [];
            }
        },
        parseJsonObject(value) {
            if (value && typeof value === 'object' && !Array.isArray(value)) {
                return value;
            }
            if (typeof value !== 'string' || !value.trim()) {
                return {};
            }
            try {
                const parsed = JSON.parse(value);
                return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
            } catch (error) {
                return {};
            }
        },
        parseRuntimeToolMeta(name, rawArguments) {
            const toolName = String(name || 'unknown').trim() || 'unknown';
            const argumentsText = typeof rawArguments === 'string' ? rawArguments : '';
            // 工具名到显示标签的映射（支持直接调用格式）
            const toolLabelMap = {
                file_read: '读取文件',
                file_write: '写入文件',
                list_dir: '查看目录',
                stat: '查看属性',
                run_python: '执行 Python',
                write_artifact: '写出产物',
            };
            // 直接调用格式：工具名就是 action
            if (toolName !== 'runtime_tool') {
                const toolLabel = toolLabelMap[toolName] || toolName;
                return {
                    name: toolName,
                    displayName: toolLabel,
                    action: toolName,
                    actionLabel: toolLabel,
                    inputText: argumentsText,
                };
            }
            // runtime_tool 包装格式
            const payload = this.parseJsonObject(argumentsText);
            const action = String(payload?.action || payload?.arg0 || '').trim();
            const rawParams = payload?.params ?? payload?.arg1;
            const params =
                rawParams && typeof rawParams === 'string'
                    ? this.parseJsonObject(rawParams)
                    : rawParams;
            const paramsText =
                params && typeof params === 'object'
                    ? JSON.stringify(params, null, 2)
                    : argumentsText;
            const actionLabel = toolLabelMap[action] || action || 'unknown';
            return {
                name: toolName,
                displayName:
                    payload.displayName || (action ? `runtime_tool / ${action}` : 'runtime_tool'),
                action,
                actionLabel,
                inputText: paramsText,
            };
        },
        stringifyToolPayload(value) {
            if (value == null) {
                return '';
            }
            if (typeof value === 'string') {
                return value;
            }
            if (typeof value === 'object') {
                try {
                    return JSON.stringify(value, null, 2);
                } catch (error) {
                    return String(value);
                }
            }
            return String(value);
        },
        extractToolResponseText(payload = {}) {
            if (!payload || typeof payload !== 'object') {
                return '';
            }
            const candidates = [
                payload.response,
                payload.output,
                payload.result,
                payload.textOutput,
                payload.data,
            ];
            for (const candidate of candidates) {
                const text = this.stringifyToolPayload(candidate);
                if (typeof text === 'string' && text.trim()) {
                    return text;
                }
            }
            if (payload.errorMessage) {
                const code = String(payload.errorCode || '').trim();
                const message = String(payload.errorMessage || '').trim();
                return code ? `${code}: ${message}` : message;
            }
            return '';
        },
        normalizeHistorySegments(rawValue) {
            const input = this.parseJsonArray(rawValue);
            if (!input.length) {
                return [];
            }
            const output = [];
            input.forEach(item => {
                if (!item || typeof item !== 'object') {
                    return;
                }
                const segment = { ...item };
                if (segment.type === ChatSegmentType.text) {
                    const extraction = this.extractHtmlBlocks(String(segment.text || ''));
                    if (extraction.text) {
                        output.push({
                            ...segment,
                            text: extraction.text,
                        });
                    }
                    extraction.htmlBlocks.forEach(html => {
                        output.push(this.buildHtmlSegment(html));
                    });
                    return;
                }
                if (segment.type === ChatSegmentType.tool) {
                    const responseText = String(segment.response || '');
                    if (!segment.renderPayload && responseText) {
                        segment.renderPayload = this.resolveRenderPayload(
                            segment.name || 'unknown',
                            responseText
                        );
                    }
                    if (segment.open === undefined) {
                        segment.open = false;
                    }
                    if (segment.inputExpanded === undefined) {
                        segment.inputExpanded = true;
                    }
                    if (segment.outputExpanded === undefined) {
                        segment.outputExpanded = true;
                    }
                    output.push(segment);
                    return;
                }
                if (segment.type === ChatSegmentType.artifact) {
                    segment.previewUrl = this.resolveArtifactPreviewUrl(segment);
                    if (segment.previewable == null) {
                        const fileName = String(segment.fileName || '')
                            .trim()
                            .toLowerCase();
                        const contentType = String(segment.contentType || '')
                            .trim()
                            .toLowerCase();
                        segment.previewable =
                            contentType.startsWith('text/html') ||
                            fileName.endsWith('.html') ||
                            fileName.endsWith('.htm');
                    }
                }
                output.push(segment);
            });
            // 在最后统一追加所有 artifact
            const artifacts = [];
            for (const segment of output) {
                if (segment.type === ChatSegmentType.tool && !segment.renderPayload) {
                    const artifactSegment = this.extractArtifactSegmentFromToolPayload(
                        segment.name || 'unknown',
                        segment.action || '',
                        segment
                    );
                    if (
                        artifactSegment &&
                        !artifacts.some(
                            item =>
                                item?.type === ChatSegmentType.artifact &&
                                item?.key === artifactSegment.key
                        ) &&
                        !output.some(
                            item =>
                                item?.type === ChatSegmentType.artifact &&
                                item?.key === artifactSegment.key
                        )
                    ) {
                        artifacts.push(artifactSegment);
                    }
                }
            }
            // 将 artifacts 追加到 output 末尾
            if (artifacts.length > 0) {
                output.push(...artifacts);
            }
            return output;
        },
        restoreLatestHistoryPreview(messages = []) {
            for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
                const message = messages[messageIndex];
                const segments = Array.isArray(message?.segments) ? message.segments : [];
                for (let segmentIndex = segments.length - 1; segmentIndex >= 0; segmentIndex -= 1) {
                    const segment = segments[segmentIndex];
                    if (segment?.type === ChatSegmentType.artifact && segment?.previewable) {
                        this.openHtmlPreview(segment);
                        return;
                    }
                    if (segment?.type === 'html' && String(segment?.html || '').trim()) {
                        this.openHtmlPreview(segment);
                        return;
                    }
                }
            }
            this.activeHtml = null;
        },
        upsertRuntimeEngineSegmentFromPhase(payload, requestId) {
            const normalizedRequestId = String(requestId || '').trim();
            if (!normalizedRequestId) {
                return false;
            }
            const key = `runtime-engine:${normalizedRequestId}`;
            const previous =
                this.runtimeEngineStateMap[key] &&
                typeof this.runtimeEngineStateMap[key] === 'object'
                    ? this.runtimeEngineStateMap[key]
                    : null;
            const phase = String(payload?.phase || '')
                .trim()
                .toUpperCase();
            if (!phase) {
                return false;
            }
            const currentNode = this.resolveRuntimeNodeFromPhase(phase);
            const previousHistory = Array.isArray(previous?.history)
                ? previous.history.slice()
                : [];
            const lastEntry = previousHistory.length
                ? previousHistory[previousHistory.length - 1]
                : null;
            if (!lastEntry || lastEntry.phase !== phase) {
                previousHistory.push({
                    sequence: previousHistory.length + 1,
                    node: currentNode,
                    kind: phase === 'COMPLETED' ? 'completed' : 'phase',
                    status: phase === 'COMPLETED' ? 'completed' : 'running',
                    phase,
                    at: String(payload?.at || '').trim(),
                });
            }
            const runtimeEnginePayload = {
                type: ChatSegmentType.runtimeEngine,
                key,
                requestId: normalizedRequestId,
                engine: String(previous?.engine || 'graph').trim(),
                preview: previous?.preview !== false,
                stream: previous?.stream !== false,
                status:
                    phase === 'COMPLETED'
                        ? 'completed'
                        : phase === 'FAILED' || phase === 'CANCELLED'
                          ? 'failed'
                          : 'running',
                currentNode,
                currentPhase: phase,
                state: {
                    ...(previous?.state && typeof previous.state === 'object'
                        ? previous.state
                        : {}),
                    phase,
                    mode: payload?.mode || '',
                    iterationCount: Number(payload?.iterationCount || 0),
                    llmCallCount: Number(payload?.llmCallCount || 0),
                    toolCallCount: Number(payload?.toolCallCount || 0),
                    decisionRepairCount: Number(payload?.decisionRepairCount || 0),
                    finishReason: payload?.finishReason || '',
                    subStage: payload?.subStage || '',
                    subStageLabel: payload?.subStageLabel || '',
                    progressMessage: payload?.progressMessage || '',
                },
                history: previousHistory,
                nodes: Array.isArray(previous?.nodes) ? previous.nodes : [],
                updatedAt: String(payload?.at || '').trim(),
                open: previous?.open === true,
            };
            this.runtimeEngineStateMap[key] = runtimeEnginePayload;
            const target = this.getOrCreateAssistantMessage(normalizedRequestId);
            target.message.segments = target.message.segments || [];
            this.upsertRuntimeEngineSegment(target.message, runtimeEnginePayload);
            this.activeAssistantIndex = target.index;
            this.activeAssistantByRequest[normalizedRequestId] = target.index;
            return true;
        },
        upsertRuntimeEngineSegment(message, payload) {
            if (!message || !payload) {
                return false;
            }
            const segments = Array.isArray(message.segments) ? message.segments : [];
            const nextSegment = {
                ...payload,
                type: ChatSegmentType.runtimeEngine,
                open: payload.open === true,
            };
            const existingIndex = segments.findIndex(
                segment =>
                    segment?.type === ChatSegmentType.runtimeEngine &&
                    String(segment?.key || '').trim() === String(nextSegment.key || '').trim()
            );
            if (existingIndex >= 0) {
                const mergedSegment = {
                    ...segments[existingIndex],
                    ...nextSegment,
                };
                segments.splice(existingIndex, 1);
                segments.unshift(mergedSegment);
                return true;
            }
            segments.unshift(nextSegment);
            return true;
        },
        collectAssistantTextContent(message) {
            if (!message || !Array.isArray(message.segments)) {
                return '';
            }
            return message.segments.reduce((combined, segment) => {
                if (segment?.type !== ChatSegmentType.text) {
                    return combined;
                }
                const text = typeof segment?.text === 'string' ? segment.text : '';
                return text ? combined + text : combined;
            }, '');
        },
        findLastRenderableSegment(message) {
            if (!message || !Array.isArray(message.segments)) {
                return { index: -1, segment: null };
            }
            for (let index = message.segments.length - 1; index >= 0; index -= 1) {
                const segment = message.segments[index];
                if (segment?.type === ChatSegmentType.runtimeEngine) {
                    continue;
                }
                return { index, segment };
            }
            return { index: -1, segment: null };
        },
        buildRuntimeHistoryFromPhaseTrace(phaseTrace = []) {
            if (!Array.isArray(phaseTrace) || !phaseTrace.length) {
                return [];
            }
            const history = [];
            phaseTrace.forEach((item, index) => {
                const phase = String(item?.phase || '')
                    .trim()
                    .toUpperCase();
                if (!phase) {
                    return;
                }
                history.push({
                    sequence: index + 1,
                    node: this.resolveRuntimeNodeFromPhase(phase),
                    kind: phase === 'COMPLETED' ? 'completed' : 'phase',
                    status: phase === 'COMPLETED' ? 'completed' : 'running',
                    phase,
                    at: String(item?.at || '').trim(),
                });
            });
            return history;
        },
        resolveRuntimeNodeFromPhase(phase) {
            const normalized = String(phase || '')
                .trim()
                .toUpperCase();
            const mapping = {
                TRIAGE: 'triageNode',
                REASONING: 'reasoningNode',
                ACTION: 'actionNode',
                OBSERVATION: 'observationNode',
                FINAL_STREAMING: 'finalAnswerNode',
                FINALIZING: 'finalAnswerNode',
                COMPLETED: 'finalAnswerNode',
                FAILED: 'finalAnswerNode',
                CANCELLED: 'finalAnswerNode',
            };
            return mapping[normalized] || '';
        },
        messageHasSegmentType(message, segmentType) {
            if (!message || !Array.isArray(message.segments)) {
                return false;
            }
            return message.segments.some(segment => segment?.type === segmentType);
        },
        messageHasTextContent(message, requestId) {
            let targetMessage = message;
            if (!targetMessage && requestId) {
                const active = this.getActiveAssistantMessage(requestId);
                targetMessage = active?.message || null;
            }
            if (!targetMessage || !Array.isArray(targetMessage.segments)) {
                return false;
            }
            return targetMessage.segments.some(
                segment =>
                    segment?.type === ChatSegmentType.text && String(segment?.text || '').trim()
            );
        },
        extractArtifactSegmentFromHistoryToolEvents(toolEvents = []) {
            if (!Array.isArray(toolEvents) || !toolEvents.length) {
                return null;
            }
            for (const event of toolEvents) {
                const payload =
                    event?.content && typeof event.content === 'object' ? event.content : {};
                const artifact = payload?.artifact;
                if (artifact && typeof artifact === 'object') {
                    return {
                        type: ChatSegmentType.artifact,
                        fileName: artifact.fileName || artifact.filename || 'artifact',
                        downloadUrl: artifact.downloadUrl || artifact.url || '',
                        previewUrl:
                            artifact.previewUrl || artifact.downloadUrl || artifact.url || '',
                        previewable: artifact.previewable === true,
                        contentType: artifact.contentType || '',
                    };
                }
            }
            return null;
        },
        buildToolSegmentsFromHistory(toolEvents = []) {
            if (!Array.isArray(toolEvents) || !toolEvents.length) {
                return [];
            }
            const segments = [];
            const toolIndex = {};
            toolEvents.forEach(event => {
                const eventType = String(event?.type || '').trim();
                const payload =
                    event?.content && typeof event.content === 'object' ? event.content : {};
                const toolMeta = this.parseRuntimeToolMeta(
                    payload.name || 'unknown',
                    payload['arguments'] || ''
                );
                const name = toolMeta.name;
                const displayName = payload.displayName || toolMeta.displayName;
                const inputText = toolMeta.inputText;
                const key = payload.id || `${name}:${inputText}`;
                if (eventType === ChatStreamEventType.tool) {
                    const toolBlock = {
                        type: ChatSegmentType.tool,
                        id: payload.id || '',
                        key,
                        name,
                        displayName,
                        action: toolMeta.action,
                        actionLabel: toolMeta.actionLabel,
                        inputText,
                        response: '',
                        renderPayload: null,
                        status: ChatToolStatus.running,
                        open: false,
                    };
                    toolIndex[key] = segments.length;
                    segments.push(toolBlock);
                    return;
                }
                if (eventType !== ChatStreamEventType.result) {
                    return;
                }
                const responseText = this.extractToolResponseText(payload);
                const resultKey = payload.id || key;
                const index = toolIndex[resultKey];
                if (index !== undefined && segments[index]) {
                    segments[index].response = responseText;
                    segments[index].inputText = inputText || segments[index].inputText;
                    segments[index].renderPayload = this.resolveRenderPayload(
                        payload.name || segments[index].name,
                        responseText
                    );
                    segments[index].status = ChatToolStatus.done;
                    segments[index].justCompleted = true;
                    this.scheduleJustCompletedClear(segments[index]);
                    const artifactSegment = this.extractArtifactSegmentFromToolPayload(
                        name,
                        toolMeta.action,
                        payload
                    );
                    this.appendArtifactSegmentToList(segments, artifactSegment);
                    return;
                }
                toolIndex[resultKey] = segments.length;
                segments.push({
                    type: ChatSegmentType.tool,
                    id: payload.id || '',
                    key: resultKey,
                    name,
                    displayName,
                    action: toolMeta.action,
                    actionLabel: toolMeta.actionLabel,
                    inputText,
                    response: responseText,
                    renderPayload: this.resolveRenderPayload(name, responseText),
                    status: ChatToolStatus.done,
                    justCompleted: true,
                    open: false,
                });
                this.scheduleJustCompletedClear(segments[segments.length - 1]);
                const artifactSegment = this.extractArtifactSegmentFromToolPayload(
                    name,
                    toolMeta.action,
                    payload
                );
                this.appendArtifactSegmentToList(segments, artifactSegment);
            });
            return segments;
        },
        normalizeMessageTime(value) {
            return this.formatDisplayTime(value);
        },
        formatDisplayTime(value) {
            const fallback = this.formatLocalDateTime(new Date());
            if (value instanceof Date && !Number.isNaN(value.getTime())) {
                return this.formatLocalDateTime(value);
            }
            if (typeof value === 'number' && Number.isFinite(value)) {
                return this.formatDisplayTime(new Date(value));
            }
            if (typeof value !== 'string') {
                return fallback;
            }
            const normalized = value.trim();
            if (!normalized) {
                return fallback;
            }
            const normalizedDate = normalized.replace('T', ' ');
            if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,3})?$/.test(normalizedDate)) {
                return normalizedDate.slice(0, 19);
            }
            const parsed = new Date(normalized.replace(' ', 'T'));
            if (!Number.isNaN(parsed.getTime())) {
                return this.formatLocalDateTime(parsed);
            }
            return normalized;
        },
        formatLocalDateTime(value) {
            const date = value instanceof Date ? value : new Date(value);
            if (Number.isNaN(date.getTime())) {
                return '';
            }
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            const seconds = String(date.getSeconds()).padStart(2, '0');
            return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
        },
        handleToolEvent(eventType, content, requestId) {
            const payload = content || {};
            const toolMeta = this.parseRuntimeToolMeta(
                payload.name || 'unknown',
                payload['arguments'] || ''
            );
            const name = toolMeta.name;
            const displayName = payload.displayName || toolMeta.displayName;
            const inputText = toolMeta.inputText;
            const key = payload.id || `${name}:${inputText}`;
            const responseText = this.extractToolResponseText(payload);
            const dedupeKey = `${eventType}:${key}:${responseText}`;
            if (this.seenToolEvents[dedupeKey]) {
                return;
            }
            this.seenToolEvents[dedupeKey] = true;
            if (eventType === ChatStreamEventType.skill) {
                return;
            }
            if (eventType === ChatStreamEventType.tool) {
                const target = this.getOrCreateAssistantMessage(requestId);
                const toolBlock = {
                    id: payload.id || '',
                    key,
                    name,
                    displayName,
                    action: toolMeta.action,
                    actionLabel: toolMeta.actionLabel,
                    inputText,
                    response: '',
                    renderPayload: null,
                    status: ChatToolStatus.running,
                    open: false,
                };
                target.message.segments = target.message.segments || [];
                target.message.segments.push({ type: ChatSegmentType.tool, ...toolBlock });
                this.toolEventIndex[key] = {
                    msgIndex: target.index,
                    segIndex: target.message.segments.length - 1,
                };
                this.activeAssistantIndex = target.index;
                if (requestId) {
                    this.activeAssistantByRequest[requestId] = target.index;
                }
                this.shouldAutoScroll = true;
                this.touchMessages();
                return;
            }
            const resultKey = payload.id || key;
            const mapping = this.toolEventIndex[resultKey];
            if (mapping && this.messages[mapping.msgIndex]) {
                const base = this.messages[mapping.msgIndex];
                const toolBlock = base.segments ? base.segments[mapping.segIndex] : null;
                if (toolBlock) {
                    toolBlock.response = responseText;
                    toolBlock.inputText = inputText || toolBlock.inputText;
                    toolBlock.renderPayload = this.resolveRenderPayload(
                        payload.name || toolBlock.name,
                        responseText
                    );
                    toolBlock.status = ChatToolStatus.done;
                    toolBlock.justCompleted = true;
                    this.scheduleJustCompletedClear(toolBlock);
                }
                const artifactSegment = this.extractArtifactSegmentFromToolPayload(
                    name,
                    toolMeta.action,
                    payload
                );
                this.appendArtifactSegment(base, artifactSegment, {
                    autoPreview: this.shouldAutoPreviewArtifact(requestId),
                });
                this.activeAssistantIndex = mapping.msgIndex;
                if (requestId) {
                    this.activeAssistantByRequest[requestId] = mapping.msgIndex;
                }
                this.shouldAutoScroll = true;
                this.touchMessages();
            } else {
                const target = this.getOrCreateAssistantMessage(requestId);
                const toolBlock = {
                    id: payload.id || '',
                    key: resultKey,
                    name,
                    displayName,
                    action: toolMeta.action,
                    actionLabel: toolMeta.actionLabel,
                    inputText,
                    response: responseText,
                    renderPayload: this.resolveRenderPayload(name, responseText),
                    status: ChatToolStatus.done,
                    justCompleted: true,
                    open: false,
                };
                this.scheduleJustCompletedClear(toolBlock);
                target.message.segments = target.message.segments || [];
                target.message.segments.push({ type: ChatSegmentType.tool, ...toolBlock });
                const artifactSegment = this.extractArtifactSegmentFromToolPayload(
                    name,
                    toolMeta.action,
                    payload
                );
                this.appendArtifactSegment(target.message, artifactSegment, {
                    autoPreview: this.shouldAutoPreviewArtifact(requestId),
                });
                this.toolEventIndex[resultKey] = {
                    msgIndex: target.index,
                    segIndex: target.message.segments.length - 1,
                };
                this.activeAssistantIndex = target.index;
                if (requestId) {
                    this.activeAssistantByRequest[requestId] = target.index;
                }
                this.shouldAutoScroll = true;
                this.touchMessages();
            }
        },
        scheduleJustCompletedClear(toolBlock) {
            if (!toolBlock) return;
            setTimeout(() => {
                if (toolBlock.justCompleted) {
                    toolBlock.justCompleted = false;
                    this.touchMessages();
                }
            }, 2000);
        },
        resolveRenderPayload(toolName, response) {
            const payload = this.parseJsonObject(response);
            if (String(payload?.type || '').trim() !== 'frontend_render') {
                return null;
            }
            this.$emit('render-payload', payload);
            return payload;
        },
        emitLatestRenderPayload() {
            for (
                let messageIndex = this.messages.length - 1;
                messageIndex >= 0;
                messageIndex -= 1
            ) {
                const message = this.messages[messageIndex];
                const segments = Array.isArray(message?.segments) ? message.segments : [];
                for (let segmentIndex = segments.length - 1; segmentIndex >= 0; segmentIndex -= 1) {
                    const segment = segments[segmentIndex];
                    if (segment?.type === ChatSegmentType.tool && segment?.renderPayload) {
                        this.$emit('render-payload', segment.renderPayload);
                        return;
                    }
                }
            }
            this.$emit('render-payload', null);
        },
        appendArtifactSegment(message, segment, options = {}) {
            if (!message || !segment) {
                return;
            }
            message.segments = message.segments || [];
            this.appendArtifactSegmentToList(message.segments, segment, options);
        },
        appendArtifactSegmentToList(segments, segment, options = {}) {
            if (!Array.isArray(segments) || !segment) {
                return;
            }
            const exists = segments.some(
                item => item?.type === ChatSegmentType.artifact && item?.key === segment.key
            );
            if (!exists) {
                segments.push(segment);
                this.autoPreviewArtifactSegment(segment, options);
            }
        },
        shouldAutoPreviewArtifact(requestId) {
            const normalizedRequestId = String(requestId || '').trim();
            return (
                Boolean(normalizedRequestId) &&
                normalizedRequestId === String(this.activeRequestId || '').trim()
            );
        },
        autoPreviewArtifactSegment(segment, options = {}) {
            if (!options?.autoPreview || !segment?.previewable) {
                return;
            }
            if (this.previewMode === 'external') {
                this.$emit('artifact-preview', segment);
                return;
            }
            this.$nextTick(() => {
                this.openHtmlPreview(segment);
            });
        },
        extractArtifactSegmentFromToolPayload(toolName, action, payload) {
            if (!this.isArtifactToolResult(toolName, action, payload)) {
                return null;
            }
            const artifact = this.extractArtifactPayload(payload);
            if (!this.hasArtifactPayload(artifact)) {
                return null;
            }
            return this.buildArtifactSegment(artifact);
        },
        hasArtifactPayload(artifact) {
            if (!artifact || typeof artifact !== 'object') {
                return false;
            }
            return Boolean(
                String(artifact.fileName || '').trim() ||
                String(artifact.downloadUrl || '').trim() ||
                String(artifact.objectName || '').trim()
            );
        },
        isArtifactToolResult(toolName, action, payload) {
            const normalizedToolName = String(toolName || '').trim();
            // 支持直接调用格式：write_artifact 或 writeArtifact
            if (normalizedToolName === 'write_artifact' || normalizedToolName === 'writeArtifact') {
                return true;
            }
            if (normalizedToolName !== 'runtime_tool') {
                return false;
            }
            const responseObject = this.parseJsonObject(payload?.response);
            const normalizedAction = String(
                action || payload?.action || responseObject?.action || ''
            )
                .trim()
                .toLowerCase();
            return normalizedAction === 'write_artifact';
        },
        extractArtifactPayload(payload) {
            const responseObject = this.parseJsonObject(payload?.response);
            const dataObject =
                responseObject?.data && typeof responseObject.data === 'object'
                    ? responseObject.data
                    : {};
            if (dataObject?.artifact && typeof dataObject.artifact === 'object') {
                return dataObject.artifact;
            }
            if (responseObject?.artifact && typeof responseObject.artifact === 'object') {
                return responseObject.artifact;
            }
            if (responseObject?.file && typeof responseObject.file === 'object') {
                return responseObject.file;
            }
            // 检查 payload.data.artifact（后端 RuntimeExecutionResult 返回的格式）
            const payloadDataObject =
                payload?.data && typeof payload.data === 'object' ? payload.data : {};
            if (payloadDataObject?.artifact && typeof payloadDataObject.artifact === 'object') {
                return payloadDataObject.artifact;
            }
            if (payload?.artifact && typeof payload.artifact === 'object') {
                return payload.artifact;
            }
            if (payload?.file && typeof payload.file === 'object') {
                return payload.file;
            }
            return {};
        },
        parseArtifactSummary(rawValue) {
            const asObject = this.parseJsonObject(rawValue);
            if (!asObject || typeof asObject !== 'object') {
                return {};
            }
            if (asObject?.artifact && typeof asObject.artifact === 'object') {
                return asObject.artifact;
            }
            if (asObject?.file && typeof asObject.file === 'object') {
                return asObject.file;
            }
            return asObject;
        },
        isHtmlArtifact(artifact) {
            if (!artifact || typeof artifact !== 'object') {
                return false;
            }
            const contentType = String(artifact.contentType || '')
                .trim()
                .toLowerCase();
            if (contentType.startsWith('text/html')) {
                return true;
            }
            const fileName = String(artifact.fileName || '')
                .trim()
                .toLowerCase();
            return fileName.endsWith('.html') || fileName.endsWith('.htm');
        },
        buildArtifactSegment(artifact) {
            const fileName = String(artifact?.fileName || 'generated-page.html').trim();
            const previewUrl = this.resolveArtifactPreviewUrl(artifact);
            const contentType = String(artifact?.contentType || 'text/html').trim();
            const previewable = this.isHtmlArtifact(artifact);
            const key =
                String(artifact?.id || '').trim() ||
                String(artifact?.objectName || '').trim() ||
                previewUrl ||
                fileName;
            const segment = {
                type: ChatSegmentType.artifact,
                key: `artifact:${key}`,
                title: fileName,
                fileName,
                size: contentType,
                contentType,
                previewUrl,
                downloadUrl: String(artifact?.downloadUrl || '').trim(),
                previewable,
            };
            return segment;
        },
        resolveArtifactPreviewUrl(artifact) {
            const previewUrl = String(artifact?.previewUrl || '').trim();
            if (previewUrl) {
                return previewUrl;
            }
            const downloadUrl = String(artifact?.downloadUrl || '').trim();
            if (!downloadUrl) {
                return '';
            }
            return downloadUrl.replace('/download?', '/preview?');
        },
        resolveAttachmentPreviewUrl(file) {
            const previewUrl = String(file?.previewUrl || '').trim();
            if (previewUrl) {
                return previewUrl;
            }
            const fromDownload = this.resolveArtifactPreviewUrl(file);
            if (fromDownload) {
                return fromDownload;
            }
            const objectName = this.resolveAttachmentObjectName(file);
            if (!objectName) {
                return '';
            }
            const fileName = String(file?.name || file?.fileName || 'preview').trim();
            return `/api/files/artifacts/${this.toArtifactId(objectName)}/preview?fileName=${encodeURIComponent(fileName)}`;
        },
        resolveAttachmentObjectName(file) {
            const objectName = String(file?.objectName || '').trim();
            if (objectName) {
                return objectName;
            }
            const path = String(file?.path || '').trim();
            if (path.startsWith('/uploads/')) {
                return path.slice('/uploads/'.length);
            }
            if (path.startsWith('minio://')) {
                return path.split('/').slice(3).join('/');
            }
            return '';
        },
        toArtifactId(objectName) {
            const bytes = new TextEncoder().encode(String(objectName || ''));
            let binary = '';
            for (let i = 0; i < bytes.length; i++) {
                binary += String.fromCharCode(bytes[i]);
            }
            return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        },
        handleCitationEvent(content, requestId) {
            let payload = content;
            if (typeof payload === 'string') {
                try {
                    payload = JSON.parse(payload);
                } catch (error) {
                    return;
                }
            }
            if (!payload || typeof payload !== 'object') {
                return;
            }

            const citationKey = `${requestId || 'default'}:${payload.indexId || payload.chunkId || payload.ref || ''}`;
            if (this.seenCitationEvents[citationKey]) {
                return;
            }
            this.seenCitationEvents[citationKey] = true;

            const target = this.getOrCreateAssistantMessage(requestId);
            target.message.segments = target.message.segments || [];
            target.message.segments.push({
                type: ChatSegmentType.citation,
                ref: payload.ref ?? null,
                fileName: payload.fileName || 'unknown',
                indexId: payload.indexId || '',
                snippet: payload.snippet || '',
                score: Number(payload.score || 0).toFixed(4),
            });
            this.activeAssistantIndex = target.index;
            if (requestId) {
                this.activeAssistantByRequest[requestId] = target.index;
            }
            this.shouldAutoScroll = true;
        },
        handleFallbackNoticeEvent(content, requestId) {
            const text = String(content || '').trim();
            if (!text) {
                return;
            }
            const target = this.getOrCreateAssistantMessage(requestId);
            target.message.segments = target.message.segments || [];
            target.message.segments.push({
                type: ChatSegmentType.fallbackNotice,
                text,
            });
            target.message.answerMode = 'LLM_FALLBACK';
            this.activeAssistantIndex = target.index;
            if (requestId) {
                this.activeAssistantByRequest[requestId] = target.index;
            }
            this.shouldAutoScroll = true;
        },
        extractFallbackNoticeFromAnswer(answer) {
            const raw = String(answer || '').trim();
            if (!raw) {
                return { body: '', notice: '' };
            }
            const notices = [
                '以上回答基于通用模型能力，非知识库依据。',
                '以下回答基于通用模型能力，非知识库依据。',
            ];
            for (const notice of notices) {
                const escaped = notice.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                const suffixPattern = new RegExp(`\\s*${escaped}\\s*$`);
                if (suffixPattern.test(raw)) {
                    return {
                        body: raw.replace(suffixPattern, '').trim(),
                        notice,
                    };
                }
                const prefixPattern = new RegExp(`^\\s*${escaped}\\s*`);
                if (prefixPattern.test(raw)) {
                    return {
                        body: raw.replace(prefixPattern, '').trim(),
                        notice,
                    };
                }
            }
            return { body: raw, notice: '' };
        },
        getOrCreateAssistantMessage(requestId) {
            if (requestId && this.activeAssistantByRequest[requestId] !== undefined) {
                const idx = this.activeAssistantByRequest[requestId];
                const existing = this.messages[idx];
                if (existing && existing.kind === ChatMessageKind.assistant) {
                    return { message: existing, index: idx };
                }
            }
            const assistantMessage = this.buildMessage(
                '助手',
                '',
                ChatMessageRender.markdown,
                ChatMessageKind.assistant,
                {
                    segments: [],
                    pending: false,
                    time: '',
                    answerMode: '',
                    routeReason: '',
                    fallbackReason: '',
                }
            );
            this.messages.push(assistantMessage);
            const idx = this.messages.length - 1;
            if (requestId) {
                this.activeAssistantByRequest[requestId] = idx;
            }
            this.requestScrollToLatest();
            return { message: assistantMessage, index: idx };
        },
        getActiveAssistantMessage(requestId) {
            if (requestId && this.activeAssistantByRequest[requestId] !== undefined) {
                const idx = this.activeAssistantByRequest[requestId];
                if (this.messages[idx]) {
                    return { message: this.messages[idx], index: idx };
                }
            }
            return null;
        },
        buildMessage(label, text, render, kind, meta = {}) {
            const hasExplicitTime = Object.prototype.hasOwnProperty.call(meta, 'time');
            return {
                roleLabel: label,
                text,
                render,
                kind,
                time: hasExplicitTime
                    ? this.formatDisplayTime(meta.time)
                    : this.formatDisplayTime(new Date()),
                ...meta,
            };
        },
        buildAttachmentOnlyMessage(files) {
            const safeFiles = Array.isArray(files) ? files.filter(Boolean) : [];
            if (!safeFiles.length) {
                return '';
            }
            if (safeFiles.length === 1) {
                return `已上传附件：${safeFiles[0].name || '未命名文件'}`;
            }
            return `已上传 ${safeFiles.length} 个附件`;
        },
        resolveMessageChunk(content, fallback) {
            if (typeof content === 'string') {
                return content;
            }
            if (typeof content === 'number' || typeof content === 'boolean') {
                return String(content);
            }
            if (content && typeof content === 'object') {
                const candidates = [
                    content.delta,
                    content.text,
                    content.answer,
                    content.message,
                    content.content,
                ];
                for (const item of candidates) {
                    if (typeof item === 'string') {
                        return item;
                    }
                    if (typeof item === 'number' || typeof item === 'boolean') {
                        return String(item);
                    }
                }
            }
            return typeof fallback === 'string' ? fallback : '';
        },
        applyAssistantChunk(chunk, assistantMessage, htmlBuffer, requestId) {
            const extraction = this.extractHtmlBlocks(htmlBuffer + chunk);
            if (extraction.text) {
                if (!assistantMessage) {
                    const active = this.getActiveAssistantMessage(requestId);
                    if (active) {
                        assistantMessage = active.message;
                    } else {
                        const created = this.getOrCreateAssistantMessage(requestId);
                        assistantMessage = created.message;
                        this.activeAssistantIndex = created.index;
                    }
                }
                assistantMessage.segments = assistantMessage.segments || [];
                const currentText = this.collectAssistantTextContent(assistantMessage);
                const tailSegmentInfo = this.findLastRenderableSegment(assistantMessage);
                const tailSegment = tailSegmentInfo.segment;
                const normalizedText = this.normalizeAssistantTextChunk(
                    extraction.text,
                    currentText
                );
                if (!normalizedText) {
                    return { assistantMessage, htmlBuffer: extraction.remainder };
                }
                if (tailSegment && tailSegment.type === ChatSegmentType.text) {
                    tailSegment.text += normalizedText;
                } else {
                    assistantMessage.segments.push({ type: 'text', text: normalizedText });
                }
                this.shouldAutoScroll = true;
            }
            if (extraction.htmlBlocks.length) {
                if (!assistantMessage) {
                    const active = this.getActiveAssistantMessage(requestId);
                    if (active) {
                        assistantMessage = active.message;
                    } else {
                        const created = this.getOrCreateAssistantMessage(requestId);
                        assistantMessage = created.message;
                        this.activeAssistantIndex = created.index;
                    }
                }
                assistantMessage.segments = assistantMessage.segments || [];
                extraction.htmlBlocks.forEach(html => {
                    assistantMessage.segments.push(this.buildHtmlSegment(html));
                });
                this.shouldAutoScroll = true;
            }
            return { assistantMessage, htmlBuffer: extraction.remainder };
        },
        normalizeAssistantTextChunk(chunk, existingText) {
            const nextChunk = typeof chunk === 'string' ? chunk : String(chunk || '');
            if (!nextChunk) {
                return '';
            }
            const currentText =
                typeof existingText === 'string' ? existingText : String(existingText || '');
            if (!currentText) {
                return nextChunk;
            }
            if (nextChunk === currentText || currentText.endsWith(nextChunk)) {
                return '';
            }
            if (nextChunk.startsWith(currentText)) {
                return nextChunk.slice(currentText.length);
            }
            return nextChunk;
        },
        extractHtmlBlocks(value) {
            const blocks = [];
            let textOut = '';
            let remaining = value;
            while (remaining) {
                const startIndex = this.findHtmlStart(remaining);
                if (startIndex === -1) {
                    textOut += remaining;
                    remaining = '';
                    break;
                }
                textOut += remaining.slice(0, startIndex);
                const endIndex = this.findHtmlEnd(remaining, startIndex);
                if (endIndex === -1) {
                    remaining = remaining.slice(startIndex);
                    break;
                }
                const html = remaining.slice(startIndex, endIndex + 7);
                blocks.push(html);
                remaining = remaining.slice(endIndex + 7);
            }
            return { text: textOut, remainder: remaining, htmlBlocks: blocks };
        },
        findHtmlStart(text) {
            const lower = text.toLowerCase();
            const doctypeIndex = lower.indexOf('<!doctype html');
            const htmlIndex = lower.indexOf('<html');
            if (doctypeIndex === -1) {
                return htmlIndex;
            }
            if (htmlIndex === -1) {
                return doctypeIndex;
            }
            return Math.min(doctypeIndex, htmlIndex);
        },
        findHtmlEnd(text, startIndex) {
            const lower = text.toLowerCase();
            return lower.indexOf('</html>', startIndex);
        },
        buildHtmlSegment(html) {
            const title = this.extractHtmlTitle(html);
            return {
                type: 'html',
                title: title || 'HTML 预览',
                size: `${html.length} chars`,
                html,
            };
        },
        extractHtmlTitle(html) {
            const match = html.match(/<title[^>]*>([^<]+)<\/title>/i);
            return match ? match[1].trim() : '';
        },
        openHtmlPreview(segment) {
            // 取消之前的关闭定时器（快速切换预览时）
            if (this.previewClosingTimer) {
                clearTimeout(this.previewClosingTimer);
                this.previewClosingTimer = null;
            }
            if (this.previewMode === 'external') {
                this.$emit('artifact-preview', segment);
                return;
            }
            this.activeCitation = null;
            this.filePanelOpen = true;
            this.agentSettingsPanelOpen = false;
            if (segment?.type === ChatSegmentType.artifact) {
                if (!segment.previewable) {
                    return;
                }
                this.activeHtml = {
                    title: segment.fileName || segment.title || 'HTML 产物预览',
                    size: segment.size || segment.contentType || 'text/html',
                    src: segment.previewUrl || segment.downloadUrl || '',
                    html: '',
                    downloadUrl: segment.downloadUrl || '',
                    fileName: segment.fileName || '',
                };
                return;
            }
            this.activeHtml = {
                title: segment.title,
                size: segment.size,
                src: '',
                html: this.normalizeHtmlForPreview(segment.html),
                downloadUrl: '',
                fileName: '',
            };
        },
        openHtmlPreviewInNewWindow(payload) {
            const source = payload && typeof payload === 'object' ? payload : this.activeHtml;
            if (!source) {
                return;
            }
            if (source.src) {
                window.open(source.src, '_blank', 'noopener,noreferrer');
                return;
            }
            const html = this.normalizeHtmlForPreview(source.html || '');
            const blob = new Blob([html], { type: 'text/html' });
            const objectUrl = URL.createObjectURL(blob);
            const child = window.open(objectUrl, '_blank', 'noopener,noreferrer');
            if (!child) {
                window.location.href = objectUrl;
            }
            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        },
        openCitationPreview(citation) {
            if (!citation || typeof citation !== 'object') {
                return;
            }
            this.activeHtml = null;
            this.activeCitation = {
                ref: citation.ref ?? null,
                fileName: citation.fileName || 'unknown',
                indexId: citation.indexId || '',
                score: citation.score || '',
                snippet: citation.snippet || '',
            };
        },
        toggleFilePanel() {
            if (!this.showFileListPanel) {
                return;
            }
            if (this.isFilePanelOpen) {
                this.closePreview();
                return;
            }
            if (this.previewClosingTimer) {
                clearTimeout(this.previewClosingTimer);
                this.previewClosingTimer = null;
            }
            this.activeCitation = null;
            this.agentSettingsPanelOpen = false;
            this.filePanelOpen = true;
        },
        closePreview() {
            // 清除之前的定时器（防止快速重复开关）
            if (this.previewClosingTimer) {
                clearTimeout(this.previewClosingTimer);
                this.previewClosingTimer = null;
            }
            this.filePanelOpen = false;
            this.agentSettingsPanelOpen = false;
            this.agentSettingsWechatQrModalOpen = false;
            this.agentSettingsWechatQrCode = '';
            this.agentSettingsDingtalkQrModalOpen = false;
            this.agentSettingsDingtalkQrCode = '';
            this.stopAgentSettingsWechatPolling();
            this.stopAgentSettingsDingtalkRegisterPolling();
            this.stopAgentSettingsWecomPolling();
            // 延迟清空 activeHtml，等待预览面板动画完成
            // 动画时长 250ms，这里延迟 260ms 确保动画完成
            this.previewClosingTimer = setTimeout(() => {
                this.activeHtml = null;
                this.previewClosingTimer = null;
            }, 260);
        },
        closeCitationPreview() {
            this.activeCitation = null;
        },
        normalizeHtmlForPreview(html) {
            const safeHtml = (html || '').trim();
            if (!safeHtml) {
                return '<!doctype html><html><body></body></html>';
            }
            const baseTag = '<base href="/" />';
            const baseStyle =
                '<style>body{margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;}</style>';
            if (/<head[^>]*>/i.test(safeHtml)) {
                return safeHtml.replace(/<head[^>]*>/i, match => `${match}${baseTag}${baseStyle}`);
            }
            if (/<html[^>]*>/i.test(safeHtml)) {
                return safeHtml.replace(
                    /<html[^>]*>/i,
                    match => `${match}<head>${baseTag}${baseStyle}</head>`
                );
            }
            return `<!doctype html><html><head>${baseTag}${baseStyle}</head><body>${safeHtml}</body></html>`;
        },
        touchMessages() {
            this.messages = this.messages.slice();
        },
        requestScrollToLatest() {
            this.shouldAutoScroll = true;
            this.scrollToken += 1;
        },
    },
};
</script>

<style scoped>
.history-pane-motion-enter-active,
.history-pane-motion-leave-active {
    transition:
        width 220ms ease,
        opacity 180ms ease,
        transform 220ms ease,
        border-color 180ms ease;
}

.history-pane-motion-enter-from,
.history-pane-motion-leave-to {
    width: 0 !important;
    border-right-color: transparent;
    opacity: 0;
    transform: translateX(-8px);
}

.history-pane-motion-enter-to,
.history-pane-motion-leave-from {
    opacity: 1;
    transform: translateX(0);
}
</style>
