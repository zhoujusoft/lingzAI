import { normalizeNullableNumber, normalizeNullableString } from '@/utils/normalize';

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
        this.sessionTypeLabel = null;
        this.scopeId = null;
        this.scopeDisplayName = null;
        this.sourceType = null;
        this.sourceLabel = null;
        this.sourceIcon = null;
        this.sourceIconColor = null;
        this.channelType = null;
        this.titleSummary = null;
        this.subtitle = null;
        this.chatModelId = null;
        this.chatModelDisplayName = null;
        this.chatModelAvailable = true;

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
        this.sessionTypeLabel = normalizeNullableString(source.sessionTypeLabel);
        this.scopeId = source.scopeId ?? null;
        this.scopeDisplayName = normalizeNullableString(source.scopeDisplayName);
        this.sourceType = normalizeNullableString(source.sourceType);
        this.sourceLabel = normalizeNullableString(source.sourceLabel);
        this.sourceIcon = normalizeNullableString(source.sourceIcon);
        this.sourceIconColor = normalizeNullableString(source.sourceIconColor);
        this.channelType = normalizeNullableString(source.channelType);
        this.titleSummary = normalizeNullableString(source.titleSummary);
        this.subtitle = normalizeNullableString(source.subtitle);
        this.chatModelId = normalizeNullableNumber(source.chatModelId);
        this.chatModelDisplayName = normalizeNullableString(source.chatModelDisplayName);
        this.chatModelAvailable = source.chatModelAvailable !== false;
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
            sessionTypeLabel: this.sessionTypeLabel,
            scopeId: this.scopeId,
            scopeDisplayName: this.scopeDisplayName,
            sourceType: this.sourceType,
            sourceLabel: this.sourceLabel,
            sourceIcon: this.sourceIcon,
            sourceIconColor: this.sourceIconColor,
            channelType: this.channelType,
            titleSummary: this.titleSummary,
            subtitle: this.subtitle,
            chatModelId: this.chatModelId,
            chatModelDisplayName: this.chatModelDisplayName,
            chatModelAvailable: this.chatModelAvailable,
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
