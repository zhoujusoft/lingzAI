<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import { listEnabledAgents, getRoleResources } from '@/api/roles';
import { getAgentDetail } from '@/api/agents';
import { ADMIN_MENU_PERMISSION_GROUPS } from '@/model/admin-menu-permissions';
import { listSkillCatalog } from '@/api/skills';
import { listToolCatalog } from '@/api/tools';

const props = defineProps({
    context: {
        type: Object,
        default: () => ({}),
    },
});

// ─── Form fields ───
const roleCode = computed({
    get: () => props.context.roleCode ?? '',
    set: value => {
        props.context.roleCode = value;
    },
});
const roleName = computed({
    get: () => props.context.roleName ?? '',
    set: value => {
        props.context.roleName = value;
    },
});
const description = computed({
    get: () => props.context.description ?? '',
    set: value => {
        props.context.description = value;
    },
});
const enabled = computed({
    get: () => props.context.enabled ?? 1,
    set: value => {
        props.context.enabled = value;
    },
});
const agentId = computed({
    get: () => props.context.agentId ?? null,
    set: value => {
        props.context.agentId = value;
    },
});
const menuPermissions = computed({
    get: () => (Array.isArray(props.context.menuPermissions) ? props.context.menuPermissions : []),
    set: value => {
        props.context.menuPermissions = Array.isArray(value) ? value : [];
    },
});
const formErrors = computed(() => props.context.formErrors ?? null);
const submitError = computed(() => props.context.submitError ?? '');
const onUnauthorized = computed(() => props.context.onUnauthorized ?? (() => {}));
const formMode = computed(() => props.context.mode ?? 'create');
const isEdit = computed(() => formMode.value === 'edit');
const activeTab = ref('basic'); // 'basic' | 'resources'

// ─── Agent templates ───
const agents = ref([]);
const localSelectedAgent = ref(null);
const agentOptions = computed(() => {
    const options = [{ value: null, label: '不绑定模板' }];
    for (const agent of agents.value) {
        options.push({
            value: agent.id,
            label: `${agent.agentName} (${agent.agentCode})`,
        });
    }
    return options;
});

async function loadAgents() {
    try {
        const data = await listEnabledAgents(onUnauthorized.value);
        agents.value = Array.isArray(data) ? data : [];
    } catch {
        agents.value = [];
    }
}

async function handleAgentChange(value) {
    agentId.value = value;
    if (!value) {
        localSelectedAgent.value = null;
        return;
    }
    try {
        const detail = await getAgentDetail(value, onUnauthorized.value);
        localSelectedAgent.value = detail
            ? { ...detail, description: detail.description || detail.agentDescription || '' }
            : null;
    } catch {
        localSelectedAgent.value = agents.value.find(item => item.id === value) || null;
    }
}

// ─── Field errors ───
function clearFieldError(field) {
    if (props.context.formErrors && props.context.formErrors[field]) {
        delete props.context.formErrors[field];
    }
    if (props.context.submitError) {
        props.context.submitError = '';
    }
}

// ─── Menu permissions ───
function hasMenuPermission(permissionKey) {
    return menuPermissions.value.includes(permissionKey);
}

function toggleMenuPermission(permissionKey, checked) {
    const next = new Set(menuPermissions.value);
    if (checked) {
        next.add(permissionKey);
    } else {
        next.delete(permissionKey);
    }
    menuPermissions.value = Array.from(next);
    clearFieldError('menuPermissions');
}

function isGroupFullySelected(groupOptions) {
    if (!Array.isArray(groupOptions) || groupOptions.length === 0) return false;
    return groupOptions.every(option => hasMenuPermission(option.key));
}

function toggleGroupPermissions(groupOptions, checked) {
    const next = new Set(menuPermissions.value);
    for (const option of groupOptions) {
        if (checked) {
            next.add(option.key);
        } else {
            next.delete(option.key);
        }
    }
    menuPermissions.value = Array.from(next);
    clearFieldError('menuPermissions');
}

// ─── Resource permissions ───
const activeResourceTab = ref('skills');
const skillSearchKeyword = ref('');
const toolSearchKeyword = ref('');
const allSkills = ref([]);
const allTools = ref([]);
const selectedSkillIds = ref([]);
const selectedToolIds = ref([]);
const resourceLoading = ref(false);

