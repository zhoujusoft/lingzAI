<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { listMcpServers, listSkillTools } from '@/api/skills';
import { getSkillStudioProjectSettings, updateSkillStudioProjectSettings } from '@/api/skillstudio';
import AdminSkillBindingPickerModalContent from '@/views/admin/components/skill-management/AdminSkillBindingPickerModalContent.vue';

const TOOL_TYPE_LABELS = {
    GLOBAL: '公共工具',
    RUNTIME: '运行时工具',
    LOWCODE_API: '低代码 API',
    DATASET_TOOL: '数据集工具',
    KNOWLEDGE_BASE_TOOL: '知识库工具',
    MCP_REMOTE: 'MCP 远程工具',
};

const props = defineProps({
    projectId: {
        type: Number,
        required: true,
    },
    refreshToken: {
        type: [String, Number],
        default: 0,
    },
});

const emit = defineEmits(['settings-loaded', 'settings-updated', 'unauthorized']);

const loading = ref(false);
const saving = ref(false);
const error = ref('');
const saveMessage = ref('');
const hintText = ref('');
const constraintText = ref('');
const toolLibrary = ref([]);
const mcpServers = ref([]);
const bindings = ref([]);

const selectedBindingCount = computed(() => bindings.value.length);
const toolMap = computed(() => {
    const map = new Map();
    for (const tool of toolLibrary.value) {
        if (tool?.name) {
            map.set(tool.name, tool);
        }
    }
    return map;
});

const globalBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'GLOBAL')
);

const datasetBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'DATASET_TOOL')
);

const knowledgeBaseBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'KNOWLEDGE_BASE_TOOL')
);

const lowcodeBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'LOWCODE_API')
);

const mcpServerToolMap = computed(() => {
    const map = new Map();
    for (const server of mcpServers.value) {
        map.set(server.serverKey, []);
    }
    for (const tool of toolLibrary.value) {
        if (tool.type !== 'MCP_REMOTE' || !tool.source?.startsWith('mcp:')) {
            continue;
        }
        const serverKey = tool.source.slice(4);
        const list = map.get(serverKey) || [];
        list.push(tool);
        map.set(serverKey, list);
    }
    return map;
});

const pickerGroups = computed(() => [
    {
        key: 'global',
        title: '公共工具',
        description: '适合通用文件、脚本、宿主级操作。',
        tools: globalBindableTools.value,
    },
    {
        key: 'dataset',
        title: '数据集工具',
        description: '适合把数据集摘要、结构和只读 SQL 查询能力追加给当前项目。',
        tools: datasetBindableTools.value,
    },
    {
        key: 'knowledge',
        title: '知识库工具',
        description: '适合把已发布的知识库检索能力追加给当前项目。',
        tools: knowledgeBaseBindableTools.value,
    },
    {
        key: 'lowcode',
        title: 'API 工具',
        description: '适合把已注册发布的 API 工具追加给当前项目。',
        tools: lowcodeBindableTools.value,
    },
]);

watch(
    () => props.projectId,
    () => {
        loadAll();
    }
);

watch(
    () => props.refreshToken,
    () => {
        if (!props.projectId) {
            return;
        }
        loadSettings();
    }
);

onMounted(() => {
    loadAll();
});

async function loadAll() {
    await Promise.all([loadToolLibrary(), loadSettings()]);
}

async function loadToolLibrary() {
    try {
        const [tools, servers] = await Promise.all([
            listSkillTools(handleUnauthorized),
            listMcpServers(handleUnauthorized),
        ]);
        toolLibrary.value = Array.isArray(tools) ? tools.filter(item => item?.bindable) : [];
        mcpServers.value = Array.isArray(servers) ? servers : [];
    } catch (requestError) {
        error.value = requestError?.message || '工具库加载失败';
    }
}

