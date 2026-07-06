<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
    disableSkillPublish,
    listMcpServers,
    listSkillCatalogs,
    listSkillTools,
    publishSkillChatbot,
    regenerateSkillPublishCode,
    refreshSkillBindings,
    updateSkillPublishInfo,
    updateSkillBindings,
    updateSkillCatalog,
} from '@/api/skills';
import { alert, openModal } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import AdminSkillBindingPickerModalContent from '@/views/admin/components/skill-management/AdminSkillBindingPickerModalContent.vue';
import AdminSkillIconPickerModalContent from '@/views/admin/components/skill-management/AdminSkillIconPickerModalContent.vue';
import AdminSkillPublishEmbedModalContent from '@/views/admin/components/skill-management/AdminSkillPublishEmbedModalContent.vue';
import {
    getSkillIconColorLabel,
    getSkillIconGradientClass,
    resolveSkillIcon,
    resolveSkillIconColor,
} from '@/utils/skillVisuals';

const props = defineProps({
    skillId: {
        type: Number,
        required: true,
    },
});

const emit = defineEmits(['back']);

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const bindingSaving = ref(false);
const bindingRefreshing = ref(false);
const publishSaving = ref(false);
const publishDisabling = ref(false);
const publishRegenerating = ref(false);
const loadError = ref('');
const selectedSkill = ref(null);
const toolLibrary = ref([]);
const mcpServers = ref([]);
const selectedBoundToolNames = ref([]);
const runtimeToolsExpanded = ref(false);
const activeTab = ref('basic');

const publishedChatbotUrl = computed(() =>
    buildAbsoluteChatbotUrl(selectedSkill.value?.chatbotUrl || '')
);

const form = reactive({
    displayName: '',
    description: '',
    category: '',
    sortOrder: 0,
    visible: true,
});

const publishForm = reactive({
    appName: '',
    appDescription: '',
});

const toolMap = computed(() => {
    const map = new Map();
    for (const item of toolLibrary.value) {
        map.set(item.name, item);
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

const connectorBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'CONNECTOR_API')
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

const selectedToolItems = computed(() =>
    selectedBoundToolNames.value.map(
        name =>
            toolMap.value.get(name) || {
                name,
                displayName: name,
                description: '该工具不在当前可用目录中，可能尚未刷新或已从远端下线。',
                type: 'UNKNOWN',
                source: '',
            }
    )
);

const runtimeTools = computed(() =>
    (selectedSkill.value?.runtimeTools || []).map(tool => ({
        ...tool,
        bindingMode: 'runtime',
    }))
);

const effectiveBoundTools = computed(() =>
    selectedToolItems.value.map(tool => {
        const alsoRuntime = runtimeTools.value.some(item => item.name === tool.name);
        return {
            ...tool,
            bindingMode: alsoRuntime ? 'runtime+manual' : 'manual',
        };
    })
);

// 对已绑定工具进行分组展示
const groupedBoundTools = computed(() => {
    const groups = new Map();
    const singles = [];

    for (const tool of effectiveBoundTools.value) {
        // 识别数据集工具（source 格式：dataset:{datasetCode}）
        if (tool.type === 'DATASET_TOOL' && tool.source?.startsWith('dataset:')) {
            const groupKey = tool.source;
            if (!groups.has(groupKey)) {
                // 从 displayName 提取数据集名称（格式："数据集名 / 功能名"）
                const datasetName = tool.displayName?.split('/')[0]?.trim() || '未知数据集';
                groups.set(groupKey, {
                    type: 'group',
                    groupKey: groupKey,
                    displayName: datasetName,
                    description: `数据集工具组`,
                    children: [],
                });
            }
            groups.get(groupKey).children.push(tool);
        } else {
            // 普通工具
            singles.push({
                type: 'single',
                ...tool,
            });
        }
    }

    // 合并：普通工具 + 工具组
    return [...singles, ...Array.from(groups.values())];
});

const runtimeToolCount = computed(() => selectedSkill.value?.runtimeTools?.length || 0);

const manualToolCount = computed(() => selectedBoundToolNames.value.length);

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
        description: '适合把数据集摘要、结构和只读 SQL 查询能力追加给当前 skill。',
        tools: datasetBindableTools.value,
    },
    {
        key: 'knowledge',
        title: '知识库工具',
        description: '适合把已发布的知识库检索能力追加给当前 skill。',
        tools: knowledgeBaseBindableTools.value,
    },
    {
        key: 'lowcode',
        title: 'API 工具',
        description: '适合把已注册发布的 API 工具追加给当前 skill。',
        tools: lowcodeBindableTools.value,
    },
    {
        key: 'connectorApi',
        title: '连接器 API',
        description: '适合把连接器下已发布的 API 工具追加给当前 skill。',
        tools: connectorBindableTools.value,
    },
]);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function syncForm(skill) {
    form.displayName = skill?.displayName || '';
    form.description = skill?.description || '';
    form.category = skill?.category || '';
    form.sortOrder = Number(skill?.sortOrder || 0);
    form.visible = Boolean(skill?.visible);
    selectedBoundToolNames.value = Array.isArray(skill?.boundGlobalToolNames)
        ? [...skill.boundGlobalToolNames]
        : [];
    publishForm.appName = skill?.publishAppName || skill?.displayName || '';
    publishForm.appDescription = skill?.publishAppDescription || skill?.description || '';
    runtimeToolsExpanded.value = false;
    activeTab.value = 'basic';
}