const filteredSkills = computed(() => {
    if (!skillSearchKeyword.value) return allSkills.value;
    const kw = skillSearchKeyword.value.toLowerCase();
    return allSkills.value.filter(
        s =>
            s.displayName?.toLowerCase().includes(kw) ||
            s.runtimeSkillName?.toLowerCase().includes(kw)
    );
});

const groupedTools = computed(() => {
    const groups = new Map();
    const singles = [];
    for (const tool of allTools.value) {
        if (tool.type === 'DATASET_TOOL' && tool.source?.startsWith('dataset:')) {
            const groupKey = tool.source;
            if (!groups.has(groupKey)) {
                const datasetName = tool.displayName?.split('/')[0]?.trim() || '未知数据集';
                groups.set(groupKey, {
                    type: 'group',
                    groupKey,
                    displayName: datasetName,
                    description: '数据集工具组（包含摘要检索、结构获取、SQL执行）',
                    children: [],
                    permissionScope: tool.permissionScope,
                });
            }
            groups.get(groupKey).children.push(tool);
        } else {
            singles.push({ type: 'single', ...tool });
        }
    }
    return [...singles, ...Array.from(groups.values())];
});

const filteredTools = computed(() => {
    if (!toolSearchKeyword.value) return groupedTools.value;
    const kw = toolSearchKeyword.value.toLowerCase();
    return groupedTools.value.filter(item => {
        if (item.type === 'single') {
            return (
                item.displayName?.toLowerCase().includes(kw) ||
                item.name?.toLowerCase().includes(kw) ||
                item.description?.toLowerCase().includes(kw)
            );
        }
        if (item.type === 'group') {
            return (
                item.displayName?.toLowerCase().includes(kw) ||
                item.description?.toLowerCase().includes(kw) ||
                item.children.some(
                    c =>
                        c.displayName?.toLowerCase().includes(kw) ||
                        c.name?.toLowerCase().includes(kw)
                )
            );
        }
        return false;
    });
});

const selectAllSkills = () => {
    selectedSkillIds.value = filteredSkills.value.map(s => s.id);
};
const invertSkillSelection = () => {
    const allIds = new Set(filteredSkills.value.map(s => s.id));
    const selected = new Set(selectedSkillIds.value);
    selectedSkillIds.value = [...allIds].filter(id => !selected.has(id));
};
const selectAllTools = () => {
    const allIds = [];
    for (const item of filteredTools.value) {
        if (item.type === 'single') allIds.push(item.id);
        else if (item.type === 'group') allIds.push(...item.children.map(c => c.id));
    }
    selectedToolIds.value = allIds;
};
const invertToolSelection = () => {
    const allIds = [];
    for (const item of filteredTools.value) {
        if (item.type === 'single') allIds.push(item.id);
        else if (item.type === 'group') allIds.push(...item.children.map(c => c.id));
    }
    const selected = new Set(selectedToolIds.value);
    selectedToolIds.value = allIds.filter(id => !selected.has(id));
};
const toggleToolGroup = group => {
    const childIds = group.children.map(c => c.id);
    const allSelected = childIds.every(id => selectedToolIds.value.includes(id));
    if (allSelected) {
        selectedToolIds.value = selectedToolIds.value.filter(id => !childIds.includes(id));
    } else {
        selectedToolIds.value = [...new Set([...selectedToolIds.value, ...childIds])];
    }
};
const isToolGroupSelected = group =>
    group.children.every(c => selectedToolIds.value.includes(c.id));
const isToolGroupPartialSelected = group => {
    const count = group.children.filter(c => selectedToolIds.value.includes(c.id)).length;
    return count > 0 && count < group.children.length;
};
const getChildToolNames = children =>
    children
        .map(c => {
            const parts = c.displayName?.split('/') || [];
            return parts[1]?.trim() || c.displayName || c.name;
        })
        .join('、');

async function loadSkills() {
    try {
        const data = await listSkillCatalog({}, () => {});
        allSkills.value = data?.items || data || [];
    } catch {
        allSkills.value = [];
    }
}

async function loadTools() {
    try {
        const data = await listToolCatalog({}, () => {});
        allTools.value = data?.items || data || [];
    } catch {
        allTools.value = [];
    }
}