async function loadSettings() {
    if (!props.projectId) {
        return;
    }
    loading.value = true;
    error.value = '';
    try {
        const data = await getSkillStudioProjectSettings(props.projectId, handleUnauthorized);
        applySettings(data);
        emit('settings-loaded', data);
    } catch (requestError) {
        error.value = requestError?.message || '项目设置加载失败';
    } finally {
        loading.value = false;
    }
}

function handleUnauthorized() {
    emit('unauthorized');
}

function applySettings(data) {
    const normalized = data && typeof data === 'object' ? data : {};
    hintText.value = Array.isArray(normalized.projectHints)
        ? normalized.projectHints.join('\n')
        : '';
    constraintText.value = Array.isArray(normalized.projectConstraints)
        ? normalized.projectConstraints.join('\n')
        : '';
    bindings.value = (Array.isArray(normalized.bindings) ? normalized.bindings : []).map(item => ({
        toolName: String(item?.toolName || '').trim(),
        enabled: item?.enabled !== false,
        businessPurpose: String(item?.businessPurpose || '').trim(),
        triggerHintsText: Array.isArray(item?.triggerHints) ? item.triggerHints.join(', ') : '',
        priority: Number(item?.priority || 100),
    }));
}

function nextDefaultPriority() {
    if (!bindings.value.length) {
        return 100;
    }
    const maxValue = Math.max(...bindings.value.map(item => Number(item.priority || 100)));
    return Math.max(10, maxValue + 10);
}

function syncBindingsFromToolNames(toolNames = []) {
    const previousMap = new Map(bindings.value.map(item => [item.toolName, item]));
    const nextBindings = [];
    for (const toolName of toolNames) {
        const normalized = String(toolName || '').trim();
        if (!normalized) {
            continue;
        }
        const previous = previousMap.get(normalized);
        nextBindings.push(
            previous || {
                toolName: normalized,
                enabled: true,
                businessPurpose: '',
                triggerHintsText: '',
                priority: nextDefaultPriority(),
            }
        );
    }
    bindings.value = nextBindings;
}

function toggleTool(toolName) {
    const next = new Set(bindings.value.map(item => item.toolName));
    if (next.has(toolName)) {
        next.delete(toolName);
    } else {
        next.add(toolName);
    }
    syncBindingsFromToolNames(Array.from(next));
}

function bindServerTools(serverKey) {
    const next = new Set(bindings.value.map(item => item.toolName));
    for (const tool of mcpServerToolMap.value.get(serverKey) || []) {
        next.add(tool.name);
    }
    syncBindingsFromToolNames(Array.from(next));
}

function removeServerTools(serverKey) {
    const next = new Set(bindings.value.map(item => item.toolName));
    for (const tool of mcpServerToolMap.value.get(serverKey) || []) {
        next.delete(tool.name);
    }
    syncBindingsFromToolNames(Array.from(next));
}

const bindingPickerContext = computed(() => ({
    get selectedToolNames() {
        return bindings.value.map(item => item.toolName);
    },
    groups: pickerGroups.value,
    mcpServers: mcpServers.value,
    mcpServerToolMap: mcpServerToolMap.value,
    toggleTool,
    bindServerTools,
    removeServerTools,
}));

function splitLines(value) {
    return String(value || '')
        .split(/\n+/)
        .map(item => item.trim())
        .filter(Boolean);
}

function splitHints(value) {
    return String(value || '')
        .split(/[\n,，]+/)
        .map(item => item.trim())
        .filter(Boolean);
}

async function saveSettings() {
    if (!props.projectId || saving.value) {
        return;
    }
    saving.value = true;
    error.value = '';
    saveMessage.value = '';
    try {
        const payload = {
            projectHints: splitLines(hintText.value),
            projectConstraints: splitLines(constraintText.value),
            bindings: bindings.value.map(item => ({
                toolName: item.toolName,
                enabled: item.enabled !== false,
                businessPurpose: String(item.businessPurpose || '').trim(),
                triggerHints: splitHints(item.triggerHintsText),
                priority: Number(item.priority || 100),
            })),
        };
        const data = await updateSkillStudioProjectSettings(
            props.projectId,
            payload,
            handleUnauthorized
        );
        applySettings(data);
        saveMessage.value = data?.needsRegenerate
            ? '设置已保存，建议重新生成技能草稿。'
            : '设置已保存。';
        emit('settings-updated', data);
    } catch (requestError) {
        error.value = requestError?.message || '设置保存失败';
    } finally {
        saving.value = false;
    }
}
</script>