function buildAbsoluteChatbotUrl(chatbotPath) {
    if (!chatbotPath) {
        return '';
    }
    if (/^https?:\/\//i.test(chatbotPath)) {
        return chatbotPath;
    }
    return `${window.location.origin}${chatbotPath}`;
}

function buildEmbedScriptUrl() {
    return new URL(
        'embed.min.js',
        `${window.location.origin}${import.meta.env.BASE_URL}`
    ).toString();
}

function isPublished(skill) {
    return (
        String(skill?.publishStatus || '')
            .trim()
            .toUpperCase() === 'PUBLISHED'
    );
}

function copyTextWithExecCommand(text) {
    if (!text) {
        return false;
    }
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);
    let copied = false;
    try {
        copied = document.execCommand('copy');
    } catch (error) {
        copied = false;
    }
    document.body.removeChild(textarea);
    return copied;
}

async function handleCopyChatbotUrl() {
    if (!selectedSkill.value?.chatbotUrl) {
        return;
    }
    const url = publishedChatbotUrl.value;
    if (copyTextWithExecCommand(url)) {
        await alert({
            title: '复制成功',
            message: '公开访问 URL 已复制到剪贴板。',
        });
        return;
    }
    await alert({
        title: '复制失败',
        message: '浏览器限制导致复制失败，请手动复制该地址。',
    });
}

function handleOpenPublishedChatbot() {
    if (!publishedChatbotUrl.value) {
        return;
    }
    window.open(publishedChatbotUrl.value, '_blank', 'noopener');
}

async function handleOpenPublishEmbedModal() {
    if (!selectedSkill.value?.appCode || !publishedChatbotUrl.value) {
        await alert({
            title: '无法打开嵌入配置',
            message: '当前技能尚未生成发布地址，请先完成发布。',
        });
        return;
    }
    await openModal({
        title: '嵌入到网站中',
        showClose: true,
        footer: null,
        panelClass: '!max-w-[880px]',
        content: {
            component: AdminSkillPublishEmbedModalContent,
        },
        context: {
            appCode: selectedSkill.value.appCode,
            chatbotUrl: publishedChatbotUrl.value,
            embedScriptUrl: buildEmbedScriptUrl(),
        },
    });
}

function getSkillIcon(skill) {
    return resolveSkillIcon(skill?.icon);
}

function getSkillIconColor(skill) {
    return resolveSkillIconColor(skill?.iconColor);
}

function getSkillGradient(skill) {
    return getSkillIconGradientClass(skill?.iconColor);
}

function getSkillIconColorText(skill) {
    return getSkillIconColorLabel(skill?.iconColor);
}

function hasToolBindingIssue(skill) {
    return skill?.toolBindingStatus && skill.toolBindingStatus !== 'READY';
}

function getToolBindingStatusLabel(skill) {
    switch (skill?.toolBindingStatus) {
        case 'MISSING_DEPENDENCY':
            return '工具缺失';
        case 'NEEDS_REBIND':
            return '待重绑';
        case 'UNSUPPORTED':
            return '待处理';
        default:
            return '正常';
    }
}

function getToolBindingStatusClass(skill) {
    switch (skill?.toolBindingStatus) {
        case 'MISSING_DEPENDENCY':
            return 'bg-rose-50 text-rose-600';
        case 'NEEDS_REBIND':
            return 'bg-amber-50 text-amber-700';
        case 'UNSUPPORTED':
            return 'bg-blue-50 text-primary';
        default:
            return 'bg-emerald-50 text-emerald-600';
    }
}

async function loadSkill() {
    loading.value = true;
    loadError.value = '';
    try {
        const [catalogs, tools, servers] = await Promise.all([
            listSkillCatalogs({}, handleUnauthorized),
            listSkillTools(handleUnauthorized),
            listMcpServers(handleUnauthorized),
        ]);
        const matched = Array.isArray(catalogs)
            ? catalogs.find(item => item.id === props.skillId) || null
            : null;
        if (!matched) {
            throw new Error('技能不存在或已下线');
        }
        selectedSkill.value = matched;
        toolLibrary.value = Array.isArray(tools) ? tools.filter(item => item.bindable) : [];
        mcpServers.value = Array.isArray(servers) ? servers : [];
        syncForm(matched);
    } catch (error) {
        loadError.value = error?.message || '加载技能详情失败';
    } finally {
        loading.value = false;
    }
}

async function saveMeta() {
    if (!selectedSkill.value || saving.value) {
        return;
    }
    saving.value = true;
    try {
        const updated = await updateSkillCatalog(
            selectedSkill.value.id,
            {
                displayName: form.displayName,
                description: form.description,
                category: form.category,
                sortOrder: Number(form.sortOrder || 0),
                visible: form.visible,
                icon: getSkillIcon(selectedSkill.value),
                iconColor: getSkillIconColor(selectedSkill.value),
            },
            handleUnauthorized
        );
        selectedSkill.value = {
            ...selectedSkill.value,
            ...updated,
            boundGlobalToolNames: selectedBoundToolNames.value,
        };
        syncForm(selectedSkill.value);
        await alert({
            title: '保存成功',
            message: '技能基础信息已更新。',
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '技能信息保存失败',
        });
    } finally {
        saving.value = false;
    }
}

