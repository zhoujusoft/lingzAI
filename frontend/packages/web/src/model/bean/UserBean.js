import { normalizeNullableNumber, normalizeNullableString } from '@/utils/normalize';
import { AgentTemplateBean } from './AgentTemplateBean';
import { normalizeMenuPermissions } from '@/model/admin-menu-permissions';

export class UserBean {
    constructor(source = {}) {
        this.id = null;
        this.name = null;
        this.code = null;
        this.userType = null;
        this.mobile = null;
        this.email = null;
        this.state = null;
        this.parentId = null;
        this.roleId = null;
        this.roleName = null;
        this.roleCode = null;
        this.avatarUrl = null;
        this.appVersion = null;
        this.roleAgent = null;
        this.tokenQuota = null;
        this.menuPermissions = [];
        this.permittedSkillIds = [];
        this.permittedToolIds = [];
        this.resourcePermissionUnrestricted = false;

        this.assign(source);
    }

    assign(source = {}) {
        this.id = normalizeNullableNumber(source.id);
        this.name = normalizeNullableString(source.name);
        this.code = normalizeNullableString(source.code);
        this.userType = normalizeNullableNumber(source.userType);
        this.mobile = normalizeNullableString(source.mobile);
        this.email = normalizeNullableString(source.email);
        this.state = normalizeNullableNumber(source.state);
        this.parentId = normalizeNullableString(source.parentId);
        this.roleId = normalizeNullableNumber(source.roleId);
        this.roleName = normalizeNullableString(source.roleName);
        this.roleCode = normalizeNullableString(source.roleCode);
        this.avatarUrl = normalizeNullableString(source.avatarUrl);
        this.appVersion = normalizeNullableString(source.appVersion);
        this.roleAgent = source.roleAgent ? AgentTemplateBean.fromApi(source.roleAgent) : null;
        this.tokenQuota = normalizeTokenQuota(source.tokenQuota);
        this.menuPermissions = normalizeMenuPermissions(source.menuPermissions);
        this.permittedSkillIds = Array.isArray(source.permittedSkillIds)
            ? source.permittedSkillIds.map(id => Number(id)).filter(id => Number.isFinite(id))
            : [];
        this.permittedToolIds = Array.isArray(source.permittedToolIds)
            ? source.permittedToolIds.map(id => Number(id)).filter(id => Number.isFinite(id))
            : [];
        this.resourcePermissionUnrestricted = Boolean(source.resourcePermissionUnrestricted);
        return this;
    }

    clone() {
        return new UserBean(this);
    }

    toApiObject() {
        return {
            id: this.id,
            name: this.name,
            code: this.code,
            userType: this.userType,
            mobile: this.mobile,
            email: this.email,
            state: this.state,
            parentId: this.parentId,
            roleId: this.roleId,
            roleName: this.roleName,
            roleCode: this.roleCode,
            avatarUrl: this.avatarUrl,
            appVersion: this.appVersion,
            roleAgent: this.roleAgent ? this.roleAgent.clone() : null,
            tokenQuota: this.tokenQuota ? { ...this.tokenQuota } : null,
            menuPermissions: [...this.menuPermissions],
            permittedSkillIds: [...this.permittedSkillIds],
            permittedToolIds: [...this.permittedToolIds],
            resourcePermissionUnrestricted: this.resourcePermissionUnrestricted,
        };
    }

    static from(source = {}) {
        return new UserBean(source);
    }

    static fromApi(source = {}) {
        return new UserBean(source);
    }

    static empty() {
        return new UserBean();
    }
}

function normalizeTokenQuota(source) {
    if (!source || typeof source !== 'object') {
        return null;
    }
    return {
        enabled: Boolean(source.enabled),
        unlimited: Boolean(source.unlimited),
        grantedTokens: normalizeNullableNumber(source.grantedTokens) ?? 0,
        consumedTokens: normalizeNullableNumber(source.consumedTokens) ?? 0,
        remainingTokens: normalizeNullableNumber(source.remainingTokens) ?? 0,
    };
}
