import { normalizeNullableNumber, normalizeNullableString } from '@/utils/normalize';

export class AgentTemplateBean {
    constructor(source = {}) {
        this.id = null;
        this.agentCode = null;
        this.agentName = null;
        this.description = null;
        this.openingMessage = null;
        this.icon = null;
        this.displayName = null;
        this.avatarObjectName = null;
        this.avatarUrl = null;
        this.skills = [];

        this.assign(source);
    }

    assign(source = {}) {
        this.id = normalizeNullableNumber(source.id);
        this.agentCode = normalizeNullableString(source.agentCode);
        this.agentName = normalizeNullableString(source.agentName);
        this.description = normalizeNullableString(source.description);
        this.openingMessage = normalizeNullableString(source.openingMessage);
        this.icon = normalizeNullableString(source.icon);
        this.displayName = normalizeNullableString(source.displayName);
        this.avatarObjectName = normalizeNullableString(source.avatarObjectName);
        this.avatarUrl = normalizeNullableString(source.avatarUrl);
        this.skills = Array.isArray(source.skills) ? source.skills : [];
        return this;
    }

    clone() {
        return new AgentTemplateBean(this);
    }

    static from(source = {}) {
        return new AgentTemplateBean(source);
    }

    static fromApi(source = {}) {
        return new AgentTemplateBean(source);
    }

    static empty() {
        return new AgentTemplateBean();
    }
}
