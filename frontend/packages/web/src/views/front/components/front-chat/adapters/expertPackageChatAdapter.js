import { requestJson as doRequestJson } from '@lingzhou/core/http/request';
import { EXPERT_SKILL_PACKAGE_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
import {
    createRequestOptions,
    buildRuntimeChatBody,
    sendRuntimeSseRequest,
    fetchConversationListRequest,
    fetchConversationMessagesRequest,
    deleteConversationRequest,
    renameConversationRequest,
    normalizeNumericId,
    parseSseEventPayload,
    createUploadProgressHandler,
    normalizeUploadedFileResponse,
} from './sseChatAdapterShared';

const SESSION_TYPE = 'EXPERT_SKILL_PACKAGE_CHAT';

export const expertPackageChatAdapter = {
    sessionStorageKey: EXPERT_SKILL_PACKAGE_CHAT_SESSION_STORAGE_KEY,
    continuationSessionTypes: [SESSION_TYPE],

    async sendStream({
        message,
        fileIds,
        sessionId,
        selectedKnowledge,
        messageType,
        eventPayload,
        systemPromptAppend,
        options,
        mentionedSkillId,
        chatModelId,
        onUnauthorized,
    }) {
        const packageId = normalizeNumericId(selectedKnowledge);
        if (packageId == null) {
            throw new Error('未选择专家技能包，请先选择一个专家包。');
        }
        return sendRuntimeSseRequest('/api/expert-packages/chat', {
            auth: true,
            onUnauthorized,
            body: buildRuntimeChatBody({
                message,
                fileIds,
                sessionId,
                messageType,
                eventPayload,
                systemPromptAppend,
                options,
                mentionedSkillId,
                chatModelId,
                extraBody: {
                    packageId,
                },
            }),
        });
    },

    async uploadFile({ file, sessionId, onUnauthorized, onProgress }) {
        const formData = new FormData();
        formData.append('file', file);
        if (sessionId) {
            formData.append('sessionId', sessionId);
        }

        const { data } = await doRequestJson(
            '/api/files/upload',
            createRequestOptions(
                {
                    method: 'POST',
                    auth: true,
                    body: formData,
                    onUploadProgress: createUploadProgressHandler(file, onProgress),
                },
                onUnauthorized
            )
        );

        return normalizeUploadedFileResponse(data, file);
    },

    async fetchConversationList({ pageNo = 1, pageSize = 20, onUnauthorized } = {}) {
        return fetchConversationListRequest('/api/chat/sessions', {
            auth: true,
            onUnauthorized,
            sessionType: SESSION_TYPE,
            pageNo,
            pageSize,
        });
    },

    async fetchMessages({
        conversationId,
        selectedKnowledge,
        pageNo = 1,
        pageSize = 100,
        sessionType = SESSION_TYPE,
        scopeId,
        onUnauthorized,
    } = {}) {
        const packageId =
            scopeId == null ? normalizeNumericId(selectedKnowledge) : normalizeNumericId(scopeId);
        return fetchConversationMessagesRequest('/api/chat/sessions', {
            conversationId,
            sessionType,
            scopeId: packageId,
            pageNo,
            pageSize,
            auth: true,
            onUnauthorized,
        });
    },

    async deleteConversation({
        conversationId,
        selectedKnowledge,
        sessionType = SESSION_TYPE,
        scopeId,
        onUnauthorized,
    } = {}) {
        const packageId =
            scopeId == null ? normalizeNumericId(selectedKnowledge) : normalizeNumericId(scopeId);
        return deleteConversationRequest('/api/chat/sessions', {
            conversationId,
            sessionType,
            scopeId: packageId,
            auth: true,
            onUnauthorized,
        });
    },

    async renameConversation({
        conversationId,
        selectedKnowledge,
        sessionType = SESSION_TYPE,
        scopeId,
        name,
        onUnauthorized,
    } = {}) {
        const packageId =
            scopeId == null ? normalizeNumericId(selectedKnowledge) : normalizeNumericId(scopeId);
        return renameConversationRequest('/api/chat/sessions', {
            conversationId,
            name,
            sessionType,
            scopeId: packageId,
            auth: true,
            onUnauthorized,
        });
    },

    parseEventPayload: parseSseEventPayload,
};
