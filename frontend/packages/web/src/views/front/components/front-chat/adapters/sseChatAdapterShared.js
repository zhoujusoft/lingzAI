import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';

export function createRequestOptions(options = {}, onUnauthorized) {
    return {
        ...options,
        onUnauthorized,
    };
}

export function createUploadProgressHandler(file, onProgress) {
    if (typeof onProgress !== 'function') {
        return undefined;
    }
    return event => {
        const total = Number(event?.total || file?.size || 0);
        const loaded = Number(event?.loaded || 0);
        onProgress(total > 0 ? (loaded / total) * 100 : 0);
    };
}

export function normalizeUploadedFileResponse(data, fallbackFile) {
    const descriptor = data?.file || {};
    return {
        id: data?.id || descriptor.id || '',
        name: data?.name || descriptor.fileName || fallbackFile?.name || '未命名文件',
        size: data?.size || descriptor.size || fallbackFile?.size || 0,
        contentType: descriptor.contentType || fallbackFile?.type || '',
        objectName: descriptor.objectName || '',
        path: descriptor.path || '',
        downloadUrl: descriptor.downloadUrl || '',
        descriptor,
    };
}

export function buildRuntimeChatBody({
    message,
    fileIds,
    sessionId,
    messageType,
    eventPayload,
    systemPromptAppend,
    options,
    mentionedSkillId,
    chatModelId,
    extraBody = {},
} = {}) {
    return {
        ...extraBody,
        message,
        fileIds: Array.isArray(fileIds) ? fileIds : [],
        sessionId,
        messageType: messageType || '',
        eventPayload: eventPayload || null,
        systemPromptAppend: systemPromptAppend || null,
        options: options || null,
        mentionedSkillId: mentionedSkillId ?? null,
        chatModelId: chatModelId ?? null,
    };
}

export function buildConversationQuery({ sessionType, scopeId, pageNo, pageSize, limit } = {}) {
    const search = new URLSearchParams();
    if (sessionType) {
        search.set('sessionType', String(sessionType).trim());
    }
    if (scopeId != null && String(scopeId).trim() !== '') {
        search.set('scopeId', String(scopeId).trim());
    }
    if (limit != null) {
        search.set('limit', String(limit));
    }
    if (pageNo != null) {
        search.set('pageNo', String(pageNo));
    }
    if (pageSize != null) {
        search.set('pageSize', String(pageSize));
    }
    const query = search.toString();
    return query ? `?${query}` : '';
}

export function appendQuery(path, query) {
    return query ? `${path}${query}` : path;
}

export async function sendRuntimeSseRequest(
    path,
    { auth = true, headers, onUnauthorized, body, signal } = {}
) {
    return doRequestRaw(
        path,
        createRequestOptions(
            {
                method: 'POST',
                responseType: 'stream',
                auth,
                headers,
                body,
                signal,
            },
            onUnauthorized
        )
    );
}

export async function fetchConversationListRequest(
    path,
    {
        auth = true,
        headers,
        onUnauthorized,
        sessionType,
        scopeId,
        limit,
        pageNo = 1,
        pageSize = 20,
    } = {}
) {
    const { data } = await doRequestJson(
        appendQuery(
            path,
            buildConversationQuery({
                sessionType,
                scopeId,
                limit,
                pageNo,
                pageSize,
            })
        ),
        createRequestOptions(
            {
                method: 'GET',
                auth,
                headers,
            },
            onUnauthorized
        )
    );
    return {
        data: {
            items: Array.isArray(data?.items) ? data.items : [],
            pageNo: Number(data?.pageNo || pageNo || 1),
            pageSize: Number(data?.pageSize || pageSize || 20),
            total: Number(data?.total || 0),
            hasMore: Boolean(data?.hasMore),
            nextPageNo: data?.nextPageNo == null ? null : Number(data.nextPageNo),
        },
    };
}

export async function fetchConversationMessagesRequest(
    path,
    {
        conversationId,
        auth = true,
        headers,
        onUnauthorized,
        sessionType,
        scopeId,
        pageNo = 1,
        pageSize = 100,
    } = {}
) {
    const encoded = encodeURIComponent(String(conversationId || '').trim());
    if (!encoded) {
        return { data: { items: [] } };
    }
    const { data } = await doRequestJson(
        appendQuery(
            `${path}/${encoded}/messages`,
            buildConversationQuery({
                sessionType,
                scopeId,
                pageNo,
                pageSize,
            })
        ),
        createRequestOptions(
            {
                method: 'GET',
                auth,
                headers,
            },
            onUnauthorized
        )
    );
    return {
        data: {
            items: Array.isArray(data?.items) ? data.items : [],
            pageNo: Number(data?.pageNo || pageNo || 1),
            pageSize: Number(data?.pageSize || pageSize || 100),
            total: Number(data?.total || 0),
            hasMore: Boolean(data?.hasMore),
            nextPageNo: data?.nextPageNo == null ? null : Number(data.nextPageNo),
        },
    };
}

export async function deleteConversationRequest(
    path,
    { conversationId, auth = true, headers, onUnauthorized, sessionType, scopeId } = {}
) {
    const encoded = encodeURIComponent(String(conversationId || '').trim());
    if (!encoded) {
        return { data: { success: true, alreadyDeleted: true } };
    }
    const { data } = await doRequestJson(
        appendQuery(
            `${path}/${encoded}`,
            buildConversationQuery({
                sessionType,
                scopeId,
            })
        ),
        createRequestOptions(
            {
                method: 'DELETE',
                auth,
                headers,
            },
            onUnauthorized
        )
    );
    return { data };
}

export async function renameConversationRequest(
    path,
    { conversationId, name, auth = true, headers, onUnauthorized, sessionType, scopeId } = {}
) {
    const encoded = encodeURIComponent(String(conversationId || '').trim());
    if (!encoded) {
        return { data: { success: false } };
    }
    const { data } = await doRequestJson(
        appendQuery(
            `${path}/${encoded}/name`,
            buildConversationQuery({
                sessionType,
                scopeId,
            })
        ),
        createRequestOptions(
            {
                method: 'PUT',
                auth,
                headers,
                body: {
                    name,
                },
            },
            onUnauthorized
        )
    );
    return { data };
}

export function parseSseEventPayload(data) {
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

    if (typeof data === 'string') {
        try {
            const parsed = JSON.parse(data);
            if (parsed && typeof parsed === 'object' && parsed.eventName) {
                return {
                    type: String(parsed.type || parsed.eventName || 'message').trim(),
                    content: parsed.content ?? parsed.payload ?? parsed,
                };
            }
        } catch (error) {
            // fall through
        }
    }

    return { type: 'message', content: data };
}

export function normalizeNumericId(value) {
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
