import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';
import { DATASET_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
import { listIntegrationDatasets } from '@/api/integration';

let cachedDatasetIds = null;
let cachedDatasetAt = 0;

function createRequestOptions(options = {}, onUnauthorized) {
    return {
        ...options,
        onUnauthorized,
    };
}

function normalizeDatasetId(value) {
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

async function loadDatasetIds(onUnauthorized) {
    const now = Date.now();
    if (cachedDatasetIds && now - cachedDatasetAt < 30_000) {
        return cachedDatasetIds;
    }
    const records = await listIntegrationDatasets({}, onUnauthorized);
    const dedup = new Set();
    const ids = [];
    (Array.isArray(records) ? records : []).forEach(item => {
        const id = normalizeDatasetId(item?.id);
        if (id != null && !dedup.has(id)) {
            dedup.add(id);
            ids.push(id);
        }
    });
    cachedDatasetIds = ids;
    cachedDatasetAt = now;
    return ids;
}

async function resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized) {
    const directId = normalizeDatasetId(selectedKnowledge);
    if (directId != null) {
        return directId;
    }
    const selectedText = String(selectedKnowledge || '').trim();
    if (selectedText) {
        return null;
    }
    const ids = await loadDatasetIds(onUnauthorized);
    return ids[0] ?? null;
}

const SESSION_TYPE = 'DATASET_CHAT';

export const intelligentDatasetChatAdapter = {
    sessionStorageKey: DATASET_CHAT_SESSION_STORAGE_KEY,

    async sendStream({ message, fileIds: _fileIds, sessionId, selectedKnowledge, onUnauthorized }) {
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            throw new Error('未找到可用数据集或当前数据集选择无效，请重新选择。');
        }
        return doRequestRaw(
            `/api/integration/datasets/${datasetId}/chat/stream`,
            createRequestOptions(
                {
                    method: 'POST',
                    responseType: 'stream',
                    auth: true,
                    body: {
                        message,
                        sessionId,
                    },
                },
                onUnauthorized
            )
        );
    },

    async fetchConversationList({ selectedKnowledge, onUnauthorized } = {}) {
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            return { data: { items: [] } };
        }
        const { data } = await doRequestJson(
            `/api/chat/sessions?sessionType=${SESSION_TYPE}&scopeId=${datasetId}&limit=50`,
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
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            return { data: { items: [] } };
        }
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}/messages?sessionType=${SESSION_TYPE}&scopeId=${datasetId}&pageNo=${pageNo}&pageSize=${pageSize}`,
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
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            return { data: { success: true, alreadyDeleted: true } };
        }
        const { data } = await doRequestJson(
            `/api/chat/sessions/${encoded}?sessionType=${SESSION_TYPE}&scopeId=${datasetId}`,
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
