<script setup>
import { ref, onMounted, computed } from 'vue';
import { listSkillCatalog } from '@/api/skills';
import { listToolCatalog } from '@/api/tools';
import {
    groupSkillsByCategory,
    groupToolsByType,
    isGroupSelected,
    isGroupPartialSelected,
    toggleGroupSelection,
    getChildrenNames,
    getChildToolShortName,
    filterUserFacingTools,
} from '@/utils/groupingUtils';

const props = defineProps({
    roleId: {
        type: Number,
        required: true,
    },
    initialSkillIds: {
        type: Array,
        default: () => [],
    },
    initialToolIds: {
        type: Array,
        default: () => [],
    },
});

const emit = defineEmits(['update:skillIds', 'update:toolIds']);

const activeTab = ref('skills'); // 'skills' | 'tools'
const skillSearchKeyword = ref('');
const toolSearchKeyword = ref('');
const allSkills = ref([]);
const allTools = ref([]);
const selectedSkillIds = ref([...props.initialSkillIds]);
const selectedToolIds = ref([...props.initialToolIds]);
const loading = ref(false);

// --- 技能分组逻辑 ---
const groupedSkills = computed(() => {
    return groupSkillsByCategory(allSkills.value);
});

// 过滤后的技能列表（支持搜索）
const filteredSkills = computed(() => {
    const keyword = skillSearchKeyword.value.toLowerCase();
    if (!keyword) {
        return groupedSkills.value;
    }

    // 搜索时展开匹配的分组
    const result = [];
    const matchedGroups = new Set();

    for (const item of groupedSkills.value) {
        if (item.itemType === 'group') {
            // 检查分组名或子项是否匹配
            const matchGroup = item.displayName.toLowerCase().includes(keyword);
            const matchChildren = item.children.some(
                c =>
                    c.displayName?.toLowerCase().includes(keyword) ||
                    c.runtimeSkillName?.toLowerCase().includes(keyword) ||
                    c.description?.toLowerCase().includes(keyword)
            );
            if (matchGroup || matchChildren) {
                matchedGroups.add(item.groupKey);
            }
        } else if (item.itemType === 'single') {
            // 单项直接匹配
            const match =
                item.displayName?.toLowerCase().includes(keyword) ||
                item.runtimeSkillName?.toLowerCase().includes(keyword) ||
                item.description?.toLowerCase().includes(keyword);
            if (match) {
                matchedGroups.add(`category:${item.category || '通用能力'}`);
            }
        }
    }

    // 重新构建过滤后的列表
    for (const item of groupedSkills.value) {
        if (item.itemType === 'group' && matchedGroups.has(item.groupKey)) {
            result.push(item);
            for (const child of item.children) {
                result.push(child);
            }
        }
    }

    return result;
});

// --- 工具分组逻辑 ---
const groupedTools = computed(() => {
    return groupToolsByType(allTools.value);
});

// 过滤后的工具列表（支持搜索）
const filteredTools = computed(() => {
    const keyword = toolSearchKeyword.value.toLowerCase();
    if (!keyword) {
        return groupedTools.value;
    }

    // 搜索时展开匹配的分组
    const result = [];
    const matchedGroups = new Set();

    for (const item of groupedTools.value) {
        if (item.itemType === 'group') {
            // 检查分组名或子项是否匹配
            const matchGroup = item.displayName.toLowerCase().includes(keyword);
            const matchChildren = item.children.some(
                c =>
                    c.displayName?.toLowerCase().includes(keyword) ||
                    c.name?.toLowerCase().includes(keyword) ||
                    c.description?.toLowerCase().includes(keyword)
            );
            if (matchGroup || matchChildren) {
                matchedGroups.add(item.groupKey);
            }
        } else if (item.itemType === 'single') {
            // 单项直接匹配
            const match =
                item.displayName?.toLowerCase().includes(keyword) ||
                item.name?.toLowerCase().includes(keyword) ||
                item.description?.toLowerCase().includes(keyword);
            if (match) {
                // 找到所属分组
                if (item.type === 'DATASET_TOOL' && item.source?.startsWith('dataset:')) {
                    matchedGroups.add(item.source);
                } else {
                    matchedGroups.add(`type:${item.type || 'OTHER'}`);
                }
            }
        }
    }

    // 重新构建过滤后的列表
    for (const item of groupedTools.value) {
        if (item.itemType === 'group' && matchedGroups.has(item.groupKey)) {
            result.push(item);
            for (const child of item.children) {
                result.push(child);
            }
        }
    }

    return result;
});

// --- 技能选择操作 ---
const selectAllSkills = () => {
    const allIds = [];
    for (const item of groupedSkills.value) {
        if (item.itemType === 'single') {
            allIds.push(item.id);
        }
    }
    selectedSkillIds.value = allIds;
};

