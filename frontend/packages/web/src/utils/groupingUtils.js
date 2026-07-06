/**
 * 资源分组工具函数
 * 用于前台资源总览和后台权限配置的技能/工具分组显示
 */

import { getChildToolShortName } from './toolVisuals';

/**
 * 用户可配置的工具类型（白名单）
 * - MCP_REMOTE: MCP 远程工具
 * - LOWCODE_API: 低代码 API 工具
 * - DATASET_TOOL: 数据集工具
 * - KNOWLEDGE_BASE_TOOL: 知识库工具
 *
 * 排除的系统工具类型：
 * - GLOBAL: 公共工具，可跨技能追加，不需要角色权限配置
 * - RUNTIME: 系统运行时自动提供，不参与权限绑定
 * - SKILL_NATIVE: 技能原生工具，随技能自动绑定
 */
export const USER_FACING_TOOL_TYPES = new Set([
    'MCP_REMOTE',
    'LOWCODE_API',
    'DATASET_TOOL',
    'KNOWLEDGE_BASE_TOOL',
]);

/**
 * 过滤出用户可配置的工具
 * @param {Array} tools - 工具列表
 * @returns {Array} 过滤后的工具列表
 */
export function filterUserFacingTools(tools) {
    if (!Array.isArray(tools)) return [];
    return tools.filter(t => USER_FACING_TOOL_TYPES.has(t.type));
}

/**
 * 按分类对技能进行分组
 * @param {Array} skills - 技能列表
 * @returns {Array} 分组后的技能列表，每项为 { itemType: 'group'|'single', ... }
 */
export function groupSkillsByCategory(skills) {
    const groups = new Map();
    const result = [];

    for (const skill of skills) {
        const category = skill.category || '通用能力';
        const groupKey = `category:${category}`;

        if (!groups.has(groupKey)) {
            groups.set(groupKey, {
                itemType: 'group',
                groupKey,
                displayName: category,
                children: [],
            });
        }
        groups.get(groupKey).children.push({
            ...skill,
            itemType: 'single',
        });
    }

    // 按分组名称排序，转换为数组
    const sortedGroups = Array.from(groups.values()).sort((a, b) =>
        a.displayName.localeCompare(b.displayName, 'zh-CN')
    );

    // 展开为：分组头 + 子项
    for (const group of sortedGroups) {
        result.push(group);
        for (const child of group.children) {
            result.push(child);
        }
    }

    return result;
}

/**
 * 按类型对工具进行分组
 *
 * 【数据集工具折叠规范】
 * 数据集工具（DATASET_TOOL）必须按数据集（source 字段）分别折叠显示：
 * - 每个数据集（source: "dataset:{datasetCode}"）作为一个独立的折叠组
 * - 组名从 displayName 提取（格式："数据集名 / 功能名"）
 * - 组内包含该数据集的所有子工具
 * - 不展开显示子工具，保持折叠状态
 *
 * 其他工具类型按 type 分组并展开显示。
 *
 * @param {Array} tools - 工具列表
 * @returns {Array} 分组后的工具列表
 */
