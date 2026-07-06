import { requestJson as doRequestJson } from '@lingzhou/core/http/request';
import {
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

function toChatbotHeaders(appCode, passport, includePassport = true) {
    const headers = {
        'X-App-Code': String(appCode || '').trim(),
    };
    if (includePassport) {
        headers['X-App-Passport'] = String(passport || '').trim();
    }
    return headers;
}

export function createChatbotSkillChatAdapter({ appCode, passport, sessionStorageKey }) {
    const normalizedAppCode = String(appCode || '').trim();
    const normalizedPassport = String(passport || '').trim();

    return {
        sessionStorageKey: sessionStorageKey || '',

        async sendStream({
            message,
            fileIds,
            sessionId,
            messageType,
            eventPayload,
            systemPromptAppend,
            options,
        }) {
            return sendRuntimeSseRequest('/api/chatbot/chat/stream', {
                auth: false,
                headers: toChatbotHeaders(normalizedAppCode, normalizedPassport),
                body: buildRuntimeChatBody({
                    message,
                    fileIds,
                    sessionId,
                    messageType,
                    eventPayload,
                    systemPromptAppend,
                    options,
                }),
            });
        },

        async uploadFile({ file, sessionId, onProgress }) {
            const formData = new FormData();
            formData.append('file', file);
            if (sessionId) {
                formData.append('sessionId', sessionId);
            }

            const { data } = await doRequestJson('/api/chatbot/files/upload', {
                method: 'POST',
                auth: false,
                headers: toChatbotHeaders(normalizedAppCode, normalizedPassport),
                body: formData,
                onUploadProgress: createUploadProgressHandler(file, onProgress),
            });

            return normalizeUploadedFileResponse(data, file);
        },

        async fetchConversationList({ pageNo = 1, pageSize = 20 } = {}) {
            return fetchConversationListRequest('/api/chatbot/sessions', {
                auth: false,
                headers: toChatbotHeaders(normalizedAppCode, normalizedPassport),
                pageNo,
                pageSize,
            });
        },

        async fetchMessages({ conversationId, pageNo = 1, pageSize = 100 } = {}) {
            return fetchConversationMessagesRequest('/api/chatbot/sessions', {
                conversationId,
                pageNo,
                pageSize,
                auth: false,
                headers: toChatbotHeaders(normalizedAppCode, normalizedPassport),
            });
        },

        async deleteConversation({ conversationId } = {}) {
            return deleteConversationRequest('/api/chatbot/sessions', {
                conversationId,
                auth: false,
                headers: toChatbotHeaders(normalizedAppCode, normalizedPassport),
            });
        },

        async renameConversation({ conversationId, name } = {}) {
            return renameConversationRequest('/api/chatbot/sessions', {
                conversationId,
                name,
                auth: false,
                headers: toChatbotHeaders(normalizedAppCode, normalizedPassport),
            });
        },

        parseEventPayload: parseSseEventPayload,
    };
}
