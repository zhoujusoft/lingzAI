import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function getUserAgentFiles(userId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/files/${userId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getUserAgentTemplate(userId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/template/${userId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data || null;
}

export async function listAvailableUserAgentTemplates(onUnauthorized) {
    const { data } = await doRequestJson('/api/admin/user-agent-config/templates', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function updateUserAgentTemplate(userId, agentId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/template/${userId}`, {
        method: 'PUT',
        body: { agentId },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getUserAgentFile(userId, filename, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/admin/user-agent-config/file/${userId}/${filename}`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
        }
    );
    return data || null;
}

export async function updateUserAgentFile(userId, filename, content, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/admin/user-agent-config/file/${userId}/${filename}`,
        {
            method: 'PUT',
            body: { content },
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function updateUserProfile(userId, profileContent, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/profile/${userId}`, {
        method: 'PUT',
        body: { profileContent },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getUserAgentSkills(userId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/skills/${userId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getUserAgentSkillPreference(userId, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/admin/user-agent-config/skills/preference/${userId}`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
        }
    );
    return data || { permittedSkills: [], enabledSkillIds: [], configured: false };
}

export async function updateUserAgentSkillPreference(userId, enabledSkillIds, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/admin/user-agent-config/skills/preference/${userId}`,
        {
            method: 'PUT',
            body: { enabledSkillIds },
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function addUserAgentSkill(userId, skillId, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/admin/user-agent-config/skill/${userId}/${skillId}`,
        {
            method: 'POST',
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function removeUserAgentSkill(userId, skillId, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/admin/user-agent-config/skill/${userId}/${skillId}`,
        {
            method: 'DELETE',
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function initUserAgentConfig(userId, roleId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/init/${userId}`, {
        method: 'POST',
        body: roleId ? { roleId } : {},
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function syncUserAgentConfig(userId, roleId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/admin/user-agent-config/sync/${userId}`, {
        method: 'POST',
        body: roleId ? { roleId } : {},
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getCurrentUserAgentTemplate(onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/template', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data || null;
}

export async function listCurrentUserAgentTemplates(onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/templates', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function updateCurrentUserAgentTemplate(agentId, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/template', {
        method: 'PUT',
        body: { agentId },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateCurrentUserAgentProfile(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/assistant', {
        method: 'PUT',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function uploadCurrentUserAgentAvatar(file, onUnauthorized) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await doRequestJson('/api/user/agent-config/assistant/avatar/upload', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data || null;
}

export async function getCurrentUserAgentFile(filename, onUnauthorized) {
    const { data } = await doRequestJson(`/api/user/agent-config/file/${filename}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data || null;
}

export async function updateCurrentUserAgentFile(filename, content, onUnauthorized) {
    const { data } = await doRequestJson(`/api/user/agent-config/file/${filename}`, {
        method: 'PUT',
        body: { content },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateCurrentUserProfile(profileContent, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/profile', {
        method: 'PUT',
        body: { profileContent },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getCurrentUserAgentSkills(onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/skills', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getCurrentUserAgentSkillDetails(onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/skills/detail', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getCurrentUserAgentSkillPreference(onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/skills/preference', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data || { permittedSkills: [], enabledSkillIds: [], configured: false };
}

export async function updateCurrentUserAgentSkillPreference(enabledSkillIds, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/skills/preference', {
        method: 'PUT',
        body: { enabledSkillIds },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function addCurrentUserAgentSkill(skillId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/user/agent-config/skill/${skillId}`, {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function removeCurrentUserAgentSkill(skillId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/user/agent-config/skill/${skillId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function syncCurrentUserAgentConfig(onUnauthorized) {
    const { data } = await doRequestJson('/api/user/agent-config/sync', {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}
