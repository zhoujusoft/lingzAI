import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';
import { INTELLIGENT_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
import { listKnowledgeBases } from '@/api/knowledge-bases';

let cachedKnowledgeIds = null;
let cachedKnowledgeAt = 0;

function createRequestOptions(options = {}, onUnauthorized) {
    return {
        ...options,
        onUnauthorized,
    };
}

function normalizeKnowledgeId(value) {
    if (value == null) {
        return null;
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
    }
    const text = String(value).trim();
    if (!text) {
        return null;
    }
    if (/^\d+$/.test(text)) {
        return Number(text);
    }
    return null;
}

async function loadKnowledgeIds(onUnauthorized) {
    const now = Date.now();
    if (cachedKnowledgeIds && now - cachedKnowledgeAt < 30_000) {
        return cachedKnowledgeIds;
    }
    const data = await listKnowledgeBases({}, onUnauthorized);
    const records = Array.isArray(data?.records) ? data.records : [];
    const dedup = new Set();
    const ids = [];
    records.forEach(item => {
        const id = normalizeKnowledgeId(item?.id ?? item?.kbId);
        if (id != null && !dedup.has(id)) {
            dedup.add(id);
            ids.push(id);
        }
    });
    cachedKnowledgeIds = ids;
    cachedKnowledgeAt = now;
    return ids;
}

async function resolveKbIdBySelection(selectedKnowledge, onUnauthorized) {
    const directId = normalizeKnowledgeId(selectedKnowledge);
    if (directId != null) {
        return directId;
    }
    const selectedText = String(selectedKnowledge || '').trim();
    if (selectedText) {
        return null;
    }
    const ids = await loadKnowledgeIds(onUnauthorized);
    return ids[0] ?? null;
}

const SESSION_TYPE = 'KNOWLEDGE_QA';

export const intelligentChatAdapter = {
    sessionStorageKey: INTELLIGENT_CHAT_SESSION_STORAGE_KEY,

    async sendStream({ message, fileIds: _fileIds, sessionId, selectedKnowledge, onUnauthorized }) {
        const kbId = await resolveKbIdBySelection(selectedKnowledge, onUnauthorized);
        if (kbId == null) {
            throw new Error('未找到可用知识库或当前知识库选择无效，请重新选择。');
        }
        return doRequestRaw(
            `/api/datasets/base/${kbId}/qa/stream`,
            createRequestOptions(
                {
                    method: 'POST',
                    responseType: 'stream',
                    auth: true,
                    body: {
                        message,
                        sessionId,
                        topK: 8,
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

    async fetchConversationList({ selectedKnowledge, onUnauthorized } = {}) {
        const kbId = await resolveKbIdBySelection(selectedKnowledge, onUnauthorized);
        if (kbId == null) {
            return { data: { items: [] } };
        }
        const { data } = await doRequestJson(
            `/api/chat/sessions?sessionType=${SESSION_TYPE}&scopeId=${kbId}&limit=50`,
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

    async fetchMessages({ conversationId, selectedKnowledge, pageNo = 1, pageSize = 100, onUnauthorized } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { items: [] } };
        }
        const kbId = await resolveKbIdBySelection(selectedKnowledge, onUnauthorized);
        if (kbId == null) {
            return { data: { items: [] } };
        }
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}/messages?sessionType=${SESSION_TYPE}&scopeId=${kbId}&pageNo=${pageNo}&pageSize=${pageSize}`,
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

    async deleteConversation({ conversationId, selectedKnowledge, onUnauthorized } = {}) {
        const encoded = encodeURIComponent(String(conversationId || '').trim());
        if (!encoded) {
            return { data: { success: true, alreadyDeleted: true } };
        }
        const kbId = await resolveKbIdBySelection(selectedKnowledge, onUnauthorized);
        if (kbId == null) {
            return { data: { success: true, alreadyDeleted: true } };
        }
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}?sessionType=${SESSION_TYPE}&scopeId=${kbId}`,
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
