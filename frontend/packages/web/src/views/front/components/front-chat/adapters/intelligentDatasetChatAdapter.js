import { DATASET_CHAT_SESSION_STORAGE_KEY } from '@/model/session';
import { listIntegrationDatasets } from '@/api/integration';
import {
    buildRuntimeChatBody,
    sendRuntimeSseRequest,
    fetchConversationListRequest,
    fetchConversationMessagesRequest,
    deleteConversationRequest,
    parseSseEventPayload,
} from './sseChatAdapterShared';

let cachedDatasetIds = null;
let cachedDatasetAt = 0;

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

function isPublishedDataset(item) {
    return (
        String(item?.publishStatus || '')
            .trim()
            .toUpperCase() === 'PUBLISHED'
    );
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
        if (!isPublishedDataset(item)) {
            return;
        }
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
    continuationSessionTypes: [SESSION_TYPE],

    async sendStream({
        message,
        fileIds: _fileIds,
        sessionId,
        selectedKnowledge,
        messageType,
        eventPayload,
        systemPromptAppend,
        options,
        onUnauthorized,
    }) {
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            throw new Error('未找到可用数据集或当前数据集选择无效，请重新选择。');
        }
        return sendRuntimeSseRequest(`/api/integration/datasets/${datasetId}/chat/stream`, {
            auth: true,
            onUnauthorized,
            body: buildRuntimeChatBody({
                message,
                fileIds: [],
                sessionId,
                messageType,
                eventPayload,
                systemPromptAppend,
                options,
            }),
        });
    },

    async fetchConversationList({
        selectedKnowledge,
        pageNo = 1,
        pageSize = 20,
        onUnauthorized,
    } = {}) {
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            return { data: { items: [] } };
        }
        return fetchConversationListRequest('/api/chat/sessions', {
            auth: true,
            onUnauthorized,
            sessionType: SESSION_TYPE,
            scopeId: datasetId,
            pageNo,
            pageSize,
        });
    },

    async fetchMessages({
        conversationId,
        selectedKnowledge,
        pageNo = 1,
        pageSize = 100,
        onUnauthorized,
    } = {}) {
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            return { data: { items: [] } };
        }
        return fetchConversationMessagesRequest('/api/chat/sessions', {
            conversationId,
            sessionType: SESSION_TYPE,
            scopeId: datasetId,
            pageNo,
            pageSize,
            auth: true,
            onUnauthorized,
        });
    },

    async deleteConversation({ conversationId, selectedKnowledge, onUnauthorized } = {}) {
        const datasetId = await resolveDatasetIdBySelection(selectedKnowledge, onUnauthorized);
        if (datasetId == null) {
            return { data: { success: true, alreadyDeleted: true } };
        }
        return deleteConversationRequest('/api/chat/sessions', {
            conversationId,
            sessionType: SESSION_TYPE,
            scopeId: datasetId,
            auth: true,
            onUnauthorized,
        });
    },

    parseEventPayload: parseSseEventPayload,
};
