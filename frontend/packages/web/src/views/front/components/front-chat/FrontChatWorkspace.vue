<template>
    <section class="relative flex min-h-0 flex-1 overflow-hidden bg-white">
        <div
            class="hidden shrink-0 overflow-hidden transition-[width] duration-200 xl:block"
            :class="[
                isSidebarCollapsed ? 'w-14 border-r border-slate-200' : 'w-72 border-r border-slate-200',
            ]"
        >
            <ChatSidebar
                :collapsed="isSidebarCollapsed"
                :show-knowledge-select="showKnowledgeSelect"
                :selected-knowledge="selectedKnowledge"
                :search-keyword="conversationSearchKeyword"
                :knowledge-options="knowledgeOptions"
                :select-label="selectLabel"
                :conversation-items="filteredConversationItems"
                :deleting-conversation-ids="deletingConversationIds"
                :renaming-conversation-ids="renamingConversationIds"
                @update:selectedKnowledge="selectedKnowledge = $event"
                @update:searchKeyword="conversationSearchKeyword = $event"
                @toggle-sidebar="toggleSidebar"
                @new-chat="startNewChat"
                @delete-conversation="deleteConversation"
                @rename-conversation="renameConversation"
                @select-conversation="selectConversation"
            />
        </div>

        <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
            <ChatHeaderBar
                :title="headerTitle"
                :status-text="headerStatusText"
                :icon="headerIcon"
            />

            <ChatMessageStream
                :messages="messages"
                :scroll-token="scrollToken"
                :should-auto-scroll="shouldAutoScroll"
                :empty-title="emptyTitle"
                :empty-description="emptyDescription"
                :empty-icon="emptyIcon"
                @toggle-segment="toggleSegment"
                @open-html-preview="openHtmlPreview"
                @open-citation="openCitationPreview"
                @frontend-render-action="handleFrontendRenderAction"
            />

            <ChatComposer
                :draft="draft"
                :sending="sending"
                :pending-files="pendingFiles"
                :show-actions="enableAttachments"
                :placeholder="draftPlaceholder"
                :status-text="footerStatusText"
                :chat-error="chatError"
                :state-label="footerStateLabel"
                @update:draft="draft = $event"
                @submit="sendMessage"
                @trigger-file-picker="triggerFilePicker"
                @remove-pending-file="removePendingFile"
            />

            <input ref="fileInput" type="file" class="hidden" @change="handleFileChange" />
        </div>

        <ChatHtmlPreview :active-html="activeHtml" @close="closePreview" />
        <ChatCitationModal :active-citation="activeCitation" @close="closeCitationPreview" />
    </section>
</template>

<script>
import { ChatConversationBean } from '@/model/bean';
import {
    ChatMessageKind,
    ChatMessageRender,
    ChatSegmentType,
    ChatStreamEventType,
    ChatToolStatus,
} from '@/model/front-chat';
import { alert, confirm, prompt } from '@/composables/useModal';
import ChatCitationModal from './ChatCitationModal.vue';
import ChatComposer from './ChatComposer.vue';
import ChatHeaderBar from './ChatHeaderBar.vue';
import ChatHtmlPreview from './ChatHtmlPreview.vue';
import ChatMessageStream from './ChatMessageStream.vue';
import ChatSidebar from './ChatSidebar.vue';

