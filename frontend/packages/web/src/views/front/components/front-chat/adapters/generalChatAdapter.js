import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';
import { GENERAL_CHAT_SESSION_STORAGE_KEY } from '@/model/session';

function createRequestOptions(options = {}, onUnauthorized) {
    return {
        ...options,
        onUnauthorized,
    };
}

const SESSION_TYPE = 'GENERAL_CHAT';

export const generalChatAdapter = {
    sessionStorageKey: GENERAL_CHAT_SESSION_STORAGE_KEY,

    async sendStream({ message, fileIds, sessionId, onUnauthorized }) {
        return doRequestRaw(
            '/api/chat',
            createRequestOptions(
                {
                    method: 'POST',
                    responseType: 'stream',
                    auth: true,
                    body: {
                        message,
                        fileIds,
                        sessionId,
                    },
                },
                onUnauthorized
            )
        );
    },

    async uploadFile({ file, onUnauthorized }) {
        const formData = new FormData();
        formData.append('file', file);

        const { data } = await doRequestJson(
            '/api/files/upload',
            createRequestOptions(
                {
                    method: 'POST',
                    body: formData,
                },
                onUnauthorized
            )
        );

        return {
            id: data.id,
            name: data.name || file.name,
            size: data.size || file.size,
        };
    },

    async fetchConversationList({ onUnauthorized } = {}) {
        const { data } = await doRequestJson(
            '/api/chat/sessions?limit=50',
            createRequestOptions(
                {
                    method: 'GET',
                    auth: true,
                },
                onUnauthorized
            )
        );

        return {
            data: {
                items: Array.isArray(data?.items) ? data.items : [],
            },
        };
    },

    async fetchMessages({
        conversationId,
        pageNo = 1,
        pageSize = 100,
        sessionType = SESSION_TYPE,
        scopeId = null,
        onUnauthorized,
    } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { items: [] } };
        }
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const scopeQuery = scopeId == null ? '' : `&scopeId=${encodeURIComponent(String(scopeId))}`;
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}/messages?sessionType=${encodeURIComponent(resolvedSessionType)}${scopeQuery}&pageNo=${pageNo}&pageSize=${pageSize}`,
            createRequestOptions(
                {
                    method: 'GET',
                    auth: true,
                },
                onUnauthorized
            )
        );

        return {
            data: {
                items: Array.isArray(data?.items) ? data.items : [],
            },
        };
    },

    async deleteConversation({ conversationId, sessionType = SESSION_TYPE, scopeId = null, onUnauthorized } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { success: true, alreadyDeleted: true } };
        }
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const scopeQuery = scopeId == null ? '' : `&scopeId=${encodeURIComponent(String(scopeId))}`;

        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}?sessionType=${encodeURIComponent(resolvedSessionType)}${scopeQuery}`,
            createRequestOptions(
                {
                    method: 'DELETE',
                    auth: true,
                },
                onUnauthorized
            )
        );

        return { data };
    },

    async renameConversation({
        conversationId,
        sessionType = SESSION_TYPE,
        scopeId = null,
        name,
        onUnauthorized,
    } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { success: false } };
        }
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const scopeQuery = scopeId == null ? '' : `&scopeId=${encodeURIComponent(String(scopeId))}`;

        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}/name?sessionType=${encodeURIComponent(resolvedSessionType)}${scopeQuery}`,
            createRequestOptions(
                {
                    method: 'PUT',
                    auth: true,
                    body: {
                        name,
                    },
                },
                onUnauthorized
            )
        );

        return { data };
    },

    parseEventPayload(data) {
        try {
            const parsed = JSON.parse(data);
            if (parsed && typeof parsed === 'object' && parsed.type) {
                return parsed;
            }
        } catch (error) {
            // fall through
        }

        if (data === '[DONE]') {
            return { type: 'done', content: '' };
        }

        return { type: 'message', content: data };
    },
};