export function groupToolsByType(tools) {
    const result = [];

    // 工具类型的中文名称和排序
    const typeConfig = {
        MCP_REMOTE: { label: 'MCP 远程工具', order: 1 },
        LOWCODE_API: { label: '低代码 API', order: 2 },
        DATASET_TOOL: { label: '数据集工具', order: 3 },
        KNOWLEDGE_BASE_TOOL: { label: '知识库工具', order: 4 },
    };

    // 按类型分组（非数据集工具）
    const typeGroups = new Map();
    // 数据集工具按 source（数据集）分组
    const datasetGroups = new Map();

    for (const tool of tools) {
        const type = tool.type || 'OTHER';

        // 数据集工具按 source（数据集）分别折叠
        if (type === 'DATASET_TOOL' && tool.source?.startsWith('dataset:')) {
            const groupKey = tool.source;
            if (!datasetGroups.has(groupKey)) {
                // 从 displayName 提取数据集名称（格式："数据集名 / 功能名"）
                const datasetName = tool.displayName?.split('/')[0]?.trim() || '未知数据集';
                datasetGroups.set(groupKey, {
                    itemType: 'group',
                    groupKey,
                    displayName: datasetName,
                    toolType: 'DATASET_TOOL',
                    order: 3, // 数据集工具的排序
                    source: tool.source,
                    children: [],
                });
            }
            datasetGroups.get(groupKey).children.push(tool);
        } else {
            // 其他工具按类型分组
            const groupKey = `type:${type}`;
            if (!typeGroups.has(groupKey)) {
                const config = typeConfig[type] || { label: type, order: 99 };
                typeGroups.set(groupKey, {
                    itemType: 'group',
                    groupKey,
                    displayName: config.label,
                    toolType: type,
                    order: config.order,
                    children: [],
                });
            }
            typeGroups.get(groupKey).children.push({ ...tool, itemType: 'single' });
        }
    }

    // 合并所有分组并排序
    const allGroups = [
        ...Array.from(typeGroups.values()),
        ...Array.from(datasetGroups.values()),
    ].sort((a, b) => a.order - b.order);

    // 构建结果
    for (const group of allGroups) {
        if (group.toolType === 'DATASET_TOOL') {
            // 数据集工具组：只显示折叠的 group 项，不展开子项
            result.push(group);
        } else {
            // 其他工具组：显示 group 头 + 展开的 single 项
            result.push(group);
            for (const child of group.children) {
                result.push(child);
            }
        }
    }

    return result;
}

/**
 * 从分组列表中获取所有可选择的 ID
 * @param {Array} items - 分组后的列表
 * @returns {Array} 所有可选择的 ID
 */
export function getAllSelectableIds(items) {
    const ids = [];
    for (const item of items) {
        if (item.itemType === 'single' && item.id != null) {
            ids.push(item.id);
        }
    }
    return ids;
}

/**
 * 判断分组是否全选
 * @param {Object} group - 分组对象
 * @param {Array} selectedIds - 已选择的 ID 列表
 * @returns {boolean}
 */
export function isGroupSelected(group, selectedIds) {
    if (!group?.children?.length) return false;
    return group.children.every(c => selectedIds.includes(c.id));
}

/**
 * 判断分组是否部分选中
 * @param {Object} group - 分组对象
 * @param {Array} selectedIds - 已选择的 ID 列表
 * @returns {boolean}
 */
export function isGroupPartialSelected(group, selectedIds) {
    if (!group?.children?.length) return false;
    const selectedCount = group.children.filter(c => selectedIds.includes(c.id)).length;
    return selectedCount > 0 && selectedCount < group.children.length;
}

/**
 * 切换分组选择状态
 * @param {Object} group - 分组对象
 * @param {Array} selectedIds - 当前已选择的 ID 列表
 * @returns {Array} 新的选择 ID 列表
 */
export function toggleGroupSelection(group, selectedIds) {
    const childIds = group.children.map(c => c.id);
    const allSelected = childIds.every(id => selectedIds.includes(id));

    if (allSelected) {
        // 取消全选
        return selectedIds.filter(id => !childIds.includes(id));
    } else {
        // 全选
        return [...new Set([...selectedIds, ...childIds])];
    }
}

/**
 * 获取分组子项的名称列表（用于展示）
 * @param {Array} children - 子项列表
 * @param {string} type - 分组类型 ('skill' | 'tool')
 * @returns {string}
 */
export function getChildrenNames(children, type = 'tool') {
    if (!children?.length) return '';
    if (type === 'tool') {
        return children.map(c => getChildToolShortName(c)).join('、');
    }
    return children.map(c => c.displayName || c.runtimeSkillName || '').join('、');
}
