/**
 * 知识库文档 API 服务
 *
 * 接口文档：
 * - 上传文档：POST /datasets/document
 * - 查询进度：GET /datasets/document/getProgress/{docId}
 * - 重试解析：POST /datasets/document/{docId}/retry
 */

import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';

/**
 * 上传文档到知识库
 * @param {Object} params
 * @param {number} params.kbId - 知识库 ID
 * @param {File} params.file - 要上传的文件
 * @param {string} [params.chunkStrategy] - 切片策略：AUTO/DELIMITER_WINDOW/HEADING_DIRECTORY
 * @param {Object} [params.chunkConfig] - 切片配置
 * @param {Function} [params.onUnauthorized] - 未授权回调
 * @returns {Promise<{docId: number, status: number, message: string}>}
 */
export async function uploadDocument({
    kbId,
    parentId,
    file,
    chunkStrategy = 'AUTO',
    chunkConfig,
    onUnauthorized,
}) {
    const formData = new FormData();
    formData.append('kbId', kbId);
    if (parentId != null) {
        formData.append('parentId', parentId);
    }
    formData.append('file', file);
    formData.append('chunkStrategy', chunkStrategy);

    if (chunkConfig) {
        formData.append('chunkConfig', JSON.stringify(chunkConfig));
    }

    const { data } = await doRequestJson('/api/datasets/document', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });

    return data;
}

/**
 * 查询文档处理进度
 * @param {number} docId - 文档 ID
 * @param {Function} [onUnauthorized] - 未授权回调
 * @returns {Promise<{
 *   docId: number,
 *   status: number,
 *   progress: number,
 *   stage: string,
 *   message: string,
 *   updatedAt: string
 * }>}
 */
export async function getDocumentProgress(docId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/getProgress/${docId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });

    return data;
}

/**
 * 重试解析失败的文档
 * @param {number} docId - 文档 ID
 * @param {Function} [onUnauthorized] - 未授权回调
 * @returns {Promise<{docId: number, status: number, message: string}>}
 */
export async function retryDocument(docId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/${docId}/retry`, {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });

    return data;
}

export async function listKnowledgeDocuments(params = {}, onUnauthorized) {
    const search = new URLSearchParams();
    if (params.kbId != null) {
        search.set('kbId', String(params.kbId));
    }
    if (params.status != null && params.status !== '') {
        search.set('status', String(params.status));
    }
    if (params.name) {
        search.set('name', params.name);
    }

    const query = search.toString();
    const { data } = await doRequestJson(
        `/api/datasets/document/queryList${query ? `?${query}` : ''}`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function getDocumentDetail(docId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/${docId}/detail`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function listDocumentChunks({ docId, pageNum = 1, pageSize = 10 }, onUnauthorized) {
    const search = new URLSearchParams();
    if (docId != null) {
        search.set('docId', String(docId));
    }
    search.set('pageNum', String(pageNum));
    search.set('pageSize', String(pageSize));

    const query = search.toString();
    const { data } = await doRequestJson(`/api/datasets/chunk/list?${query}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function appendDocumentChunk(
    { docId, chunkContent, chunkType, headings, keywords },
    onUnauthorized
) {
    const { data } = await doRequestJson(`/api/datasets/document/${docId}/chunks`, {
        method: 'POST',
        body: {
            chunkContent,
            chunkType,
            headings,
            keywords,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateDocumentChunk(
    { kbId, chunkId, chunkContent, chunkType, headings, keywords },
    onUnauthorized
) {
    const { data } = await doRequestJson(`/api/datasets/chunk/${kbId}`, {
        method: 'PUT',
        body: {
            chunkId,
            chunkContent,
            chunkType,
            headings,
            keywords,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function deleteDocument(kbId, docId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/${kbId}/${docId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function listKnowledgeTree(kbId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/tree/${kbId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function listKnowledgeChildren({ kbId, parentId }, onUnauthorized) {
    const search = new URLSearchParams();
    if (parentId != null) {
        search.set('parentId', String(parentId));
    }
    const query = search.toString();
    const { data } = await doRequestJson(
        `/api/datasets/document/children/${kbId}${query ? `?${query}` : ''}`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function createKnowledgeFolder({ kbId, parentId, name }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/folder/${kbId}`, {
        method: 'POST',
        body: {
            parentId,
            name,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function renameKnowledgeFolder({ kbId, docId, name }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/folder/${kbId}/${docId}`, {
        method: 'PUT',
        body: { name },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function deleteKnowledgeFolder({ kbId, docId }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/folder/${kbId}/${docId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function moveKnowledgeNode({ kbId, docId, targetParentId }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/move/${kbId}/${docId}`, {
        method: 'PUT',
        body: {
            targetParentId,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function previewDocumentChunks({ docId, chunkStrategy, chunkConfig }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/${docId}/chunk-preview`, {
        method: 'POST',
        body: {
            chunkStrategy,
            chunkConfig: chunkConfig ? JSON.stringify(chunkConfig) : null,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function processDocument({ docId, chunkStrategy, chunkConfig }, onUnauthorized) {
    const { data } = await doRequestJson(`/api/datasets/document/${docId}/process`, {
        method: 'POST',
        body: {
            chunkStrategy,
            chunkConfig: chunkConfig ? JSON.stringify(chunkConfig) : null,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

/**
 * 文档状态枚举
 */
export const DocumentStatus = {
    PENDING: 0, // 待处理
    PROCESSING: 1, // 处理中
    COMPLETED: 2, // 已处理
    FAILED: 3, // 失败
};

/**
 * 进度阶段枚举
 */
export const ProgressStage = {
    PARSING: 'PARSING', // 解析中 (0-30%)
    CHUNKING: 'CHUNKING', // 分块中 (30-70%)
    EMBEDDING: 'EMBEDDING', // 向量化中 (70-90%)
    INDEXING: 'INDEXING', // 入库中 (90-100%)
    COMPLETED: 'COMPLETED', // 完成 (100%)
    FAILED: 'FAILED', // 失败
};

/**
 * 获取进度阶段的中文描述
 * @param {string} stage - 阶段英文
 * @returns {string} 中文描述
 */
export function getStageLabel(stage) {
    const stageMap = {
        [ProgressStage.PARSING]: '解析中',
        [ProgressStage.CHUNKING]: '分块中',
        [ProgressStage.EMBEDDING]: '向量化中',
        [ProgressStage.INDEXING]: '入库中',
        [ProgressStage.COMPLETED]: '处理完成',
        [ProgressStage.FAILED]: '处理失败',
    };
    return stageMap[stage] || stage;
}
