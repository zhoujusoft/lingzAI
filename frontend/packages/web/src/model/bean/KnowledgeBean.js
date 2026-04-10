import { normalizeNullableNumber, normalizeNullableString } from '@/utils/normalize';

function normalizeCount(value) {
    if (value == null || value === '') {
        return null;
    }
    if (typeof value === 'number') {
        return Number.isFinite(value) ? value : null;
    }
    const text = String(value);
    const matched = text.match(/-?\d+(\.\d+)?/);
    if (!matched) {
        return null;
    }
    const parsed = Number(matched[0]);
    return Number.isFinite(parsed) ? parsed : null;
}

export class KnowledgeBean {
    constructor(source = {}) {
        this.id = null;
        this.kbCode = null;
        this.name = null;
        this.description = null;
        this.docCount = null;
        this.charCount = null;
        this.appCount = null;
        this.state = null;
        this.publishStatus = null;
        this.publishedVersion = null;
        this.lastPublishMessage = null;
        this.publishedAt = null;
        this.lastCompiledAt = null;
        this.createdAt = null;
        this.updatedAt = null;

        this.assign(source);
    }

    assign(source = {}) {
        this.id = normalizeNullableNumber(source.id ?? source.kbId);
        this.kbCode = normalizeNullableString(source.kbCode);
        this.name = normalizeNullableString(source.name ?? source.kbName);
        this.description = normalizeNullableString(source.description);
        this.docCount = normalizeCount(source.docCount);
        this.charCount = normalizeCount(source.charCount);
        this.appCount = normalizeCount(source.appCount);
        this.state = normalizeNullableNumber(source.state);
        this.publishStatus = normalizeNullableString(source.publishStatus);
        this.publishedVersion = normalizeCount(source.publishedVersion);
        this.lastPublishMessage = normalizeNullableString(source.lastPublishMessage);
        this.publishedAt = normalizeNullableString(source.publishedAt);
        this.lastCompiledAt = normalizeNullableString(source.lastCompiledAt);
        this.createdAt = normalizeNullableString(source.createdAt ?? source.uploadTime);
        this.updatedAt = normalizeNullableString(source.updatedAt);
        return this;
    }

    clone() {
        return new KnowledgeBean(this);
    }

    toApiObject() {
        return {
            id: this.id,
            kbCode: this.kbCode,
            name: this.name,
            description: this.description,
            docCount: this.docCount,
            charCount: this.charCount,
            appCount: this.appCount,
            state: this.state,
            publishStatus: this.publishStatus,
            publishedVersion: this.publishedVersion,
            lastPublishMessage: this.lastPublishMessage,
            publishedAt: this.publishedAt,
            lastCompiledAt: this.lastCompiledAt,
            createdAt: this.createdAt,
            updatedAt: this.updatedAt,
        };
    }

    static from(source = {}) {
        return new KnowledgeBean(source);
    }

    static fromApi(source = {}) {
        return new KnowledgeBean(source);
    }

    static empty() {
        return new KnowledgeBean();
    }
}
