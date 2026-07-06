import { requestJson as doRequestJson } from '@lingzhou/core/http/request';
import { SKILL_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
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

const SESSION_TYPE = 'SKILL_CHAT';
const PUBLISHED_SESSION_TYPE = 'PUBLISHED_SKILL_CHAT';

export const skillChatAdapter = {
    sessionStorageKey: SKILL_CHAT_SESSION_STORAGE_KEY,
    continuationSessionTypes: [SESSION_TYPE, PUBLISHED_SESSION_TYPE],

    async sendStream({
        message,
        fileIds,
        sessionId,
        selectedKnowledge,
        messageType,
        eventPayload,
        systemPromptAppend,
        options,
        onUnauthorized,
    }) {
        const skillId = normalizeNumericId(selectedKnowledge);
        if (skillId == null) {
            throw new Error('未选择技能，请先从技能市场进入或在当前页面选择技能。');
        }
        return sendRuntimeSseRequest('/api/skills/chat', {
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
                extraBody: {
                    skillId,
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
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const skillId =
            resolvedSessionType === SESSION_TYPE || resolvedSessionType === PUBLISHED_SESSION_TYPE
                ? scopeId == null
                    ? normalizeNumericId(selectedKnowledge)
                    : normalizeNumericId(scopeId)
                : null;
        return fetchConversationMessagesRequest('/api/chat/sessions', {
            conversationId,
            sessionType: resolvedSessionType,
            scopeId: skillId,
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
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const skillId =
            resolvedSessionType === SESSION_TYPE || resolvedSessionType === PUBLISHED_SESSION_TYPE
                ? scopeId == null
                    ? normalizeNumericId(selectedKnowledge)
                    : normalizeNumericId(scopeId)
                : null;
        return deleteConversationRequest('/api/chat/sessions', {
            conversationId,
            sessionType: resolvedSessionType,
            scopeId: skillId,
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
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const skillId =
            resolvedSessionType === SESSION_TYPE || resolvedSessionType === PUBLISHED_SESSION_TYPE
                ? scopeId == null
                    ? normalizeNumericId(selectedKnowledge)
                    : normalizeNumericId(scopeId)
                : null;
        return renameConversationRequest('/api/chat/sessions', {
            conversationId,
            name,
            sessionType: resolvedSessionType,
            scopeId: skillId,
            auth: true,
            onUnauthorized,
        });
    },

    parseEventPayload: parseSseEventPayload,
};