const invertSkillSelection = () => {
    const allIds = new Set();
    for (const item of groupedSkills.value) {
        if (item.itemType === 'single') {
            allIds.add(item.id);
        }
    }
    const selected = new Set(selectedSkillIds.value);
    selectedSkillIds.value = [...allIds].filter(id => !selected.has(id));
};

const toggleSkillGroup = group => {
    selectedSkillIds.value = toggleGroupSelection(group, selectedSkillIds.value);
};

const isSkillGroupSelected = group => {
    return isGroupSelected(group, selectedSkillIds.value);
};

const isSkillGroupPartialSelected = group => {
    return isGroupPartialSelected(group, selectedSkillIds.value);
};

// --- 工具选择操作 ---
const selectAllTools = () => {
    const allIds = [];
    for (const item of groupedTools.value) {
        if (item.itemType === 'single') {
            allIds.push(item.id);
        }
    }
    selectedToolIds.value = allIds;
};

const invertToolSelection = () => {
    const allIds = new Set();
    for (const item of groupedTools.value) {
        if (item.itemType === 'single') {
            allIds.add(item.id);
        }
    }
    const selected = new Set(selectedToolIds.value);
    selectedToolIds.value = [...allIds].filter(id => !selected.has(id));
};

const toggleToolGroup = group => {
    selectedToolIds.value = toggleGroupSelection(group, selectedToolIds.value);
};

const isToolGroupSelected = group => {
    return isGroupSelected(group, selectedToolIds.value);
};

const isToolGroupPartialSelected = group => {
    return isGroupPartialSelected(group, selectedToolIds.value);
};

// --- 数据加载 ---
const loadSkills = async () => {
    try {
        const data = await listSkillCatalog({}, () => {});
        allSkills.value = data?.items || data || [];
    } catch (error) {
        console.error('Failed to load skills:', error);
    }
};

const loadTools = async () => {
    try {
        const data = await listToolCatalog({}, () => {});
        allTools.value = filterUserFacingTools(data?.items || data || []);
    } catch (error) {
        console.error('Failed to load tools:', error);
    }
};

onMounted(async () => {
    loading.value = true;
    await Promise.all([loadSkills(), loadTools()]);
    loading.value = false;
});

// 暴露数据给父组件
defineExpose({
    getSelectedSkillIds: () => selectedSkillIds.value,
    getSelectedToolIds: () => selectedToolIds.value,
});
</script>

