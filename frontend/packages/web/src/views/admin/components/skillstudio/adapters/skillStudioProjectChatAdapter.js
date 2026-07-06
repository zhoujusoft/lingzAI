import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';
import {
    buildRuntimeChatBody,
    createRequestOptions,
    deleteConversationRequest,
    fetchConversationListRequest,
    fetchConversationMessagesRequest,
    parseSseEventPayload,
    renameConversationRequest,
    sendRuntimeSseRequest,
} from '@/views/front/components/front-chat/adapters/sseChatAdapterShared';

const PREVIEW_SESSION_TYPE = 'SKILL_STUDIO_PROJECT_PREVIEW_CHAT';

function normalizeItems(data) {
    return Array.isArray(data) ? data : [];
}

export function createSkillStudioProjectChatAdapter(projectId) {
    const normalizedProjectId = Number(projectId);
    return {
        sessionStorageKey: `skill-studio-project-chat:${normalizedProjectId}`,

        async sendStream({
            message,
            sessionId,
            eventPayload,
            systemPromptAppend,
            options,
            onUnauthorized,
        }) {
            return doRequestRaw(
                `/api/skillstudio/projects/${normalizedProjectId}/chat/stream`,
                createRequestOptions(
                    {
                        method: 'POST',
                        responseType: 'stream',
                        auth: true,
                        body: {
                            sessionId: sessionId || null,
                            message,
                            eventPayload: eventPayload || null,
                            systemPromptAppend: systemPromptAppend || null,
                            options: {
                                preferMinimalChange: true,
                                allowCreateReferences: false,
                                previewOnly: false,
                                editMode: true,
                                ...(options || {}),
                            },
                        },
                    },
                    onUnauthorized
                )
            );
        },

        async fetchConversationList({ onUnauthorized } = {}) {
            const { data } = await doRequestJson(
                `/api/skillstudio/projects/${normalizedProjectId}/sessions?limit=50`,
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
                    items: normalizeItems(data),
                },
            };
        },

        async fetchMessages({ conversationId, pageNo = 1, pageSize = 200, onUnauthorized } = {}) {
            const encoded = encodeURIComponent(String(conversationId || '').trim());
            if (!encoded) {
                return { data: { items: [] } };
            }
            const { data } = await doRequestJson(
                `/api/skillstudio/projects/${normalizedProjectId}/sessions/${encoded}/messages?pageNo=${pageNo}&pageSize=${pageSize}`,
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
                    items: normalizeItems(data),
                },
            };
        },

        parseEventPayload: parseSseEventPayload,
    };
}

export function createSkillStudioProjectPreviewChatAdapter(projectId) {
    const normalizedProjectId = Number(projectId);
    return {
        sessionStorageKey: `skill-studio-project-preview-chat:${normalizedProjectId}`,

        async sendStream({
            message,
            fileIds,
            sessionId,
            messageType,
            eventPayload,
            systemPromptAppend,
            options,
            onUnauthorized,
        }) {
            return sendRuntimeSseRequest(
                `/api/skillstudio/projects/${normalizedProjectId}/preview/run/stream`,
                {
                    auth: true,
                    onUnauthorized,
                    body: buildRuntimeChatBody({
                        message,
                        fileIds,
                        sessionId,
                        messageType,
                        eventPayload,
                        systemPromptAppend,
                        options: {
                            previewRun: true,
                            ...(options || {}),
                        },
                    }),
                }
            );
        },

        async uploadFile({ file, sessionId, onUnauthorized }) {
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
            return fetchConversationListRequest('/api/chat/sessions', {
                auth: true,
                onUnauthorized,
                sessionType: PREVIEW_SESSION_TYPE,
                scopeId: normalizedProjectId,
                limit: 50,
            });
        },

        async fetchMessages({ conversationId, pageNo = 1, pageSize = 200, onUnauthorized } = {}) {
            return fetchConversationMessagesRequest('/api/chat/sessions', {
                conversationId,
                sessionType: PREVIEW_SESSION_TYPE,
                scopeId: normalizedProjectId,
                pageNo,
                pageSize,
                auth: true,
                onUnauthorized,
            });
        },

        async deleteConversation({ conversationId, onUnauthorized } = {}) {
            return deleteConversationRequest('/api/chat/sessions', {
                conversationId,
                sessionType: PREVIEW_SESSION_TYPE,
                scopeId: normalizedProjectId,
                auth: true,
                onUnauthorized,
            });
        },

        async renameConversation({ conversationId, name, onUnauthorized } = {}) {
            return renameConversationRequest('/api/chat/sessions', {
                conversationId,
                name,
                sessionType: PREVIEW_SESSION_TYPE,
                scopeId: normalizedProjectId,
                auth: true,
                onUnauthorized,
            });
        },

        parseEventPayload: parseSseEventPayload,
    };
}
