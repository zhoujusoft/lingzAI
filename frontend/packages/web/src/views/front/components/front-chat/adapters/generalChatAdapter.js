import { GENERAL_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
import { listAvailableChatModels } from '@/api/model-library';
import { requestJson as doRequestJson } from '@lingzhou/core/http/request';
import {
    createRequestOptions,
    buildRuntimeChatBody,
    sendRuntimeSseRequest,
    fetchConversationListRequest,
    fetchConversationMessagesRequest,
    deleteConversationRequest,
    renameConversationRequest,
    parseSseEventPayload,
    createUploadProgressHandler,
    normalizeUploadedFileResponse,
} from './sseChatAdapterShared';

const SESSION_TYPE = 'GENERAL_CHAT';

export const generalChatAdapter = {
    sessionStorageKey: GENERAL_CHAT_SESSION_STORAGE_KEY,
    continuationSessionTypes: [SESSION_TYPE],

    async sendStream({
        message,
        fileIds,
        sessionId,
        messageType,
        eventPayload,
        systemPromptAppend,
        options,
        mentionedSkillId,
        chatModelId,
        onUnauthorized,
    }) {
        return sendRuntimeSseRequest('/api/chat', {
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
            }),
        });
    },

    async fetchChatModelOptions({ onUnauthorized } = {}) {
        return listAvailableChatModels(onUnauthorized);
    },

    async updateConversationModel({
        conversationId,
        sessionType = SESSION_TYPE,
        scopeId = null,
        modelId,
        onUnauthorized,
    } = {}) {
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { success: false };
        }
        const search = new URLSearchParams();
        search.set('sessionType', resolvedSessionType);
        if (scopeId != null && String(scopeId).trim() !== '') {
            search.set('scopeId', String(scopeId).trim());
        }
        const query = search.toString();
        const { data } = await doRequestJson(`/api/chat/sessions/${encoded}/model?${query}`, {
            method: 'PUT',
            auth: true,
            onUnauthorized,
            body: {
                modelId,
            },
        });
        return data;
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
            pageNo,
            pageSize,
        });
    },

    async fetchMessages({
        conversationId,
        pageNo = 1,
        pageSize = 100,
        sessionType = SESSION_TYPE,
        scopeId = null,
        onUnauthorized,
    } = {}) {
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        return fetchConversationMessagesRequest('/api/chat/sessions', {
            conversationId,
            sessionType: resolvedSessionType,
            scopeId,
            pageNo,
            pageSize,
            auth: true,
            onUnauthorized,
        });
    },

    async deleteConversation({
        conversationId,
        sessionType = SESSION_TYPE,
        scopeId = null,
        onUnauthorized,
    } = {}) {
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        return deleteConversationRequest('/api/chat/sessions', {
            conversationId,
            sessionType: resolvedSessionType,
            scopeId,
            auth: true,
            onUnauthorized,
        });
    },

    async renameConversation({
        conversationId,
        sessionType = SESSION_TYPE,
        scopeId = null,
        name,
        onUnauthorized,
    } = {}) {
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        return renameConversationRequest('/api/chat/sessions', {
            conversationId,
            name,
            sessionType: resolvedSessionType,
            scopeId,
            auth: true,
            onUnauthorized,
        });
    },

    parseEventPayload: parseSseEventPayload,
};