function patchSelectedSkillPublishStatus(status) {
    if (!selectedSkill.value || !status) {
        return;
    }
    selectedSkill.value = {
        ...selectedSkill.value,
        publishStatus: status.publishStatus || selectedSkill.value.publishStatus || 'DISABLED',
        appCode: status.appCode || '',
        publishAppName: status.appName || '',
        publishAppDescription: status.appDescription || '',
        chatbotUrl: status.chatbotUrl || '',
    };
    publishForm.appName =
        selectedSkill.value.publishAppName || selectedSkill.value.displayName || '';
    publishForm.appDescription =
        selectedSkill.value.publishAppDescription || selectedSkill.value.description || '';
}

async function handlePublishSkill() {
    if (!selectedSkill.value || publishSaving.value) {
        return;
    }
    publishSaving.value = true;
    try {
        const wasPublished = isPublished(selectedSkill.value);
        const status = wasPublished
            ? await updateSkillPublishInfo(
                  selectedSkill.value.id,
                  {
                      appName: publishForm.appName,
                      appDescription: publishForm.appDescription,
                  },
                  handleUnauthorized
              )
            : await publishSkillChatbot(
                  selectedSkill.value.id,
                  {
                      appName: publishForm.appName,
                      appDescription: publishForm.appDescription,
                  },
                  handleUnauthorized
              );
        patchSelectedSkillPublishStatus(status);
        await alert({
            title: '保存成功',
            message: wasPublished
                ? '发布信息已更新。'
                : '技能已发布，可通过访问地址进入 chatbot 页面。',
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '发布信息保存失败',
        });
    } finally {
        publishSaving.value = false;
    }
}

async function handleRegeneratePublishCode() {
    if (!selectedSkill.value || publishRegenerating.value) {
        return;
    }
    publishRegenerating.value = true;
    try {
        const status = await regenerateSkillPublishCode(selectedSkill.value.id, handleUnauthorized);
        patchSelectedSkillPublishStatus(status);
        await alert({
            title: '重新生成成功',
            message: '访问地址已更新，请使用新的 URL。',
        });
    } catch (error) {
        await alert({
            title: '操作失败',
            message: error?.message || '地址重新生成失败',
        });
    } finally {
        publishRegenerating.value = false;
    }
}

async function handleDisablePublish() {
    if (!selectedSkill.value || publishDisabling.value) {
        return;
    }
    publishDisabling.value = true;
    try {
        const status = await disableSkillPublish(selectedSkill.value.id, handleUnauthorized);
        patchSelectedSkillPublishStatus(status);
        await alert({
            title: '已取消发布',
            message: '该技能的 chatbot 访问已停用。',
        });
    } catch (error) {
        await alert({
            title: '操作失败',
            message: error?.message || '取消发布失败',
        });
    } finally {
        publishDisabling.value = false;
    }
}

async function saveBindings() {
    if (!selectedSkill.value || bindingSaving.value) {
        return;
    }
    bindingSaving.value = true;
    try {
        const result = await updateSkillBindings(
            selectedSkill.value.id,
            selectedBoundToolNames.value,
            handleUnauthorized
        );
        selectedBoundToolNames.value = Array.isArray(result?.toolNames) ? result.toolNames : [];
        selectedSkill.value = {
            ...selectedSkill.value,
            boundGlobalToolNames: selectedBoundToolNames.value,
            toolBindingStatus: 'READY',
            toolBindingMessage: null,
        };
        await alert({
            title: '绑定成功',
            message: '技能工具绑定已更新。',
        });
    } catch (error) {
        await alert({
            title: '绑定失败',
            message: error?.message || '技能工具绑定失败',
        });
    } finally {
        bindingSaving.value = false;
    }
}

async function handleRefreshBindingStatus() {
    if (!selectedSkill.value || bindingRefreshing.value) {
        return;
    }
    bindingRefreshing.value = true;
    try {
        const result = await refreshSkillBindings(selectedSkill.value.id, handleUnauthorized);
        await loadSkill();
        await alert({
            title: '重新检测完成',
            message: [
                `工具绑定：共 ${result?.toolBindingSummary?.totalCount || 0} 项`,
                `已恢复 ${result?.toolBindingSummary?.restoredCount || 0} 项`,
                `缺失依赖 ${result?.toolBindingSummary?.missingDependencyCount || 0} 项`,
                `需要重绑 ${result?.toolBindingSummary?.needsRebindCount || 0} 项`,
                `不支持 ${result?.toolBindingSummary?.unsupportedCount || 0} 项`,
                result?.toolBindingMessage || '',
            ]
                .filter(Boolean)
                .join(' / '),
        });
    } catch (error) {
        await alert({
            title: '重新检测失败',
            message: error?.message || '工具绑定重新检测失败',
        });
    } finally {
        bindingRefreshing.value = false;
    }
}

function getToolTypeBadgeClass(type) {
    switch (type) {
        case 'GLOBAL':
            return 'bg-slate-100 text-slate-600';
        case 'RUNTIME':
            return 'bg-rose-50 text-rose-600';
        case 'DATASET_TOOL':
            return 'bg-emerald-50 text-emerald-600';
        case 'KNOWLEDGE_BASE_TOOL':
            return 'bg-blue-50 text-blue-600';
        case 'LOWCODE_API':
            return 'bg-amber-50 text-amber-600';
        case 'CONNECTOR_API':
            return 'bg-orange-50 text-orange-600';
        case 'MCP_REMOTE':
            return 'bg-blue-50 text-primary';
        case 'SKILL_NATIVE':
            return 'bg-blue-50 text-blue-600';
        default:
            return 'bg-slate-100 text-slate-500';
    }
}