async function loadRoleResources(roleId) {
    try {
        const data = await getRoleResources(roleId, onUnauthorized.value);
        selectedSkillIds.value = data?.skillIds || [];
        selectedToolIds.value = data?.toolIds || [];
    } catch {
        selectedSkillIds.value = [];
        selectedToolIds.value = [];
    }
}

// Sync selected IDs to context for footer save
watch(
    selectedSkillIds,
    ids => {
        props.context.selectedSkillIds = ids;
    },
    { deep: true }
);
watch(
    selectedToolIds,
    ids => {
        props.context.selectedToolIds = ids;
    },
    { deep: true }
);

// ─── Lifecycle ───
onMounted(async () => {
    loadAgents();
    resourceLoading.value = true;
    await Promise.all([loadSkills(), loadTools()]);
    if (isEdit.value && props.context.id) {
        await loadRoleResources(props.context.id);
    }
    resourceLoading.value = false;
});

defineExpose({
    getSelectedSkillIds: () => selectedSkillIds.value,
    getSelectedToolIds: () => selectedToolIds.value,
});
</script>

<template>
    <div class="flex flex-col overflow-hidden">
        <!-- Tabs (only in edit mode) -->
        <div v-if="isEdit" class="flex border-b border-slate-200 bg-white px-8">
            <button
                type="button"
                :class="[
                    'relative px-5 py-3.5 text-sm font-medium transition-colors',
                    activeTab === 'basic' ? 'text-blue-600' : 'text-slate-500 hover:text-slate-700',
                ]"
                @click="activeTab = 'basic'"
            >
                基本信息
                <span
                    v-if="activeTab === 'basic'"
                    class="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600"
                />
            </button>
            <button
                v-if="isEdit"
                type="button"
                :class="[
                    'relative px-5 py-3.5 text-sm font-medium transition-colors',
                    activeTab === 'resources'
                        ? 'text-blue-600'
                        : 'text-slate-500 hover:text-slate-700',
                ]"
                @click="activeTab = 'resources'"
            >
                资源权限
                <span
                    class="ml-1.5 rounded-full bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500"
                >
                    {{ selectedSkillIds.length + selectedToolIds.length }}
                </span>
                <span
                    v-if="activeTab === 'resources'"
                    class="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600"
                />
            </button>
        </div>

        <!-- Scrollable content -->
        <div class="max-h-[65vh] overflow-y-auto p-8">
            <!-- Error banner -->
            <div
                v-if="submitError"
                class="mb-5 rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-600"
            >
                {{ submitError }}
            </div>

            <!-- ─── Basic Info Tab ─── -->
            <div v-show="activeTab === 'basic'" class="space-y-5">
                <div class="space-y-2">
                    <label class="block text-sm font-semibold text-slate-700">
                        角色编码 <span class="text-red-500">*</span>
                    </label>
                    <input
                        v-model="roleCode"
                        type="text"
                        placeholder="请输入角色编码"
                        :class="[
                            'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                            formErrors?.roleCode
                                ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                                : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                        ]"
                        @input="clearFieldError('roleCode')"
                    />
                    <p v-if="formErrors?.roleCode" class="text-xs text-red-500">
                        {{ formErrors.roleCode }}
                    </p>
                </div>

                <div class="space-y-2">
                    <label class="block text-sm font-semibold text-slate-700">
                        角色名称 <span class="text-red-500">*</span>
                    </label>
                    <input
                        v-model="roleName"
                        type="text"
                        placeholder="请输入角色名称"
                        :class="[
                            'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                            formErrors?.roleName
                                ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                                : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                        ]"
                        @input="clearFieldError('roleName')"
                    />
                    <p v-if="formErrors?.roleName" class="text-xs text-red-500">
                        {{ formErrors.roleName }}
                    </p>
                </div>

                <div class="space-y-2">
                    <label class="block text-sm font-semibold text-slate-700">描述</label>
                    <textarea
                        v-model="description"
                        placeholder="请输入角色描述"
                        rows="2"
                        class="w-full resize-none rounded-xl border border-slate-200 bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                    />
                </div>

                <div class="space-y-2">
                    <label class="block text-sm font-semibold text-slate-700">状态</label>
                    <div class="flex items-center gap-6">
                        <label class="flex cursor-pointer items-center gap-2">
                            <input
                                type="radio"
                                :value="1"
                                :checked="enabled === 1"
                                class="h-4 w-4 text-blue-600"
                                @change="enabled = 1"
                            />
                            <span class="text-sm text-slate-700">启用</span>
                        </label>
                        <label class="flex cursor-pointer items-center gap-2">
                            <input
                                type="radio"
                                :value="0"
                                :checked="enabled === 0"
                                class="h-4 w-4 text-blue-600"
                                @change="enabled = 0"
                            />
                            <span class="text-sm text-slate-700">停用</span>
                        </label>
                    </div>
                </div>

                <div class="space-y-2">
                    <label class="block text-sm font-semibold text-slate-700"
                        >预设 Agent 模板</label
                    >
                    <AppSelect
                        :model-value="agentId"
                        :options="agentOptions"
                        placeholder="请选择模板"
                        button-class="bg-slate-50 shadow-none hover:bg-white"
                        menu-placement="top"
                        @update:model-value="handleAgentChange"
                    />
                </div>

                <div class="space-y-3">
                    <div class="flex items-center justify-between gap-4">
                        <label class="block text-sm font-semibold text-slate-700">菜单权限</label>
                        <span class="text-xs text-slate-500"
                            >共 {{ menuPermissions.length }} 项已勾选</span
                        >
                    </div>
                    <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
                        <div
                            v-for="group in ADMIN_MENU_PERMISSION_GROUPS"
                            :key="group.id"
                            class="rounded-xl border border-slate-200 bg-slate-50 p-3"
                        >
                            <div class="mb-2 flex items-center justify-between gap-3">
                                <h4 class="text-sm font-semibold text-slate-700">
                                    {{ group.label }}
                                </h4>
                                <label
                                    class="inline-flex items-center gap-1.5 text-xs text-slate-500"
                                >
                                    <input
                                        type="checkbox"
                                        :checked="isGroupFullySelected(group.options)"
                                        class="h-3.5 w-3.5 rounded border-slate-300 text-primary focus:ring-primary/30"
                                        @change="
                                            toggleGroupPermissions(
                                                group.options,
                                                $event.target.checked
                                            )
                                        "
                                    />
                                    全选
                                </label>
                            </div>
                            <div class="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
                                <label
                                    v-for="option in group.options"
                                    :key="option.key"
                                    class="inline-flex items-center gap-2 text-sm text-slate-700"
                                >
                                    <input
                                        type="checkbox"
                                        :checked="hasMenuPermission(option.key)"
                                        class="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary/30"
                                        @change="
                                            toggleMenuPermission(option.key, $event.target.checked)
                                        "
                                    />
                                    <span>{{ option.label }}</span>
                                </label>
                            </div>
                        </div>
                    </div>
                    <p v-if="formErrors?.menuPermissions" class="text-xs text-red-500">
                        {{ formErrors.menuPermissions }}
                    </p>
                </div>
            </div>

            <!-- ─── Resource Permissions Tab ─── -->
            <div v-if="isEdit" v-show="activeTab === 'resources'">
                <!-- Sub-tabs: skills / tools -->
                <div class="mb-4 flex border-b border-slate-200">
                    <button
                        type="button"
                        :class="[
                            'px-4 py-2 text-sm font-medium transition-colors',
                            activeResourceTab === 'skills'
                                ? 'border-b-2 border-blue-600 text-blue-600'
                                : 'text-slate-500 hover:text-slate-700',
                        ]"
                        @click="activeResourceTab = 'skills'"
                    >
                        技能权限
                        <span
                            :class="[
                                'ml-1.5 rounded-full px-2 py-0.5 text-xs',
                                activeResourceTab === 'skills'
                                    ? 'bg-blue-100 text-blue-700'
                                    : 'bg-slate-100 text-slate-500',
                            ]"
                        >
                            {{ selectedSkillIds.length }}
                        </span>
                    </button>
                    <button
                        type="button"
                        :class="[
                            'px-4 py-2 text-sm font-medium transition-colors',
                            activeResourceTab === 'tools'
                                ? 'border-b-2 border-blue-600 text-blue-600'
                                : 'text-slate-500 hover:text-slate-700',
                        ]"
                        @click="activeResourceTab = 'tools'"
                    >
                        工具权限
                        <span
                            :class="[
                                'ml-1.5 rounded-full px-2 py-0.5 text-xs',
                                activeResourceTab === 'tools'
                                    ? 'bg-blue-100 text-blue-700'
                                    : 'bg-slate-100 text-slate-500',
                            ]"
                        >
                            {{ selectedToolIds.length }}
                        </span>
                    </button>
                </div>

                <!-- Skills list -->
                <div v-show="activeResourceTab === 'skills'">
                    <div class="mb-3 flex items-center justify-between">
                        <h3 class="text-sm font-semibold text-slate-800">技能列表</h3>
                        <div class="flex gap-3">
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
                        class="mb-3 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
                    />
                    <div class="max-h-[360px] overflow-y-auto rounded-lg border border-slate-200">
                        <div v-if="resourceLoading" class="p-4 text-center text-sm text-slate-400">
                            加载中...
                        </div>
                        <div
                            v-else-if="filteredSkills.length === 0"
                            class="p-4 text-center text-sm text-slate-400"
                        >
                            暂无技能
                        </div>
                        <div v-else>
                            <label
                                v-for="skill in filteredSkills"
                                :key="skill.id"
                                class="flex cursor-pointer items-center border-b border-slate-100 px-3 py-2.5 hover:bg-slate-50"
                            >
                                <input
                                    v-model="selectedSkillIds"
                                    type="checkbox"
                                    :value="skill.id"
                                    class="mr-2.5 h-4 w-4 rounded border-slate-300 text-blue-600"
                                />
                                <div class="flex-1">
                                    <div class="text-sm font-medium text-slate-800">
                                        {{ skill.displayName || skill.runtimeSkillName }}
                                    </div>
                                    <div v-if="skill.description" class="text-xs text-slate-500">
                                        {{ skill.description }}
                                    </div>
                                </div>
                            </label>
                        </div>
                    </div>
                </div>

                <!-- Tools list -->
                <div v-show="activeResourceTab === 'tools'">
                    <div class="mb-3 flex items-center justify-between">
                        <h3 class="text-sm font-semibold text-slate-800">工具列表</h3>
                        <div class="flex gap-3">
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
                        class="mb-3 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
                    />
                    <div class="max-h-[360px] overflow-y-auto rounded-lg border border-slate-200">
                        <div v-if="resourceLoading" class="p-4 text-center text-sm text-slate-400">
                            加载中...
                        </div>
                        <div
                            v-else-if="filteredTools.length === 0"
                            class="p-4 text-center text-sm text-slate-400"
                        >
                            暂无工具
                        </div>
                        <div v-else>
                            <template v-for="item in filteredTools" :key="item.id || item.groupKey">
                                <!-- Single tool -->
                                <label
                                    v-if="item.type === 'single'"
                                    class="flex cursor-pointer items-center border-b border-slate-100 px-3 py-2.5 hover:bg-slate-50"
                                >
                                    <input
                                        v-model="selectedToolIds"
                                        type="checkbox"
                                        :value="item.id"
                                        class="mr-2.5 h-4 w-4 rounded border-slate-300 text-blue-600"
                                    />
                                    <div class="flex-1">
                                        <div class="text-sm font-medium text-slate-800">
                                            {{ item.displayName || item.name }}
                                        </div>
                                        <div v-if="item.description" class="text-xs text-slate-500">
                                            {{ item.description }}
                                        </div>
                                    </div>
                                </label>

                                <!-- Tool group (dataset) -->
                                <div
                                    v-else-if="item.type === 'group'"
                                    class="border-b border-slate-100"
                                >
                                    <div
                                        class="flex cursor-pointer items-center px-3 py-2.5 hover:bg-slate-50"
                                        @click="toggleToolGroup(item)"
                                    >
                                        <input
                                            type="checkbox"
                                            :checked="isToolGroupSelected(item)"
                                            :indeterminate="isToolGroupPartialSelected(item)"
                                            class="mr-2.5 h-4 w-4 rounded border-slate-300 text-blue-600"
                                            @click.stop
                                            @change="toggleToolGroup(item)"
                                        />
                                        <div class="flex flex-1 items-center gap-2">
                                            <div class="flex-1">
                                                <div class="flex items-center gap-2">
                                                    <span
                                                        class="text-sm font-medium text-slate-800"
                                                        >{{ item.displayName }}</span
                                                    >
                                                    <span
                                                        class="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700"
                                                    >
                                                        {{ item.children.length }}个子工具
                                                    </span>
                                                </div>
                                                <div class="text-xs text-slate-500">
                                                    {{ getChildToolNames(item.children) }}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </template>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
