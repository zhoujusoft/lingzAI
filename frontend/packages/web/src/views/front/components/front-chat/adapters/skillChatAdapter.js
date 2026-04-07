import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';
import { SKILL_CHAT_SESSION_STORAGE_KEY } from '@/model/session';

function createRequestOptions(options = {}, onUnauthorized) {
    return {
        ...options,
        onUnauthorized,
    };
}

function normalizeSkillId(value) {
    if (value == null) {
        return null;
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
    }
    const text = String(value).trim();
    if (!text || !/^\d+$/.test(text)) {
        return null;
    }
    return Number(text);
}

const SESSION_TYPE = 'SKILL_CHAT';

export const skillChatAdapter = {
    sessionStorageKey: SKILL_CHAT_SESSION_STORAGE_KEY,

    async sendStream({ message, fileIds, sessionId, selectedKnowledge, onUnauthorized }) {
        const skillId = normalizeSkillId(selectedKnowledge);
        if (skillId == null) {
            throw new Error('未选择技能，请先从技能市场进入或在当前页面选择技能。');
        }
        return doRequestRaw(
            '/api/skills/chat',
            createRequestOptions(
                {
                    method: 'POST',
                    responseType: 'stream',
                    auth: true,
                    body: {
                        skillId,
                        message,
                        fileIds,
                        sessionId,
                    },
                },
                onUnauthorized,
            ),
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
                    auth: true,
                    body: formData,
                },
                onUnauthorized,
            ),
        );

        return {
            id: data.id,
            name: data.name || file.name,
            size: data.size || file.size,
        };
    },

    async fetchConversationList({ selectedKnowledge, onUnauthorized } = {}) {
        const { data } = await doRequestJson(
            '/api/chat/sessions?limit=50',
            createRequestOptions(
                {
                    method: 'GET',
                    auth: true,
                },
                onUnauthorized,
            ),
        );

        return {
            data: {
                items: Array.isArray(data?.items) ? data.items : [],
            },
        };
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
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { items: [] } };
        }
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const skillId =
            resolvedSessionType === SESSION_TYPE
                ? (scopeId == null ? normalizeSkillId(selectedKnowledge) : normalizeSkillId(scopeId))
                : null;
        const scopeQuery = skillId == null ? '' : `&scopeId=${encodeURIComponent(String(skillId))}`;
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}/messages?sessionType=${encodeURIComponent(resolvedSessionType)}${scopeQuery}&pageNo=${pageNo}&pageSize=${pageSize}`,
            createRequestOptions(
                {
                    method: 'GET',
                    auth: true,
                },
                onUnauthorized,
            ),
        );

        return {
            data: {
                items: Array.isArray(data?.items) ? data.items : [],
            },
        };
    },

    async deleteConversation({
        conversationId,
        selectedKnowledge,
        sessionType = SESSION_TYPE,
        scopeId,
        onUnauthorized,
    } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { success: true, alreadyDeleted: true } };
        }
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const skillId =
            resolvedSessionType === SESSION_TYPE
                ? (scopeId == null ? normalizeSkillId(selectedKnowledge) : normalizeSkillId(scopeId))
                : null;
        const scopeQuery = skillId == null ? '' : `&scopeId=${encodeURIComponent(String(skillId))}`;
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}?sessionType=${encodeURIComponent(resolvedSessionType)}${scopeQuery}`,
            createRequestOptions(
                {
                    method: 'DELETE',
                    auth: true,
                },
                onUnauthorized,
            ),
        );
        return { data };
    },

    async renameConversation({
        conversationId,
        selectedKnowledge,
        sessionType = SESSION_TYPE,
        scopeId,
        name,
        onUnauthorized,
    } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { success: false } };
        }
        const resolvedSessionType = String(sessionType || SESSION_TYPE).trim() || SESSION_TYPE;
        const skillId =
            resolvedSessionType === SESSION_TYPE
                ? (scopeId == null ? normalizeSkillId(selectedKnowledge) : normalizeSkillId(scopeId))
                : null;
        const scopeQuery = skillId == null ? '' : `&scopeId=${encodeURIComponent(String(skillId))}`;
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
                onUnauthorized,
            ),
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