function getBindingModeLabel(mode) {
    if (mode === 'runtime') {
        return '运行时';
    }
    if (mode === 'runtime+manual') {
        return '运行时 + 追加';
    }
    return '追加绑定';
}

function getToolTypeLabel(type) {
    switch (type) {
        case 'GLOBAL':
            return '公共工具';
        case 'RUNTIME':
            return '运行时工具';
        case 'DATASET_TOOL':
            return '数据集';
        case 'KNOWLEDGE_BASE_TOOL':
            return '知识库';
        case 'LOWCODE_API':
            return 'API';
        case 'CONNECTOR_API':
            return '连接器 API';
        case 'MCP_REMOTE':
            return 'MCP';
        case 'SKILL_NATIVE':
            return '原生能力';
        default:
            return '未识别';
    }
}

function getToolIcon(type) {
    switch (type) {
        case 'GLOBAL':
            return 'extension';
        case 'RUNTIME':
            return 'terminal';
        case 'DATASET_TOOL':
            return 'database';
        case 'KNOWLEDGE_BASE_TOOL':
            return 'menu_book';
        case 'LOWCODE_API':
            return 'api';
        case 'CONNECTOR_API':
            return 'cable';
        case 'MCP_REMOTE':
            return 'hub';
        case 'SKILL_NATIVE':
            return 'smart_toy';
        default:
            return 'build';
    }
}

function getToolIconClass(type) {
    switch (type) {
        case 'GLOBAL':
            return 'bg-slate-100 text-slate-600';
        case 'RUNTIME':
            return 'bg-rose-50 text-rose-600';
        case 'DATASET_TOOL':
            return 'bg-emerald-50 text-emerald-600';
        case 'KNOWLEDGE_BASE_TOOL':
            return 'bg-blue-50 text-blue-600';
        case 'LOWCODE_API':
            return 'bg-amber-50 text-amber-600';
        case 'CONNECTOR_API':
            return 'bg-orange-50 text-orange-600';
        case 'MCP_REMOTE':
            return 'bg-blue-50 text-primary';
        case 'SKILL_NATIVE':
            return 'bg-blue-50 text-blue-600';
        default:
            return 'bg-slate-100 text-slate-500';
    }
}

async function openBindingPicker() {
    if (!selectedSkill.value || bindingSaving.value) {
        return;
    }
    const draftSelectedToolNames = ref([...selectedBoundToolNames.value]);
    const result = await openModal({
        title: '绑定工具',
        showClose: true,
        confirmText: '确认绑定',
        cancelText: '取消',
        panelClass: '!max-w-[1480px]',
        content: {
            component: AdminSkillBindingPickerModalContent,
        },
        context: {
            get selectedToolNames() {
                return draftSelectedToolNames.value;
            },
            groups: pickerGroups.value,
            mcpServers: mcpServers.value,
            mcpServerToolMap: mcpServerToolMap.value,
            toggleTool(toolName) {
                const next = new Set(draftSelectedToolNames.value);
                if (next.has(toolName)) {
                    next.delete(toolName);
                } else {
                    next.add(toolName);
                }
                draftSelectedToolNames.value = Array.from(next);
            },
            bindServerTools(serverKey) {
                const next = new Set(draftSelectedToolNames.value);
                for (const tool of mcpServerToolMap.value.get(serverKey) || []) {
                    next.add(tool.name);
                }
                draftSelectedToolNames.value = Array.from(next);
            },
            removeServerTools(serverKey) {
                const next = new Set(draftSelectedToolNames.value);
                for (const tool of mcpServerToolMap.value.get(serverKey) || []) {
                    next.delete(tool.name);
                }
                draftSelectedToolNames.value = Array.from(next);
            },
        },
        resolveWith(context) {
            return Array.isArray(context.selectedToolNames) ? [...context.selectedToolNames] : [];
        },
    });
    if (!Array.isArray(result)) {
        return;
    }
    selectedBoundToolNames.value = result;
    await saveBindings();
}

async function openIconPicker() {
    if (!selectedSkill.value || saving.value) {
        return;
    }
    const draftIcon = ref(getSkillIcon(selectedSkill.value));
    const draftIconColor = ref(getSkillIconColor(selectedSkill.value));
    const result = await openModal({
        title: '设置技能图标',
        showClose: true,
        confirmText: '保存图标',
        cancelText: '取消',
        panelClass: '!max-w-4xl',
        content: {
            component: AdminSkillIconPickerModalContent,
        },
        context: {
            get selectedIcon() {
                return draftIcon.value;
            },
            get selectedIconColor() {
                return draftIconColor.value;
            },
            setIcon(icon) {
                draftIcon.value = resolveSkillIcon(icon);
            },
            setIconColor(iconColor) {
                draftIconColor.value = resolveSkillIconColor(iconColor);
            },
        },
        resolveWith(context) {
            return {
                icon: resolveSkillIcon(context.selectedIcon),
                iconColor: resolveSkillIconColor(context.selectedIconColor),
            };
        },
    });
    if (!result?.icon || !result?.iconColor) {
        return;
    }
    saving.value = true;
    try {
        const updated = await updateSkillCatalog(
            selectedSkill.value.id,
            {
                displayName: form.displayName,
                description: form.description,
                category: form.category,
                sortOrder: Number(form.sortOrder || 0),
                visible: form.visible,
                icon: result.icon,
                iconColor: result.iconColor,
            },
            handleUnauthorized
        );
        selectedSkill.value = {
            ...selectedSkill.value,
            ...updated,
            boundGlobalToolNames: selectedBoundToolNames.value,
        };
        syncForm(selectedSkill.value);
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '技能图标更新失败',
        });
    } finally {
        saving.value = false;
    }
}