export default {
    name: 'FrontChatWorkspace',
    components: {
        ChatSidebar,
        ChatHeaderBar,
        ChatMessageStream,
        ChatComposer,
        ChatHtmlPreview,
        ChatCitationModal,
    },
    emits: ['unauthorized', 'conversation-context-change', 'reset-chat-context'],
    props: {
        adapter: {
            type: Object,
            required: true,
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
        draftPlaceholder: {
            type: String,
            default: '',
        },
        footerStatusText: {
            type: String,
            default: '',
        },
        footerStateLabel: {
            type: String,
            default: 'Ready',
        },
        showKnowledgeSelect: {
            type: Boolean,
            default: false,
        },
        enableAttachments: {
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
            activeCitation: null,
            activeAssistantIndex: null,
            activeAssistantByRequest: {},
            shouldAutoScroll: true,
            scrollToken: 0,
            pendingFiles: [],
            selectedKnowledge: this.defaultKnowledge,
            isSidebarCollapsed: false,
            conversationItems: [],
            deletingConversationIds: [],
            renamingConversationIds: [],
            selectedConversationId: null,
            conversationSearchKeyword: '',
        };
    },
    computed: {
        filteredConversationItems() {
            const keyword = (this.conversationSearchKeyword || '').trim().toLowerCase();
            if (!keyword) {
                return this.conversationItems;
            }
            return this.conversationItems.filter(item =>
                String(item?.title || '')
                    .toLowerCase()
                    .includes(keyword)
            );
        },
    },
    mounted() {
        this.initializeSessionId();
        this.loadConversationItems();
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
    },
    methods: {
        parseStreamErrorResponse(response) {
            const status = Number(response?.status) || 0;
            const payload = response && response.data !== undefined ? response.data : '';
            const fallback = this.streamStatusFallback(status);
            if (payload && typeof payload === 'object') {
                return payload.message || payload?.data?.message || payload?.data?.error || fallback;
            }
            const text = String(payload || '').trim();
            if (!text) {
                return fallback;
            }
            try {
                const parsed = JSON.parse(text);
                if (parsed && typeof parsed === 'object') {
                    return parsed.message || parsed?.data?.message || parsed?.data?.error || fallback;
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
        extractKnowledgeOptionValue(option) {
            if (option && typeof option === 'object') {
                const rawValue = option.value ?? option.id ?? option.kbId ?? '';
                const text = String(rawValue ?? '').trim();
                return text;
            }
            return String(option ?? '').trim();
        },
        toggleSidebar() {
            this.isSidebarCollapsed = !this.isSidebarCollapsed;
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
            this.messages = [];
            this.pendingFiles = [];
            this.draft = '';
            this.chatError = '';
            this.activeHtml = null;
            this.activeCitation = null;
            this.toolEventIndex = {};
            this.seenToolEvents = {};
            this.seenCitationEvents = {};
            this.activeAssistantIndex = null;
            this.activeAssistantByRequest = {};
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
            const storageKey = this.getSessionStorageKey();
            if (storageKey) {
                window.localStorage.removeItem(storageKey);
            }
            this.sessionId = '';
        },
        async loadConversationItems(options = {}) {
            const reloadMessages = options.reloadMessages !== false;
            if (!this.adapter || typeof this.adapter.fetchConversationList !== 'function') {
                this.conversationItems = [];
                return;
            }
            try {
                const { data } = await this.adapter.fetchConversationList({
                    selectedKnowledge: this.selectedKnowledge,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const list = Array.isArray(data?.items) ? data.items : [];
                const normalizedItems = list.map(item => ChatConversationBean.fromApi(item));
                const currentActiveExists = normalizedItems.some(
                    item => item.id === this.selectedConversationId
                );
                if (currentActiveExists) {
                    // keep current selected id
                } else {
                    this.selectedConversationId = null;
                }
                const activeConversation =
                    normalizedItems.find(item => item.id === this.selectedConversationId) || null;
                if (activeConversation) {
                    this.applyConversationContext(activeConversation);
                }
                this.conversationItems = normalizedItems.map(item =>
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
                    this.sessionId = '';
                    this.messages = [];
                    const storageKey = this.getSessionStorageKey();
                    if (storageKey) {
                        window.localStorage.removeItem(storageKey);
                    }
                }
            } catch (error) {
                this.conversationItems = [];
                this.selectedConversationId = null;
                this.sessionId = '';
                this.messages = [];
            }
        },
        async selectConversation(item) {
            const conversationId = item?.id ?? null;
            if (!conversationId || conversationId === this.selectedConversationId) {
                return;
            }
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
            await this.loadConversationMessages(conversationId);
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
                if (this.adapter && typeof this.adapter.deleteConversation === 'function') {
                    await this.adapter.deleteConversation({
                        conversationId,
                        selectedKnowledge: this.selectedKnowledge,
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
                if (this.adapter && typeof this.adapter.renameConversation === 'function') {
                    await this.adapter.renameConversation({
                        conversationId,
                        selectedKnowledge: this.selectedKnowledge,
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
            const normalizedDraft = this.draft.trim();
            const hasAttachments = this.pendingFiles.length > 0;
            if (this.sending || (!normalizedDraft && !hasAttachments)) {
                return;
            }
            this.activeAssistantIndex = null;
            const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
            const displayText = normalizedDraft || this.buildAttachmentOnlyMessage(this.pendingFiles);
            const userMessage = this.buildMessage(
                '你',
                displayText,
                ChatMessageRender.plain,
                ChatMessageKind.user,
                {
                    attachments: this.pendingFiles.slice(),
                    rawText: normalizedDraft,
                }
            );
            this.messages.push(userMessage);
            this.requestScrollToLatest();
            this.chatError = '';
            this.sending = true;
            const assistantMessage = this.getOrCreateAssistantMessage(requestId);
            assistantMessage.message.pending = true;
            this.requestScrollToLatest();

            try {
                const response = await this.adapter.sendStream({
                    message: normalizedDraft,
                    fileIds: this.pendingFiles.map(file => file.id),
                    sessionId: this.sessionId,
                    selectedKnowledge: this.selectedKnowledge,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const ok = response.status >= 200 && response.status < 300;
                if (!ok) {
                    throw new Error(this.parseStreamErrorResponse(response));
                }
                await this.consumeSseStream(response, requestId);
                this.sending = false;
                assistantMessage.message.pending = false;
                delete this.activeAssistantByRequest[requestId];
                this.touchMessages();
                this.draft = '';
                this.pendingFiles = [];
                await this.loadConversationItems({ reloadMessages: false });
            } catch (error) {
                this.chatError = error.message || '请求失败';
            } finally {
                this.sending = false;
                assistantMessage.message.pending = false;
                delete this.activeAssistantByRequest[requestId];
            }
        },
        triggerFilePicker() {
            if (!this.enableAttachments) {
                return;
            }
            const input = this.$refs.fileInput;
            if (input) {
                input.click();
            }
        },
        async handleFileChange(event) {
            if (!this.enableAttachments) {
                event.target.value = '';
                return;
            }
            const file = event.target.files && event.target.files[0];
            if (!file) {
                return;
            }
            await this.uploadFile(file);
            event.target.value = '';
        },
        async uploadFile(file) {
            try {
                const uploadedFile = await this.adapter.uploadFile({
                    file,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                this.pendingFiles.push(uploadedFile);
            } catch (error) {
                this.chatError = error.message || '上传失败';
            }
        },
        removePendingFile(id) {
            this.pendingFiles = this.pendingFiles.filter(file => file.id !== id);
        },
        toggleSegment(segment) {
            segment.open = !segment.open;
            this.shouldAutoScroll = false;
            this.touchMessages();
        },
        async handleFrontendRenderAction(action) {
            const renderId = String(action?.renderId || '').trim();
            if (!renderId) {
                return;
            }
            this.updateRenderPayloadState(renderId, action?.state || {});
            this.touchMessages();
            if (!this.adapter || typeof this.adapter.sendStream !== 'function') {
                return;
            }
            if (this.sending) {
                return;
            }
            const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
            const eventMessage = this.buildFrontendActionMessage(action);
                const userMessage = this.buildMessage(
                    '你',
                    eventMessage,
                    ChatMessageRender.plain,
                    ChatMessageKind.user,
                    {
                        rawText: eventMessage,
                        messageType: 'event',
                        eventPayload: action,
                    }
                );
            this.messages.push(userMessage);
            this.requestScrollToLatest();
            this.chatError = '';
            this.sending = true;
            const assistantMessage = this.getOrCreateAssistantMessage(requestId);
            assistantMessage.message.pending = true;
            this.requestScrollToLatest();
            try {
                const response = await this.adapter.sendStream({
                    message: String(action?.message || eventMessage),
                    fileIds: [],
                    sessionId: this.sessionId,
                    selectedKnowledge: this.selectedKnowledge,
                    messageType: String(action?.messageType || 'event'),
                    eventPayload: action,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const ok = response.status >= 200 && response.status < 300;
                if (!ok) {
                    throw new Error(this.parseStreamErrorResponse(response));
                }
                await this.consumeSseStream(response, requestId);
                await this.loadConversationItems({ reloadMessages: false });
            } catch (error) {
                this.chatError = error?.message || '同步卡片状态失败';
            } finally {
                this.sending = false;
                assistantMessage.message.pending = false;
                delete this.activeAssistantByRequest[requestId];
                this.touchMessages();
            }
        },
        buildFrontendActionMessage(action) {
            return String(action?.message || '').trim();
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
            const baseValue =
                base && typeof base === 'object' && !Array.isArray(base) ? base : {};
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

            while (true) {
                const { value, done } = await reader.read();
                if (value) {
                    buffer += decoder.decode(value, { stream: true });
                }
                const lines = buffer.split(/\r?\n/);
                const tail = lines.pop() || '';
                buffer = done ? '' : tail;
                const chunkResult = this.consumeSseLines(lines, requestId, assistantMessage, htmlBuffer);
                assistantMessage = chunkResult.assistantMessage;
                htmlBuffer = chunkResult.htmlBuffer;
                if (chunkResult.finished) {
                    return;
                }
                if (done) {
                    if (tail.trim()) {
                        const tailResult = this.consumeSseLines([tail], requestId, assistantMessage, htmlBuffer);
                        if (tailResult.finished) {
                            return;
                        }
                    }
                    break;
                }
            }
        },
        consumeSseLines(lines, requestId, assistantMessage, htmlBuffer) {
            let nextAssistantMessage = assistantMessage;
            let nextHtmlBuffer = htmlBuffer;
            for (const line of lines) {
                const trimmed = String(line || '').trim();
                if (!trimmed || !trimmed.startsWith('data:')) {
                    continue;
                }
                const data = trimmed.slice(5).trim();
                const parsed = this.adapter.parseEventPayload(data);
                if (parsed.type === ChatStreamEventType.done) {
                    return {
                        assistantMessage: nextAssistantMessage,
                        htmlBuffer: nextHtmlBuffer,
                        finished: true,
                    };
                }
                if (parsed.type === ChatStreamEventType.meta) {
                    this.handleMetaEvent(parsed.content, requestId);
                    continue;
                }
                if (
                    parsed.type === ChatStreamEventType.tool ||
                    parsed.type === ChatStreamEventType.skill ||
                    parsed.type === ChatStreamEventType.result
                ) {
                    this.handleToolEvent(parsed.type, parsed.content, requestId);
                    const active = this.getActiveAssistantMessage(requestId);
                    nextAssistantMessage = active ? active.message : null;
                    nextHtmlBuffer = '';
                    this.touchMessages();
                    continue;
                }
                if (parsed.type === ChatStreamEventType.citation) {
                    this.handleCitationEvent(parsed.content, requestId);
                    const active = this.getActiveAssistantMessage(requestId);
                    nextAssistantMessage = active ? active.message : null;
                    this.touchMessages();
                    continue;
                }
                if (parsed.type === ChatStreamEventType.fallbackNotice) {
                    this.handleFallbackNoticeEvent(parsed.content, requestId);
                    const active = this.getActiveAssistantMessage(requestId);
                    nextAssistantMessage = active ? active.message : null;
                    this.touchMessages();
                    continue;
                }
                if (parsed.type === ChatStreamEventType.error) {
                    this.chatError = parsed.content || '';
                    continue;
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
        async loadConversationMessages(conversationId) {
            if (
                !conversationId ||
                !this.adapter ||
                typeof this.adapter.fetchMessages !== 'function'
            ) {
                this.messages = [];
                return;
            }
            try {
                const conversation = this.conversationItems.find(item => item.id === conversationId) || null;
                const { data } = await this.adapter.fetchMessages({
                    conversationId,
                    selectedKnowledge: this.selectedKnowledge,
                    sessionType: conversation?.sessionType,
                    scopeId: conversation?.scopeId,
                    pageNo: 1,
                    pageSize: 200,
                    onUnauthorized: () => this.$emit('unauthorized'),
                });
                const rows = Array.isArray(data?.items) ? data.items : [];
                this.messages = this.hydrateMessagesFromHistory(rows);
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
        hydrateMessagesFromHistory(rows = []) {
            const output = [];
            rows.forEach(row => {
                const query = String(row?.query || '').trim();
                const messageType = String(row?.messageType || 'normal').trim() || 'normal';
                const answer = String(row?.answer || '').trim();
                const status = String(row?.status || '').trim().toLowerCase();
                const error = String(row?.error || '').trim();
                const createdAt = this.normalizeMessageTime(row?.createdAt);
                const updatedAt = this.normalizeMessageTime(row?.updatedAt || row?.createdAt);
                const params = this.parseJsonObject(row?.paramsJson);
                const attachments = this.parseJsonArray(row?.fileList).map(file => ({
                    id: file?.id || file?.path || file?.name || `${row?.id || 'file'}`,
                    name: file?.name || file?.fileName || '未命名文件',
                    size: Number(file?.size || 0),
                    path: file?.path || '',
                }));

                if (query) {
                    output.push(
                        this.buildMessage(
                            '你',
                            query,
                            ChatMessageRender.plain,
                            ChatMessageKind.user,
                            {
                                time: createdAt,
                                attachments,
                                messageType,
                            }
                        )
                    );
                }

                const assistant = this.buildMessage(
                    '助手',
                    '',
                    ChatMessageRender.markdown,
                    ChatMessageKind.assistant,
                    {
                        segments: [],
                        pending: false,
                        time: updatedAt,
                        answerMode: '',
                        routeReason: '',
                        fallbackReason: '',
                    }
                );
                assistant.segments.push(...this.buildToolSegmentsFromHistory(params?.toolEvents));

                const parsedAnswer = this.extractFallbackNoticeFromAnswer(answer);
                if (parsedAnswer.body) {
                    assistant.segments.push({ type: ChatSegmentType.text, text: parsedAnswer.body });
                }
                if (parsedAnswer.notice) {
                    assistant.segments.push({
                        type: ChatSegmentType.fallbackNotice,
                        text: parsedAnswer.notice,
                    });
                }

                const documents = this.parseJsonArray(row?.documentList);
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

                if (status === ChatStreamEventType.error && error) {
                    assistant.segments.push({ type: ChatSegmentType.text, text: `错误：${error}` });
                } else if (status === ChatToolStatus.interrupted && !answer) {
                    assistant.segments.push({ type: ChatSegmentType.text, text: '回答已中断。' });
                }

                if (parsedAnswer.notice) {
                    assistant.answerMode = 'LLM_FALLBACK';
                } else if (documents.length > 0) {
                    assistant.answerMode = 'KB_QA';
                }

                if (assistant.segments.length > 0) {
                    output.push(assistant);
                }
            });
            return output;
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
        buildToolSegmentsFromHistory(toolEvents = []) {
            if (!Array.isArray(toolEvents) || !toolEvents.length) {
                return [];
            }
            const segments = [];
            const toolIndex = {};
            toolEvents.forEach(event => {
                const eventType = String(event?.type || '').trim();
                const payload = event?.content && typeof event.content === 'object' ? event.content : {};
                const name = payload.name || 'unknown';
                const displayName = payload.displayName || name;
                const inputText = payload['arguments'] || '';
                const key = payload.id || `${name}:${inputText}`;
                if (eventType === ChatStreamEventType.tool) {
                    const toolBlock = {
                        type: ChatSegmentType.tool,
                        id: payload.id || '',
                        key,
                        name,
                        displayName,
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
                const resultKey = payload.id || key;
                const index = toolIndex[resultKey];
                if (index !== undefined && segments[index]) {
                    segments[index].response = payload.response || '';
                    segments[index].inputText = inputText || segments[index].inputText;
                    segments[index].renderPayload = this.resolveRenderPayload(
                        payload.name || segments[index].name,
                        payload.response
                    );
                    segments[index].status = ChatToolStatus.done;
                    return;
                }
                toolIndex[resultKey] = segments.length;
                segments.push({
                    type: ChatSegmentType.tool,
                    id: payload.id || '',
                    key: resultKey,
                    name,
                    displayName,
                    inputText,
                    response: payload.response || '',
                    renderPayload: this.resolveRenderPayload(name, payload.response),
                    status: ChatToolStatus.done,
                    open: false,
                });
            });
            return segments;
        },
        normalizeMessageTime(value) {
            if (typeof value === 'string' && value.trim()) {
                return value.trim();
            }
            return new Date().toLocaleTimeString();
        },
        handleToolEvent(eventType, content, requestId) {
            const payload = content || {};
            const name = payload.name || 'unknown';
            const displayName = payload.displayName || name;
            const inputText = payload['arguments'] || '';
            const key = payload.id || `${name}:${inputText}`;
            const dedupeKey = `${eventType}:${key}:${payload.response || ''}`;
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
                    toolBlock.response = payload.response || '';
                    toolBlock.inputText = inputText || toolBlock.inputText;
                    toolBlock.renderPayload = this.resolveRenderPayload(
                        payload.name || toolBlock.name,
                        payload.response
                    );
                    toolBlock.status = ChatToolStatus.done;
                }
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
                    inputText,
                    response: payload.response || '',
                    renderPayload: this.resolveRenderPayload(name, payload.response),
                    status: ChatToolStatus.done,
                    open: false,
                };
                target.message.segments = target.message.segments || [];
                target.message.segments.push({ type: ChatSegmentType.tool, ...toolBlock });
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
        resolveRenderPayload(toolName, response) {
            const payload = this.parseJsonObject(response);
            if (String(payload?.type || '').trim() !== 'frontend_render') {
                return null;
            }
            return payload;
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
            return {
                roleLabel: label,
                text,
                render,
                kind,
                time: new Date().toLocaleTimeString(),
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
                const lastSegment = assistantMessage.segments[assistantMessage.segments.length - 1];
                if (lastSegment && lastSegment.type === 'text') {
                    lastSegment.text += extraction.text;
                } else {
                    assistantMessage.segments.push({ type: 'text', text: extraction.text });
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
            this.activeCitation = null;
            this.activeHtml = {
                title: segment.title,
                size: segment.size,
                html: this.normalizeHtmlForPreview(segment.html),
            };
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
        closePreview() {
            this.activeHtml = null;
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