<template>
    <div class="p-4">
        <!-- Tab 导航 -->
        <div class="mb-4 flex border-b border-gray-200">
            <button
                type="button"
                :class="[
                    'px-4 py-2 text-sm font-medium transition-colors',
                    activeTab === 'skills'
                        ? 'border-b-2 border-blue-600 text-blue-600'
                        : 'text-gray-600 hover:text-gray-900',
                ]"
                @click="activeTab = 'skills'"
            >
                技能权限
                <span
                    :class="[
                        'ml-1 rounded-full px-2 py-0.5 text-xs',
                        activeTab === 'skills'
                            ? 'bg-blue-100 text-blue-700'
                            : 'bg-gray-100 text-gray-600',
                    ]"
                >
                    {{ selectedSkillIds.length }}
                </span>
            </button>
            <button
                type="button"
                :class="[
                    'px-4 py-2 text-sm font-medium transition-colors',
                    activeTab === 'tools'
                        ? 'border-b-2 border-blue-600 text-blue-600'
                        : 'text-gray-600 hover:text-gray-900',
                ]"
                @click="activeTab = 'tools'"
            >
                工具权限
                <span
                    :class="[
                        'ml-1 rounded-full px-2 py-0.5 text-xs',
                        activeTab === 'tools'
                            ? 'bg-blue-100 text-blue-700'
                            : 'bg-gray-100 text-gray-600',
                    ]"
                >
                    {{ selectedToolIds.length }}
                </span>
            </button>
        </div>

        <!-- 技能权限 Tab 内容 -->
        <div v-show="activeTab === 'skills'">
            <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-semibold text-gray-900">技能列表</h3>
                <div class="flex gap-2">
                    <button
                        type="button"
                        class="text-xs text-blue-600 hover:text-blue-700"
                        @click="selectAllSkills"
                    >
                        全选
                    </button>
                    <button
                        type="button"
                        class="text-xs text-blue-600 hover:text-blue-700"
                        @click="invertSkillSelection"
                    >
                        反选
                    </button>
                </div>
            </div>
            <input
                v-model="skillSearchKeyword"
                type="text"
                placeholder="搜索技能..."
                class="mb-3 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <div class="max-h-[400px] overflow-y-auto rounded border border-gray-200">
                <div v-if="loading" class="p-4 text-center text-sm text-gray-500">加载中...</div>
                <div
                    v-else-if="filteredSkills.length === 0"
                    class="p-4 text-center text-sm text-gray-500"
                >
                    暂无技能
                </div>
                <div v-else>
                    <template v-for="item in filteredSkills" :key="item.id || item.groupKey">
                        <!-- 分组头 -->
                        <div
                            v-if="item.itemType === 'group'"
                            class="flex cursor-pointer items-center border-b border-gray-100 bg-gray-50 px-3 py-2 hover:bg-gray-100"
                            @click="toggleSkillGroup(item)"
                        >
                            <input
                                type="checkbox"
                                :checked="isSkillGroupSelected(item)"
                                :indeterminate="isSkillGroupPartialSelected(item)"
                                class="mr-2"
                                @click.stop
                                @change="toggleSkillGroup(item)"
                            />
                            <span class="text-sm font-semibold text-gray-700">
                                {{ item.displayName }}
                            </span>
                            <span
                                class="ml-2 rounded-full bg-gray-200 px-2 py-0.5 text-xs text-gray-600"
                            >
                                {{ item.children.length }}个
                            </span>
                        </div>
                        <!-- 单个技能 -->
                        <label
                            v-else-if="item.itemType === 'single'"
                            class="flex cursor-pointer items-center border-b border-gray-100 px-3 py-2 pl-8 hover:bg-gray-50"
                        >
                            <input
                                v-model="selectedSkillIds"
                                type="checkbox"
                                :value="item.id"
                                class="mr-2"
                            />
                            <div class="flex-1">
                                <div class="text-sm font-medium text-gray-900">
                                    {{ item.displayName || item.runtimeSkillName }}
                                </div>
                                <div v-if="item.description" class="text-xs text-gray-500">
                                    {{ item.description }}
                                </div>
                            </div>
                        </label>
                    </template>
                </div>
            </div>
        </div>

        <!-- 工具权限 Tab 内容 -->
        <div v-show="activeTab === 'tools'">
            <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-semibold text-gray-900">工具列表</h3>
                <div class="flex gap-2">
                    <button
                        type="button"
                        class="text-xs text-blue-600 hover:text-blue-700"
                        @click="selectAllTools"
                    >
                        全选
                    </button>
                    <button
                        type="button"
                        class="text-xs text-blue-600 hover:text-blue-700"
                        @click="invertToolSelection"
                    >
                        反选
                    </button>
                </div>
            </div>
            <input
                v-model="toolSearchKeyword"
                type="text"
                placeholder="搜索工具..."
                class="mb-3 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <div class="max-h-[400px] overflow-y-auto rounded border border-gray-200">
                <div v-if="loading" class="p-4 text-center text-sm text-gray-500">加载中...</div>
                <div
                    v-else-if="filteredTools.length === 0"
                    class="p-4 text-center text-sm text-gray-500"
                >
                    暂无工具
                </div>
                <div v-else>
                    <template v-for="item in filteredTools" :key="item.id || item.groupKey">
                        <!-- 分组头 -->
                        <div
                            v-if="item.itemType === 'group'"
                            class="flex cursor-pointer items-center border-b border-gray-100 bg-gray-50 px-3 py-2 hover:bg-gray-100"
                            @click="toggleToolGroup(item)"
                        >
                            <input
                                type="checkbox"
                                :checked="isToolGroupSelected(item)"
                                :indeterminate="isToolGroupPartialSelected(item)"
                                class="mr-2"
                                @click.stop
                                @change="toggleToolGroup(item)"
                            />
                            <span class="text-sm font-semibold text-gray-700">
                                {{ item.displayName }}
                            </span>
                            <span
                                class="ml-2 rounded-full bg-gray-200 px-2 py-0.5 text-xs text-gray-600"
                            >
                                {{ item.children.length }}个
                            </span>
                            <!-- 数据集工具组显示子工具名称 -->
                            <div
                                v-if="item.toolType === 'DATASET_TOOL'"
                                class="ml-2 text-xs text-gray-500"
                            >
                                ({{ getChildrenNames(item.children, 'tool') }})
                            </div>
                        </div>
                        <!-- 单个工具 -->
                        <label
                            v-else-if="item.itemType === 'single'"
                            class="flex cursor-pointer items-center border-b border-gray-100 px-3 py-2 pl-8 hover:bg-gray-50"
                        >
                            <input
                                v-model="selectedToolIds"
                                type="checkbox"
                                :value="item.id"
                                class="mr-2"
                            />
                            <div class="flex-1">
                                <div class="text-sm font-medium text-gray-900">
                                    {{ item.displayName || item.name }}
                                </div>
                                <div v-if="item.description" class="text-xs text-gray-500">
                                    {{ item.description }}
                                </div>
                            </div>
                        </label>
                    </template>
                </div>
            </div>
        </div>
    </div>
</template>
