import { normalizeNullableString } from '@/utils/normalize';

function normalizeActive(value) {
    return value === true || value === 1 || value === '1';
}

export class ChatConversationBean {
    constructor(source = {}) {
        this.id = null;
        this.name = null;
        this.title = null;
        this.active = false;
        this.updatedAt = null;
        this.lastMessage = null;
        this.sessionType = null;
        this.scopeId = null;
        this.scopeDisplayName = null;

        this.assign(source);
    }

    assign(source = {}) {
        this.id = normalizeNullableString(source.id);
        this.name = normalizeNullableString(source.name);
        this.title = normalizeNullableString(source.title);
        this.active = normalizeActive(source.active);
        this.updatedAt = normalizeNullableString(source.updatedAt);
        this.lastMessage = normalizeNullableString(source.lastMessage);
        this.sessionType = normalizeNullableString(source.sessionType);
        this.scopeId = source.scopeId ?? null;
        this.scopeDisplayName = normalizeNullableString(source.scopeDisplayName);
        return this;
    }

    clone() {
        return new ChatConversationBean(this);
    }

    toApiObject() {
        return {
            id: this.id,
            name: this.name,
            title: this.title,
            active: this.active,
            updatedAt: this.updatedAt,
            lastMessage: this.lastMessage,
            sessionType: this.sessionType,
            scopeId: this.scopeId,
            scopeDisplayName: this.scopeDisplayName,
        };
    }

    static from(source = {}) {
        return new ChatConversationBean(source);
    }

    static fromApi(source = {}) {
        return new ChatConversationBean(source);
    }

    static empty() {
        return new ChatConversationBean();
    }
}
