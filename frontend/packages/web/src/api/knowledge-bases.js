import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function listKnowledgeBases(params = {}, onUnauthorized) {
    const search = new URLSearchParams();
    if (params.kbName) {
        search.set('kbName', params.kbName);
    }
    if (params.description) {
        search.set('description', params.description);
    }

    const query = search.toString();
    const { data } = await doRequestJson(`/api/datasets/base/list${query ? `?${query}` : ''}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function createKnowledgeBase(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/datasets/base', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function createKnowledgeBaseWithDocument(
    { kbName, description, file, chunkStrategy = 'AUTO', chunkConfig },
    onUnauthorized,
) {
    const formData = new FormData();
    formData.append('kbName', kbName);
    if (description) {
        formData.append('description', description);
    }
    formData.append('file', file);
    formData.append('chunkStrategy', chunkStrategy);
    if (chunkConfig) {
        formData.append('chunkConfig', JSON.stringify(chunkConfig));
    }

    const { data } = await doRequestJson('/api/datasets/base/upload', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function deleteKnowledgeBase(kbId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/base/${kbId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function recallKnowledgeBase({ kbId, query, topK = 5 }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/base/${kbId}/recall-test`, {
        method: 'POST',
        body: {
            query,
            topK,
        },
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getKnowledgeBasePublishStatus(kbId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/base/${kbId}/publish-status`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function publishKnowledgeBase(kbId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/base/${kbId}/publish`, {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function disableKnowledgeBase(kbId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/base/${kbId}/disable`, {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}