watch(
    () => props.skillId,
    () => {
        loadSkill();
    }
);

onMounted(() => {
    loadSkill();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-wrap items-center justify-between gap-4">
                <div class="min-w-0 flex-1">
                    <template v-if="selectedSkill">
                        <div class="flex items-start gap-4">
                            <button
                                type="button"
                                class="group flex h-12 w-12 shrink-0 items-center justify-center rounded-[18px] text-white shadow-sm transition hover:scale-[1.03]"
                                :class="getSkillGradient(selectedSkill)"
                                @click="openIconPicker"
                            >
                                <span class="material-symbols-outlined text-[24px]">{{
                                    getSkillIcon(selectedSkill)
                                }}</span>
                            </button>
                            <div class="min-w-0 flex-1">
                                <div class="flex flex-wrap items-center gap-3">
                                    <h2
                                        class="truncate text-3xl font-bold tracking-tight text-slate-900"
                                    >
                                        {{ selectedSkill.displayName }}
                                    </h2>
                                    <span
                                        class="rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-600"
                                    >
                                        {{ selectedSkill.category || '通用能力' }}
                                    </span>
                                    <span
                                        class="rounded-full px-3 py-1 text-xs font-semibold"
                                        :class="
                                            selectedSkill.visible
                                                ? 'bg-emerald-50 text-emerald-600'
                                                : 'bg-slate-100 text-slate-500'
                                        "
                                    >
                                        {{ selectedSkill.visible ? '前台可见' : '前台隐藏' }}
                                    </span>
                                    <span
                                        v-if="hasToolBindingIssue(selectedSkill)"
                                        class="rounded-full px-3 py-1 text-xs font-semibold"
                                        :class="getToolBindingStatusClass(selectedSkill)"
                                    >
                                        {{ getToolBindingStatusLabel(selectedSkill) }}
                                    </span>
                                    <span
                                        v-if="isPublished(selectedSkill)"
                                        class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-600"
                                    >
                                        已发布
                                    </span>
                                </div>
                                <p
                                    class="mt-2 line-clamp-2 max-w-4xl text-sm leading-6 text-slate-500"
                                >
                                    {{ selectedSkill.description }}
                                </p>
                                <p
                                    v-if="
                                        hasToolBindingIssue(selectedSkill) &&
                                        selectedSkill.toolBindingMessage
                                    "
                                    class="mt-2 inline-flex rounded-2xl border border-rose-100 bg-rose-50 px-3 py-2 text-xs leading-5 text-rose-700"
                                >
                                    {{ selectedSkill.toolBindingMessage }}
                                </p>
                                <p class="mt-2 text-xs font-medium text-slate-400">
                                    点击左侧图标可修改主图标和颜色
                                </p>
                                <div class="mt-3 flex flex-wrap items-center gap-3 text-sm">
                                    <span
                                        class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-slate-700"
                                    >
                                        已绑定工具 {{ groupedBoundTools.length }}
                                    </span>
                                    <span
                                        v-if="selectedSkill.version"
                                        class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-2 text-amber-700"
                                    >
                                        版本 v{{ selectedSkill.version }}
                                    </span>
                                    <span
                                        v-if="selectedSkill.author"
                                        class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-2 text-emerald-700"
                                    >
                                        作者 {{ selectedSkill.author }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </template>
                    <template v-else>
                        <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">
                            技能详情
                        </h2>
                        <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                            这里维护技能展示信息，并统一查看当前已绑定工具。需要调整绑定时，点击右侧按钮通过弹窗选择即可。
                        </p>
                    </template>
                </div>
                <button
                    type="button"
                    class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                    @click="emit('back')"
                >
                    返回列表
                </button>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto p-6">
            <p
                v-if="loadError"
                class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div
                v-if="loading"
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                加载中...
            </div>

            <div v-else-if="selectedSkill" class="space-y-6">
                <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-6 flex gap-8 border-b border-slate-200">
                        <button
                            type="button"
                            class="pb-4 text-sm font-medium transition-colors"
                            :class="
                                activeTab === 'basic'
                                    ? 'border-b-2 border-blue-700 translate-y-[1px] text-blue-700'
                                    : 'text-slate-500 hover:text-slate-800'
                            "
                            @click="activeTab = 'basic'"
                        >
                            基础配置
                        </button>
                        <button
                            type="button"
                            class="pb-4 text-sm font-medium transition-colors"
                            :class="
                                activeTab === 'tools'
                                    ? 'border-b-2 border-blue-700 translate-y-[1px] text-blue-700'
                                    : 'text-slate-500 hover:text-slate-800'
                            "
                            @click="activeTab = 'tools'"
                        >
                            工具集成
                        </button>
                        <button
                            type="button"
                            class="pb-4 text-sm font-medium transition-colors"
                            :class="
                                activeTab === 'publish'
                                    ? 'border-b-2 border-blue-700 translate-y-[1px] text-blue-700'
                                    : 'text-slate-500 hover:text-slate-800'
                            "
                            @click="activeTab = 'publish'"
                        >
                            发布应用
                        </button>
                    </div>

                    <article v-if="activeTab === 'basic'">
                        <div class="mb-5 flex items-center justify-between">
                            <div>
                                <h3 class="text-xl font-bold text-slate-900">基础信息</h3>
                                <p class="mt-1 text-sm text-slate-500">
                                    编辑技能的展示名称、分类、描述和前台可见性。
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="saving"
                                @click="saveMeta"
                            >
                                {{ saving ? '保存中...' : '保存信息' }}
                            </button>
                        </div>

                        <div class="grid gap-4 md:grid-cols-2">
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">展示名称</span>
                                <input
                                    v-model.trim="form.displayName"
                                    type="text"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">业务能力分类</span>
                                <input
                                    v-model.trim="form.category"
                                    type="text"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600 md:col-span-2">
                                <span class="font-medium">展示描述</span>
                                <textarea
                                    v-model.trim="form.description"
                                    rows="5"
                                    class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">排序值</span>
                                <input
                                    v-model.number="form.sortOrder"
                                    type="number"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label
                                class="flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700"
                            >
                                <input
                                    v-model="form.visible"
                                    type="checkbox"
                                    class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600"
                                />
                                <span class="font-medium">前台技能市场可见</span>
                            </label>
                        </div>

                        <div class="mt-6 rounded-[24px] border border-slate-200 bg-slate-50 p-5">
                            <div
                                class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between"
                            >
                                <div>
                                    <h4 class="text-base font-bold text-slate-900">技能元信息</h4>
                                    <p class="mt-1 text-sm text-slate-500">
                                        来自平台侧技能目录表。版本和作者来自初始化或导入包，图标与颜色可点击页面顶部图标进行调整。
                                    </p>
                                </div>
                            </div>

                            <div class="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-5">
                                <article
                                    class="rounded-2xl border border-slate-200 bg-white px-4 py-4"
                                >
                                    <p
                                        class="text-xs font-semibold tracking-[0.2em] text-slate-400"
                                    >
                                        版本
                                    </p>
                                    <p class="mt-2 text-sm font-semibold text-slate-900">
                                        {{
                                            selectedSkill.version
                                                ? `v${selectedSkill.version}`
                                                : '-'
                                        }}
                                    </p>
                                </article>
                                <article
                                    class="rounded-2xl border border-slate-200 bg-white px-4 py-4"
                                >
                                    <p
                                        class="text-xs font-semibold tracking-[0.2em] text-slate-400"
                                    >
                                        作者
                                    </p>
                                    <p class="mt-2 text-sm font-semibold text-slate-900">
                                        {{ selectedSkill.author || '-' }}
                                    </p>
                                </article>
                                <article
                                    class="rounded-2xl border border-slate-200 bg-white px-4 py-4"
                                >
                                    <p
                                        class="text-xs font-semibold tracking-[0.2em] text-slate-400"
                                    >
                                        图标
                                    </p>
                                    <p class="mt-2 text-sm font-semibold text-slate-900">
                                        {{ getSkillIcon(selectedSkill) || '-' }}
                                    </p>
                                </article>
                                <article
                                    class="rounded-2xl border border-slate-200 bg-white px-4 py-4"
                                >
                                    <p
                                        class="text-xs font-semibold tracking-[0.2em] text-slate-400"
                                    >
                                        图标颜色
                                    </p>
                                    <div class="mt-2 flex items-center gap-3">
                                        <span
                                            class="h-8 w-8 rounded-xl"
                                            :class="getSkillGradient(selectedSkill)"
                                        />
                                        <p class="text-sm font-semibold text-slate-900">
                                            {{ getSkillIconColorText(selectedSkill) }}
                                        </p>
                                    </div>
                                </article>
                                <article
                                    class="rounded-2xl border border-slate-200 bg-white px-4 py-4"
                                >
                                    <p
                                        class="text-xs font-semibold tracking-[0.2em] text-slate-400"
                                    >
                                        来源
                                    </p>
                                    <p class="mt-2 text-sm font-semibold text-slate-900">
                                        {{ selectedSkill.source || '-' }}
                                    </p>
                                </article>
                            </div>
                        </div>
                    </article>

                    <section v-else-if="activeTab === 'tools'">
                        <div
                            v-if="hasToolBindingIssue(selectedSkill)"
                            class="mb-5 rounded-[24px] border border-rose-200 bg-rose-50 px-5 py-4"
                        >
                            <div class="flex flex-wrap items-center justify-between gap-3">
                                <div class="flex flex-wrap items-center gap-3">
                                    <span
                                        class="rounded-full px-3 py-1 text-xs font-semibold"
                                        :class="getToolBindingStatusClass(selectedSkill)"
                                    >
                                        {{ getToolBindingStatusLabel(selectedSkill) }}
                                    </span>
                                    <p class="text-sm text-rose-700">
                                        {{
                                            selectedSkill.toolBindingMessage ||
                                            '导入时有工具绑定未完全恢复，请检查并重新绑定。'
                                        }}
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    class="rounded-xl border border-rose-200 bg-white px-4 py-2 text-sm font-semibold text-rose-700 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="bindingRefreshing"
                                    @click="handleRefreshBindingStatus"
                                >
                                    {{ bindingRefreshing ? '检测中...' : '重新检测绑定' }}
                                </button>
                            </div>
                            <div
                                v-if="
                                    Array.isArray(selectedSkill.toolBindingIssues) &&
                                    selectedSkill.toolBindingIssues.length
                                "
                                class="mt-4 rounded-2xl border border-white/80 bg-white/70 px-4 py-3"
                            >
                                <p class="text-xs font-semibold tracking-[0.16em] text-rose-500">
                                    未恢复工具
                                </p>
                                <div class="mt-3 space-y-2">
                                    <div
                                        v-for="issue in selectedSkill.toolBindingIssues"
                                        :key="`${issue.toolName}-${issue.restoreStatus}`"
                                        class="rounded-xl border border-rose-100 bg-white px-3 py-2"
                                    >
                                        <div class="flex flex-wrap items-center gap-2">
                                            <span class="text-sm font-semibold text-slate-900">
                                                {{ issue.toolName || '-' }}
                                            </span>
                                            <span
                                                class="rounded-full bg-rose-100 px-2 py-0.5 text-[11px] font-semibold text-rose-600"
                                            >
                                                {{ issue.restoreStatus || 'UNKNOWN' }}
                                            </span>
                                        </div>
                                        <p class="mt-1 text-xs leading-5 text-slate-600">
                                            {{ issue.message || '该工具当前未完成恢复。' }}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="mb-5 flex items-center justify-between gap-4">
                            <div>
                                <h3 class="text-xl font-bold text-slate-900">已绑定工具</h3>
                                <p class="mt-1 text-sm text-slate-500">
                                    这里展示当前技能显式绑定的工具。运行时工具可在下方折叠区查看。
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="bindingSaving"
                                @click="openBindingPicker"
                            >
                                {{ bindingSaving ? '保存中...' : '绑定工具' }}
                            </button>
                        </div>

                        <div v-if="groupedBoundTools.length" class="grid gap-4 md:grid-cols-2">
                            <template
                                v-for="item in groupedBoundTools"
                                :key="item.name || item.groupKey"
                            >
                                <!-- 普通工具卡片 -->
                                <article
                                    v-if="item.type === 'single'"
                                    class="group rounded-[24px] border border-slate-200 bg-slate-50 p-5 transition hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5"
                                >
                                    <div class="flex items-start justify-between gap-4">
                                        <div
                                            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl"
                                            :class="getToolIconClass(item.type)"
                                        >
                                            <span class="material-symbols-outlined text-[22px]">
                                                {{ getToolIcon(item.type) }}
                                            </span>
                                        </div>
                                        <div class="min-w-0 flex-1">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h4
                                                    class="truncate text-base font-bold text-slate-900"
                                                >
                                                    {{ item.displayName || item.name }}
                                                </h4>
                                                <span
                                                    class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                                    :class="getToolTypeBadgeClass(item.type)"
                                                >
                                                    {{ getToolTypeLabel(item.type) }}
                                                </span>
                                                <span
                                                    class="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                                >
                                                    {{ getBindingModeLabel(item.bindingMode) }}
                                                </span>
                                            </div>
                                            <p class="mt-2 text-xs text-slate-500">
                                                {{ getBindingModeLabel(item.bindingMode) }}
                                            </p>
                                        </div>
                                    </div>
                                </article>

                                <!-- 工具组卡片（数据集工具） -->
                                <article
                                    v-else-if="item.type === 'group'"
                                    class="rounded-[24px] border-2 border-amber-200 bg-gradient-to-br from-amber-50 to-orange-50 p-5 transition hover:shadow-md"
                                >
                                    <div class="flex items-start justify-between gap-4">
                                        <div
                                            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-amber-500 via-orange-500 to-yellow-400 text-white shadow-lg"
                                        >
                                            <span class="material-symbols-outlined text-[22px]">
                                                database
                                            </span>
                                        </div>
                                        <div class="min-w-0 flex-1">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h4
                                                    class="truncate text-base font-bold text-slate-900"
                                                >
                                                    {{ item.displayName }}
                                                </h4>
                                                <span
                                                    class="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-700"
                                                >
                                                    数据集工具组
                                                </span>
                                                <span
                                                    class="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-700"
                                                >
                                                    {{ item.children.length }}个子工具
                                                </span>
                                            </div>
                                            <p class="mt-2 text-xs text-slate-500">
                                                {{ item.description }}
                                            </p>
                                            <div class="mt-3 flex flex-wrap gap-1">
                                                <span
                                                    v-for="child in item.children"
                                                    :key="child.name"
                                                    class="rounded-full bg-white px-2 py-0.5 text-[10px] font-medium text-slate-600"
                                                >
                                                    {{
                                                        child.displayName?.split('/')[1]?.trim() ||
                                                        child.displayName ||
                                                        child.name
                                                    }}
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </article>
                            </template>
                        </div>
                        <div
                            v-else
                            class="rounded-[24px] border border-dashed border-slate-200 bg-slate-50 px-6 py-10 text-center text-sm text-slate-400"
                        >
                            当前还没有已绑定工具，点击右上角“绑定工具”开始添加。
                        </div>

                        <div class="mt-6 rounded-[24px] border border-slate-200 bg-slate-50">
                            <button
                                type="button"
                                class="flex w-full items-center justify-between gap-4 px-5 py-4 text-left"
                                @click="runtimeToolsExpanded = !runtimeToolsExpanded"
                            >
                                <div>
                                    <p class="text-sm font-semibold text-slate-900">运行时工具</p>
                                    <p class="mt-1 text-xs leading-6 text-slate-500">
                                        默认不展示，可展开查看当前 skill
                                        自带或系统运行时提供的工具。
                                    </p>
                                </div>
                                <div class="flex items-center gap-3">
                                    <span
                                        class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-600"
                                    >
                                        {{ runtimeTools.length }} 项
                                    </span>
                                    <span
                                        class="material-symbols-outlined text-slate-400 transition-transform"
                                        :class="runtimeToolsExpanded ? 'rotate-180' : ''"
                                    >
                                        expand_more
                                    </span>
                                </div>
                            </button>

                            <div
                                v-if="runtimeToolsExpanded"
                                class="border-t border-slate-200 px-5 py-5"
                            >
                                <div v-if="runtimeTools.length" class="grid gap-4 md:grid-cols-2">
                                    <article
                                        v-for="tool in runtimeTools"
                                        :key="`runtime-${tool.name}`"
                                        class="rounded-[20px] border border-slate-200 bg-white p-4"
                                    >
                                        <div class="flex items-start gap-4">
                                            <div
                                                class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                                                :class="getToolIconClass(tool.type)"
                                            >
                                                <span class="material-symbols-outlined text-[20px]">
                                                    {{ getToolIcon(tool.type) }}
                                                </span>
                                            </div>
                                            <div class="min-w-0 flex-1">
                                                <div class="flex flex-wrap items-center gap-2">
                                                    <h4
                                                        class="truncate text-sm font-bold text-slate-900"
                                                    >
                                                        {{ tool.displayName || tool.name }}
                                                    </h4>
                                                    <span
                                                        class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                                        :class="getToolTypeBadgeClass(tool.type)"
                                                    >
                                                        {{ getToolTypeLabel(tool.type) }}
                                                    </span>
                                                    <span
                                                        class="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-blue-600"
                                                    >
                                                        运行时
                                                    </span>
                                                </div>
                                                <p class="mt-2 text-xs text-slate-500">
                                                    {{ getToolTypeLabel(tool.type) }}
                                                </p>
                                            </div>
                                        </div>
                                    </article>
                                </div>
                                <div
                                    v-else
                                    class="rounded-[20px] border border-dashed border-slate-200 bg-white px-6 py-8 text-center text-sm text-slate-400"
                                >
                                    当前没有可展示的运行时工具。
                                </div>
                            </div>
                        </div>
                    </section>

                    <section v-else class="space-y-5">
                        <div class="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <h3 class="text-xl font-bold text-slate-900">发布应用</h3>
                                <p class="mt-1 text-sm text-slate-500">
                                    发布后可通过固定 URL
                                    访问页面。可更新信息、重新生成地址或取消发布。
                                </p>
                            </div>
                            <div class="flex items-center gap-3">
                                <button
                                    v-if="isPublished(selectedSkill)"
                                    type="button"
                                    class="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="publishDisabling"
                                    @click="handleDisablePublish"
                                >
                                    {{ publishDisabling ? '处理中...' : '取消发布' }}
                                </button>
                                <button
                                    type="button"
                                    class="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="publishSaving"
                                    @click="handlePublishSkill"
                                >
                                    {{
                                        publishSaving
                                            ? '保存中...'
                                            : isPublished(selectedSkill)
                                              ? '更新发布'
                                              : '发布'
                                    }}
                                </button>
                            </div>
                        </div>

                        <div class="grid gap-4 md:grid-cols-2">
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">应用名称（可选）</span>
                                <input
                                    v-model.trim="publishForm.appName"
                                    type="text"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                    placeholder="未填写时默认使用技能展示名称"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600 md:col-span-2">
                                <span class="font-medium">应用描述（可选）</span>
                                <textarea
                                    v-model.trim="publishForm.appDescription"
                                    rows="4"
                                    class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                    placeholder="未填写时默认使用技能描述"
                                />
                            </label>
                        </div>

                        <div
                            v-if="isPublished(selectedSkill) && selectedSkill.chatbotUrl"
                            class="rounded-2xl border border-slate-200 bg-white p-4"
                        >
                            <p class="text-sm font-semibold text-slate-700">公开访问 URL</p>
                            <div
                                class="mt-2 flex items-center gap-2 rounded-xl bg-slate-100 px-3 py-2.5"
                            >
                                <p
                                    class="min-w-0 flex-1 truncate text-sm text-slate-700"
                                    :title="publishedChatbotUrl"
                                >
                                    {{ publishedChatbotUrl }}
                                </p>
                                <button
                                    type="button"
                                    class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-200 hover:text-slate-700"
                                    title="复制地址"
                                    @click="handleCopyChatbotUrl"
                                >
                                    <span class="material-symbols-outlined text-[18px]"
                                        >content_copy</span
                                    >
                                </button>
                                <button
                                    type="button"
                                    class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-200 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="publishRegenerating"
                                    :title="publishRegenerating ? '更新中...' : '更新地址'"
                                    @click="handleRegeneratePublishCode"
                                >
                                    <span class="material-symbols-outlined text-[18px]">
                                        {{ publishRegenerating ? 'sync' : 'autorenew' }}
                                    </span>
                                </button>
                            </div>
                            <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 px-1">
                                <button
                                    type="button"
                                    class="inline-flex items-center gap-0.5 text-[11px] font-medium text-slate-600 transition hover:text-slate-900"
                                    @click="handleOpenPublishedChatbot"
                                >
                                    <span class="material-symbols-outlined text-[12px]"
                                        >open_in_new</span
                                    >
                                    <span>启动</span>
                                </button>
                                <button
                                    type="button"
                                    class="inline-flex items-center gap-0.5 text-[11px] font-medium text-slate-600 transition hover:text-slate-900"
                                    @click="handleOpenPublishEmbedModal"
                                >
                                    <span class="material-symbols-outlined text-[12px]"
                                        >inventory_2</span
                                    >
                                    <span>嵌入</span>
                                </button>
                            </div>
                        </div>
                    </section>
                </section>
            </div>
        </div>
    </section>
</template>
