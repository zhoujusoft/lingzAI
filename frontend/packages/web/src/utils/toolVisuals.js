/**
 * 工具目录的视觉/分组共享工具函数。
 * 与 skillVisuals.js 互补：skillVisuals 处理技能的图标与色彩，本模块处理工具的类型映射和数据集工具分组。
 */

// --- 图标映射 ---

export const TOOL_ICONS = {
    GLOBAL: 'build',
    RUNTIME: 'terminal',
    SKILL_NATIVE: 'smart_toy',
    MCP_REMOTE: 'hub',
    LOWCODE_API: 'api',
    DATASET_TOOL: 'database',
    KNOWLEDGE_BASE_TOOL: 'library_books',
};

// --- 渐变背景 ---

const TOOL_GRADIENTS = {
    GLOBAL: 'from-blue-500 via-blue-500 to-cyan-400',
    RUNTIME: 'from-rose-500 via-orange-500 to-amber-400',
    SKILL_NATIVE: 'from-slate-600 via-slate-500 to-slate-400',
    MCP_REMOTE: 'from-blue-600 via-blue-500 to-indigo-400',
    LOWCODE_API: 'from-emerald-500 via-teal-500 to-cyan-400',
    DATASET_TOOL: 'from-amber-500 via-orange-500 to-yellow-400',
    KNOWLEDGE_BASE_TOOL: 'from-blue-500 via-blue-500 to-cyan-400',
};

// --- 类型标签 ---

const TOOL_TYPE_LABELS = {
    GLOBAL: '公共工具',
    RUNTIME: '运行时工具',
    SKILL_NATIVE: '原生工具',
    MCP_REMOTE: 'MCP 远程',
    LOWCODE_API: '低代码 API',
    DATASET_TOOL: '数据集工具',
    KNOWLEDGE_BASE_TOOL: '知识库工具',
};

// --- 类型徽章样式 ---

const TOOL_TYPE_BADGE_CLASSES = {
    GLOBAL: 'bg-emerald-50 text-emerald-600',
    RUNTIME: 'bg-rose-50 text-rose-600',
    SKILL_NATIVE: 'bg-slate-100 text-slate-600',
    MCP_REMOTE: 'bg-indigo-50 text-indigo-600',
    LOWCODE_API: 'bg-emerald-50 text-emerald-600',
    DATASET_TOOL: 'bg-amber-50 text-amber-600',
    KNOWLEDGE_BASE_TOOL: 'bg-blue-50 text-blue-600',
};

// --- 公共 API ---

export function resolveToolIcon(type) {
    return TOOL_ICONS[type] || 'construction';
}

export function resolveToolGradient(type) {
    return TOOL_GRADIENTS[type] || TOOL_GRADIENTS.GLOBAL;
}

export function resolveToolTypeLabel(type) {
    return TOOL_TYPE_LABELS[type] || '其他工具';
}

export function resolveToolTypeBadgeClass(type) {
    return TOOL_TYPE_BADGE_CLASSES[type] || 'bg-slate-100 text-slate-600';
}

/**
 * 将数据集工具按 source 字段分组折叠。
 *
 * 数据集工具的 source 格式为 "dataset:{datasetCode}"，同一数据集会产生多个子工具。
 * 分组后，普通工具保留为 flat 项，数据集工具合并为一个 group 项。
 *
 * @param {Array} tools - 原始工具列表（已过滤掉不需要的类型）
 * @returns {Array} 混合列表，每项的 type 为 'single' 或 'group'
 */
export function groupDatasetTools(tools) {
    const groups = new Map();
    const singles = [];

    for (const tool of tools) {
        if (tool.type === 'DATASET_TOOL' && tool.source?.startsWith('dataset:')) {
            const groupKey = tool.source;
            if (!groups.has(groupKey)) {
                const datasetName = tool.displayName?.split('/')[0]?.trim() || '未知数据集';
                groups.set(groupKey, {
                    itemType: 'group',
                    groupKey,
                    displayName: datasetName,
                    description: tool.description,
                    children: [],
                    source: tool.source,
                });
            }
            groups.get(groupKey).children.push(tool);
        } else {
            singles.push({ ...tool, itemType: 'single' });
        }
    }

    return [...singles, ...Array.from(groups.values())];
}

/**
 * 从数据集子工具的 displayName 中提取短名称。
 * displayName 格式通常为 "数据集名 / 功能名"，取斜杠后的部分。
 */
export function getChildToolShortName(tool) {
    const parts = tool.displayName?.split('/') || [];
    return parts[1]?.trim() || tool.displayName || tool.name;
}

/**
 * 为数据集工具组生成一段摘要描述。
 * 优先使用第一个子工具的描述；若缺失则拼接所有子工具短名称。
 */
export function resolveGroupDescription(group) {
    if (!group || !Array.isArray(group.children) || group.children.length === 0) {
        return '';
    }
    const firstDesc = group.children.find(c => c.description)?.description;
    if (firstDesc) return firstDesc;
    return group.children.map(c => getChildToolShortName(c)).join('、');
}

/**
 * 从 source 字段提取 MCP server key。
 * source 格式为 "mcp:{serverKey}"。
 */
export function getServerKeyFromSource(source = '') {
    const match = /^mcp:(.+)$/.exec(source);
    return match?.[1] || '';
}
