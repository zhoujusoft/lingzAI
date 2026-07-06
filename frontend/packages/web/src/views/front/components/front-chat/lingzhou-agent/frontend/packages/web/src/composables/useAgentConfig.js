import { reactive } from 'vue';
import {
    getCurrentUserAgentTemplate,
    getCurrentUserAgentFile,
    getCurrentUserAgentSkillDetails,
} from '@/api/user-agent-config';

/**
 * Agent 配置状态（全局单例）
 */
export const agentConfigState = reactive({
    /** @type {import('@/model/bean').AgentTemplateBean | null} */
    template: null,
    /** @type {string} */
    profile: '',
    /** @type {string} */
    soul: '',
    /** @type {Array<import('@/api/user-agent-config').SkillSimpleDto>} */
    skills: [],
    /** @type {boolean} */
    loading: false,
    /** @type {boolean} */
    initialized: false,
});

let fetchConfigPromise = null;

/**
 * 重置 Agent 配置状态
 */
export function resetAgentConfig() {
    agentConfigState.template = null;
    agentConfigState.profile = '';
    agentConfigState.soul = '';
    agentConfigState.skills = [];
    agentConfigState.loading = false;
    agentConfigState.initialized = false;
    fetchConfigPromise = null;
}

/**
 * 获取 Agent 配置（首次调用会触发加载）
 * @param {Object} options
 * @param {Function} [options.onUnauthorized] - 未授权回调
 * @returns {Promise<typeof agentConfigState>}
 */
export async function fetchAgentConfig(options = {}) {
    const { onUnauthorized } = options;

    if (fetchConfigPromise) {
        return fetchConfigPromise;
    }

    agentConfigState.loading = true;

    fetchConfigPromise = (async () => {
        try {
            const [template, profileFile, soulFile, skills] = await Promise.all([
                getCurrentUserAgentTemplate(onUnauthorized).catch(() => null),
                getCurrentUserAgentFile('PROFILE.md', onUnauthorized).catch(() => null),
                getCurrentUserAgentFile('SOUL.md', onUnauthorized).catch(() => null),
                getCurrentUserAgentSkillDetails(onUnauthorized).catch(() => []),
            ]);

            agentConfigState.template = template;
            agentConfigState.profile = profileFile?.content || '';
            agentConfigState.soul = soulFile?.content || '';
            agentConfigState.skills = Array.isArray(skills) ? skills : [];
            agentConfigState.initialized = true;

            return agentConfigState;
        } finally {
            agentConfigState.loading = false;
            fetchConfigPromise = null;
        }
    })();

    return fetchConfigPromise;
}

/**
 * 确保配置已加载（幂等）
 * @param {Object} options
 * @param {boolean} [options.force=false] - 强制重新加载
 * @param {Function} [options.onUnauthorized] - 未授权回调
 * @returns {Promise<typeof agentConfigState>}
 */
export async function ensureAgentConfigLoaded(options = {}) {
    const { force = false, onUnauthorized } = options;

    if (!force && agentConfigState.initialized) {
        return agentConfigState;
    }

    return fetchAgentConfig({ onUnauthorized });
}

/**
 * 获取 Agent 显示名称
 * @returns {string}
 */
export function getAgentDisplayName() {
    return agentConfigState.template?.agentName || 'AI 助手';
}

/**
 * 获取 Agent 图标
 * @returns {string}
 */
export function getAgentIcon() {
    return agentConfigState.template?.icon || 'smart_toy';
}

/**
 * 检查是否有可用技能
 * @returns {boolean}
 */
export function hasAgentSkills() {
    return agentConfigState.skills.length > 0;
}