<template>
    <div class="settings-panel custom-scrollbar h-full overflow-y-auto px-5 py-5">
        <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div>
                <div class="settings-title">项目设置</div>
                <div class="settings-subtitle">
                    绑定技能工坊项目可用工具，并补充本项目的生成提示与约束。
                </div>
            </div>
            <button class="settings-save-btn" :disabled="saving || loading" @click="saveSettings">
                {{ saving ? '保存中...' : '保存设置' }}
            </button>
        </div>

        <div v-if="error" class="settings-banner settings-banner-error">{{ error }}</div>
        <div v-else-if="saveMessage" class="settings-banner settings-banner-success">
            {{ saveMessage }}
        </div>

        <div v-if="loading" class="settings-placeholder">正在加载项目设置...</div>
        <template v-else>
            <section class="settings-card">
                <div class="mb-3 flex items-center justify-between gap-3">
                    <div>
                        <div class="settings-card-title">工具绑定</div>
                        <div class="settings-card-subtitle">
                            已选择
                            {{ selectedBindingCount }}
                            个工具。这里直接展示与技能管理一致的绑定器内容，绑定状态在工具卡片内直接体现。
                        </div>
                    </div>
                </div>
                <div class="binding-picker-inline">
                    <AdminSkillBindingPickerModalContent :context="bindingPickerContext" />
                </div>
            </section>
        </template>
    </div>
</template>

<style scoped>
.settings-panel {
    background: #ffffff;
}

.settings-title {
    color: #0f172a;
    font-size: 16px;
    font-weight: 700;
}

.settings-subtitle {
    margin-top: 4px;
    color: #64748b;
    font-size: 12px;
    line-height: 1.6;
}

.settings-save-btn {
    border: 0;
    border-radius: 10px;
    background: #111827;
    color: #ffffff;
    font-size: 13px;
    font-weight: 600;
    padding: 9px 16px;
    cursor: pointer;
}

.settings-save-btn:disabled {
    cursor: not-allowed;
    opacity: 0.65;
}

.settings-banner {
    margin-bottom: 16px;
    border-radius: 12px;
    padding: 10px 12px;
    font-size: 12px;
    line-height: 1.6;
}

.settings-banner-error {
    background: #fff1f2;
    color: #be123c;
}

.settings-banner-success {
    background: #ecfdf5;
    color: #047857;
}

.settings-card {
    margin-bottom: 16px;
    border: 1px solid #e5e7eb;
    border-radius: 16px;
    background: #ffffff;
    padding: 16px;
}

.settings-card-title {
    color: #0f172a;
    font-size: 13px;
    font-weight: 700;
}

.settings-card-subtitle {
    margin-top: 4px;
    color: #64748b;
    font-size: 12px;
    line-height: 1.6;
}

.field-input {
    width: 100%;
    border: 1px solid #dbe2ea;
    border-radius: 10px;
    background: #ffffff;
    color: #0f172a;
    font-size: 13px;
    line-height: 1.6;
    padding: 10px 12px;
    transition:
        border-color 0.2s ease,
        box-shadow 0.2s ease;
}

.field-input:focus {
    outline: none;
    border-color: #60a5fa;
    box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.14);
}

.binding-picker-inline {
    margin-top: 8px;
}

.binding-picker-inline :deep(.max-h-\[78vh\]) {
    max-height: none;
    border: 1px solid #e2e8f0;
}

.binding-picker-inline :deep(.h-\[calc\(78vh-97px\)\]) {
    height: auto;
    min-height: 720px;
}

.settings-placeholder,
.settings-empty {
    color: #64748b;
    font-size: 13px;
    line-height: 1.7;
}
</style>
