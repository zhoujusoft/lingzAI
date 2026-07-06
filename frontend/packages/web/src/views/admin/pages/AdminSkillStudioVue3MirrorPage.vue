<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { marked } from 'marked';
import { useRoute, useRouter } from 'vue-router';
import {
    getSkillStudioProject,
    getSkillStudioProjectFileContent,
    getSkillStudioProjectSettings,
    listSkillStudioProjectFiles,
    listSkillStudioProjectSessions,
    publishSkillStudioProject,
} from '@/api/skillstudio';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import SkillStudioMirrorFileTreeNode from '@/views/admin/components/skillstudio/SkillStudioMirrorFileTreeNode.vue';
import SkillStudioCodeEditor from '@/views/admin/components/skillstudio/SkillStudioCodeEditor.vue';
import SkillStudioProjectSettingsPanel from '@/views/admin/components/skillstudio/SkillStudioProjectSettingsPanel.vue';
import FrontChatWorkspace from '@/views/front/components/front-chat/FrontChatWorkspace.vue';
import {
    createSkillStudioProjectChatAdapter,
    createSkillStudioProjectPreviewChatAdapter,
} from '@/views/admin/components/skillstudio/adapters/skillStudioProjectChatAdapter';

const PREVIEW_TAB_PATH = '__preview__';
const SETTINGS_TAB_PATH = '__settings__';

const props = defineProps({
    projectId: {
        type: Number,
        default: 1,
    },
});

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const loadError = ref('');
const project = ref(null);
const sessions = ref([]);
const files = ref([]);
const selectedFilePath = ref('');
const selectedFileContent = ref('');
const editableFileContent = ref('');
const fileTreeVisible = ref(true);
const projectInfoVisible = ref(false);
const fileViewMode = ref('edit');
const rightActiveTab = ref(PREVIEW_TAB_PATH);
const bootstrapRunning = ref(false);
const autoBootstrapped = ref(false);
const chatWorkspaceRef = ref(null);
const publishRunning = ref(false);
const publishMessage = ref('');
const publishError = ref('');
const settingsMeta = ref({
    needsRegenerate: false,
    toolSettingsDigest: '',
    lastGeneratedToolDigest: '',
});
const settingsRefreshToken = ref(0);
const chatAdapter = computed(() => createSkillStudioProjectChatAdapter(props.projectId));
const previewChatAdapter = computed(() =>
    createSkillStudioProjectPreviewChatAdapter(props.projectId)
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function inferToken(name) {
    const lower = String(name || '').toLowerCase();
    if (lower.endsWith('.md')) return 'M↓';
    if (lower.endsWith('.py')) return '🐍';
    if (lower.endsWith('.skill') || lower.endsWith('.coze')) return '⬡';
    if (lower.endsWith('.gitignore')) return '⬥';
    if (lower.endsWith('.json')) return '{}';
    if (lower.endsWith('.yaml') || lower.endsWith('.yml')) return 'Y';
    return '•';
}

function inferTokenClass(name) {
    const lower = String(name || '').toLowerCase();
    if (lower.endsWith('.md')) return 'token-md';
    if (lower.endsWith('.py')) return 'token-py';
    if (lower.endsWith('.skill') || lower.endsWith('.coze')) return 'token-coze';
    if (lower.endsWith('.gitignore')) return 'token-git';
    if (lower.endsWith('.json')) return 'token-json';
    return 'token-default';
}

function decorateTreeNodes(nodes = []) {
    return (Array.isArray(nodes) ? nodes : []).map(node => {
        const name = String(node?.name || '');
        const type = String(node?.type || 'file');
        return {
            ...node,
            key: node?.key || `${type}:${node?.path || name}`,
            token: type === 'file' ? inferToken(name) : undefined,
            tokenClass: type === 'file' ? inferTokenClass(name) : undefined,
            children: type === 'folder' ? decorateTreeNodes(node?.children || []) : [],
        };
    });
}

function collectFileNodes(nodes = []) {
    const output = [];
    (Array.isArray(nodes) ? nodes : []).forEach(node => {
        if (String(node?.type || '') === 'folder') {
            output.push(...collectFileNodes(node?.children || []));
            return;
        }
        output.push(node);
    });
    return output;
}

function classifyLine(content) {
    if (!content.trim()) return 'empty';
    if (content.startsWith('#')) return 'keyword';
    if (content.startsWith('---')) return 'normal';
    if (/^[A-Za-z0-9_-]+\s*:/.test(content)) return 'keyword-value';
    if (/^\s*[-*]\s+/.test(content)) return 'string';
    if (/^\s*\d+\.\s+/.test(content)) return 'string';
    if (/^\s*```/.test(content)) return 'code-block';
    if (/\[[^\]]+\]\(([^)]+)\)/.test(content)) return 'link';
    if (content.includes('`')) return 'code-content';
    return 'normal';
}

const treeNodes = computed(() => [
    {
        key: 'root',
        path: '',
        name:
            project.value?.draftSkillName ||
            project.value?.runtimeSkillName ||
            'skill-studio-project',
        type: 'folder',
        children: decorateTreeNodes(files.value || []),
    },
]);
const isMarkdownFile = computed(() =>
    String(selectedFilePath.value || '')
        .toLowerCase()
        .endsWith('.md')
);
const isPreviewTabActive = computed(() => rightActiveTab.value === PREVIEW_TAB_PATH);
const isSettingsTabActive = computed(() => rightActiveTab.value === SETTINGS_TAB_PATH);
const isPublished = computed(
    () => String(project.value?.status || '').toUpperCase() === 'PUBLISHED'
);
const isChatSending = computed(() => Boolean(chatWorkspaceRef.value?.sending));
const publishButtonText = computed(() => (isPublished.value ? '更新' : '发布'));
const publishedVersionLabel = computed(() => {
    const raw = String(project.value?.publishedVersion || '').trim();
    if (!raw) {
        return '';
    }
    return raw.toUpperCase().startsWith('V') ? raw : `V${raw}`;
});
const renderedMarkdown = computed(() => {
    if (!isMarkdownFile.value) {
        return '';
    }
    return marked.parse(String(editableFileContent.value || ''));
});

const codeLines = computed(() =>
    String(editableFileContent.value || '')
        .split('\n')
        .map((content, index) => ({
            n: index + 1,
            content,
            type: classifyLine(content),
        }))
);

const openTabs = computed(() => {
    const fileNames = collectFileNodes(files.value || []).map(item => ({
        path: item.path,
        name:
            String(item.path || '')
                .split('/')
                .pop() || item.path,
        icon: inferToken(item.path),
        iconClass: inferTokenClass(item.path),
        active: rightActiveTab.value === item.path,
    }));
    return [
        {
            path: PREVIEW_TAB_PATH,
            name: '预览',
            icon: '◉',
            iconClass: 'token-preview',
            active: rightActiveTab.value === PREVIEW_TAB_PATH,
        },
        {
            path: SETTINGS_TAB_PATH,
            name: '设置',
            icon: '⚙',
            iconClass: 'token-settings',
            active: rightActiveTab.value === SETTINGS_TAB_PATH,
        },
        ...fileNames,
    ];
});

const breadcrumbSegments = computed(() => {
    const root =
        project.value?.draftSkillName || project.value?.runtimeSkillName || 'skill-project';
    if (isPreviewTabActive.value) {
        return [root, '预览试运行'];
    }
    if (isSettingsTabActive.value) {
        return [root, '项目设置'];
    }
    if (!selectedFilePath.value) {
        return [root];
    }
    return [root, ...String(selectedFilePath.value).split('/')];
});

function selectRightTab(tab) {
    if (!tab?.path || tab.path === PREVIEW_TAB_PATH) {
        rightActiveTab.value = PREVIEW_TAB_PATH;
        return;
    }
    if (tab.path === SETTINGS_TAB_PATH) {
        rightActiveTab.value = SETTINGS_TAB_PATH;
        return;
    }
    selectFile(tab.path);
}

async function selectFile(path) {
    if (!path) {
        selectedFilePath.value = '';
        selectedFileContent.value = '';
        editableFileContent.value = '';
        rightActiveTab.value = PREVIEW_TAB_PATH;
        return;
    }
    selectedFilePath.value = path;
    rightActiveTab.value = path;
    try {
        const data = await getSkillStudioProjectFileContent(
            props.projectId,
            path,
            handleUnauthorized
        );
        selectedFileContent.value = data?.content || '';
        editableFileContent.value = selectedFileContent.value;
        fileViewMode.value = 'edit';
    } catch (error) {
        selectedFileContent.value = error?.message || '文件内容加载失败';
        editableFileContent.value = selectedFileContent.value;
        fileViewMode.value = 'edit';
    }
}

async function loadFiles() {
    const fileData = await listSkillStudioProjectFiles(props.projectId, handleUnauthorized);
    files.value = Array.isArray(fileData) ? fileData : [];
}

async function loadSessions() {
    const sessionData = await listSkillStudioProjectSessions(
        props.projectId,
        { limit: 20 },
        handleUnauthorized
    );
    sessions.value = Array.isArray(sessionData) ? sessionData : [];
}

async function loadSettingsMeta() {
    try {
        const data = await getSkillStudioProjectSettings(props.projectId, handleUnauthorized);
        applySettingsMeta(data);
    } catch (error) {
        settingsMeta.value = {
            needsRegenerate: false,
            toolSettingsDigest: '',
            lastGeneratedToolDigest: '',
        };
    }
}

function applySettingsMeta(data) {
    settingsMeta.value = {
        needsRegenerate: Boolean(data?.needsRegenerate),
        toolSettingsDigest: String(data?.toolSettingsDigest || ''),
        lastGeneratedToolDigest: String(data?.lastGeneratedToolDigest || ''),
    };
}

async function loadAll() {
    loading.value = true;
    loadError.value = '';
    try {
        const [projectData] = await Promise.all([
            getSkillStudioProject(props.projectId, handleUnauthorized),
        ]);
        project.value = projectData;
        selectedFilePath.value = '';
        selectedFileContent.value = '';
        editableFileContent.value = '';
        rightActiveTab.value = PREVIEW_TAB_PATH;
        await Promise.all([loadFiles(), loadSessions(), loadSettingsMeta()]);
    } catch (error) {
        loadError.value = error?.message || '页面加载失败';
    } finally {
        loading.value = false;
    }
    await ensureBootstrapGeneration();
}

async function handlePublishProject() {
    if (publishRunning.value) {
        return;
    }
    publishRunning.value = true;
    publishError.value = '';
    publishMessage.value = '';
    try {
        const data = await publishSkillStudioProject(props.projectId, handleUnauthorized);
        if (data && typeof data === 'object') {
            project.value = data;
        }
        publishMessage.value = isPublished.value
            ? '更新成功，已同步到技能管理。'
            : '发布成功，已同步到技能管理。';
    } catch (error) {
        publishError.value = error?.message || '发布失败，请稍后重试';
    } finally {
        publishRunning.value = false;
    }
}

async function handleChatRequestFinished() {
    await Promise.all([loadFiles(), loadSessions(), loadSettingsMeta()]);
}

function handleSettingsUpdated(data) {
    applySettingsMeta(data);
    settingsRefreshToken.value += 1;
}

function handleSettingsLoaded(data) {
    applySettingsMeta(data);
}

async function triggerRegenerateFromSettings() {
    if (isChatSending.value) {
        return;
    }
    const workspace = chatWorkspaceRef.value;
    if (!workspace || typeof workspace.handleFrontendRenderAction !== 'function') {
        return;
    }
    await workspace.handleFrontendRenderAction({
        message:
            '基于最新工具绑定重新整理当前技能草稿，保留已有合理业务目标，重点修正工具使用说明、schema 约束、步骤设计和输出规则。',
        messageType: 'normal',
        triggerType: 'TOOL_BINDING_CHANGED',
        source: 'skillstudio-settings',
    });
}

async function ensureBootstrapGeneration() {
    if (autoBootstrapped.value || bootstrapRunning.value) {
        return;
    }
    const shouldBootstrap = String(route.query.bootstrap || '').trim() === '1';
    if (!shouldBootstrap) {
        autoBootstrapped.value = true;
        return;
    }
    if (sessions.value.length) {
        autoBootstrapped.value = true;
        return;
    }
    const initialPrompt = String(project.value?.initialPrompt || '').trim();
    if (!initialPrompt) {
        autoBootstrapped.value = true;
        return;
    }
    bootstrapRunning.value = true;
    autoBootstrapped.value = true;
    try {
        await nextTick();
        const workspace = chatWorkspaceRef.value;
        if (!workspace) {
            return;
        }
        if (typeof workspace.loadConversationItems === 'function') {
            await workspace.loadConversationItems();
        }
        const currentItems = Array.isArray(workspace.conversationItems)
            ? workspace.conversationItems
            : [];
        if (currentItems.length) {
            sessions.value = currentItems;
            return;
        }
        workspace.draft = initialPrompt;
        if (typeof workspace.sendMessage === 'function') {
            await workspace.sendMessage();
        }
        await handleChatRequestFinished();
    } finally {
        bootstrapRunning.value = false;
    }
}

onMounted(() => {
    loadAll();
});
</script>

<template>
    <div class="mirror-page flex h-screen overflow-hidden">
        <div class="mirror-pane-left flex shrink-0 flex-col border-r border-editor-tab-border">
            <div
                class="panel-header flex h-[52px] items-center gap-2 border-b border-editor-tab-border px-4"
            >
                <button class="icon-btn" @click="router.push(ROUTE_PATHS.adminSkillStudio)">
                    <span class="material-symbols-outlined ui-icon text-[16px]">chevron_left</span>
                </button>
                <div class="project-badge">📦</div>
                <span class="editor-text flex-1 text-sm font-medium">
                    {{ project?.name || '技能工坊镜像页' }}
                </span>
                <button class="icon-btn relative" @click="projectInfoVisible = !projectInfoVisible">
                    <span class="material-symbols-outlined ui-icon editor-comment text-[14px]"
                        >keyboard_arrow_down</span
                    >
                    <div
                        v-if="projectInfoVisible"
                        class="project-popover absolute right-0 top-[38px] z-20 w-[280px] rounded-xl border border-slate-200 bg-white p-4 shadow-xl"
                    >
                        <div class="mb-3 flex items-center justify-between">
                            <div class="text-sm font-semibold text-slate-800">项目信息</div>
                            <span
                                class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-500"
                            >
                                {{ project?.status || 'DRAFT' }}
                            </span>
                        </div>
                        <div class="grid gap-3 text-left text-xs">
                            <div>
                                <div class="popover-label">项目名称</div>
                                <div class="popover-value">{{ project?.name || '-' }}</div>
                            </div>
                            <div>
                                <div class="popover-label">运行技能名</div>
                                <div class="popover-value">
                                    {{ project?.runtimeSkillName || '-' }}
                                </div>
                            </div>
                            <div>
                                <div class="popover-label">草稿技能名</div>
                                <div class="popover-value">
                                    {{ project?.draftSkillName || '-' }}
                                </div>
                            </div>
                            <div>
                                <div class="popover-label">最近更新时间</div>
                                <div class="popover-value">
                                    {{ project?.updatedAt || project?.modifiedAt || '-' }}
                                </div>
                            </div>
                            <div v-if="project?.description">
                                <div class="popover-label">项目描述</div>
                                <div class="popover-value whitespace-pre-wrap">
                                    {{ project.description }}
                                </div>
                            </div>
                        </div>
                    </div>
                </button>
                <div class="ml-2 flex items-center gap-1">
                    <button class="icon-btn" @click="fileTreeVisible = !fileTreeVisible">
                        <span class="material-symbols-outlined ui-icon text-[15px]"
                            >folder_open</span
                        >
                    </button>
                </div>
            </div>

            <div class="flex min-h-0 flex-1 flex-col bg-white">
                <template v-if="loading">
                    <div
                        class="flex flex-1 items-center justify-center px-6 text-xs editor-comment"
                    >
                        正在加载项目 {{ props.projectId }} ...
                    </div>
                </template>
                <template v-else-if="loadError">
                    <div class="flex flex-1 items-center justify-center px-6 text-xs text-red-500">
                        {{ loadError }}
                    </div>
                </template>
                <template v-else>
                    <FrontChatWorkspace
                        ref="chatWorkspaceRef"
                        class="mirror-chat-workspace min-h-0 flex-1"
                        :adapter="chatAdapter"
                        :show-sidebar="false"
                        :show-header="false"
                        :enable-attachments="false"
                        :show-sidebar-toggle="false"
                        draft-placeholder="输入你的要求..."
                        empty-title="开始创建技能"
                        empty-description="在这里继续补充技能需求，Creator 会实时生成结果。"
                        empty-icon="psychology"
                        @unauthorized="handleUnauthorized"
                        @request-finished="handleChatRequestFinished"
                    />
                </template>
            </div>
        </div>

        <div
            v-if="fileTreeVisible"
            class="mirror-pane-middle flex shrink-0 flex-col border-r border-editor-tab-border editor-sidebar"
        >
            <div
                class="panel-header flex h-[52px] items-center justify-between border-b border-editor-tab-border px-3"
            >
                <span class="editor-text text-xs font-medium">Projects</span>
                <div class="flex items-center gap-1">
                    <button class="icon-btn">
                        <span class="material-symbols-outlined ui-icon text-[13px]">download</span>
                    </button>
                    <button class="icon-btn">
                        <span class="material-symbols-outlined ui-icon text-[13px]">upload</span>
                    </button>
                    <button class="icon-btn">
                        <span class="material-symbols-outlined ui-icon text-[13px]"
                            >content_copy</span
                        >
                    </button>
                </div>
            </div>

            <div class="custom-scrollbar flex-1 overflow-y-auto">
                <SkillStudioMirrorFileTreeNode
                    v-for="item in treeNodes"
                    :key="item.key"
                    :item="item"
                    :depth="0"
                    :selected-path="selectedFilePath"
                    @select="selectFile"
                />
            </div>
        </div>

        <div class="mirror-pane-right flex min-w-0 flex-1 flex-col">
            <div
                class="panel-header custom-scrollbar flex h-[52px] items-center overflow-x-auto border-b border-editor-tab-border editor-sidebar"
            >
                <div
                    v-for="tab in openTabs"
                    :key="tab.path"
                    :class="['tab-item', tab.active ? 'tab-item-active' : 'tab-item-inactive']"
                    @click="selectRightTab(tab)"
                >
                    <span :class="['tab-token', tab.iconClass || 'token-default']">{{
                        tab.icon
                    }}</span>
                    <span class="tab-name">{{ tab.name }}</span>
                    <span v-if="!tab.active" class="material-symbols-outlined ui-icon tab-close"
                        >close</span
                    >
                </div>
                <div
                    class="ml-auto flex items-center gap-2 border-l border-editor-tab-border px-3 py-2"
                >
                    <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600">
                        {{ isPublished ? '已发布' : '未发布' }}
                    </span>
                    <span
                        v-if="publishedVersionLabel"
                        class="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] text-indigo-600"
                    >
                        {{ publishedVersionLabel }}
                    </span>
                    <span v-if="publishError" class="text-xs text-rose-500">{{
                        publishError
                    }}</span>
                    <span v-else-if="publishMessage" class="text-xs text-emerald-600">{{
                        publishMessage
                    }}</span>
                    <button
                        class="publish-btn"
                        :disabled="publishRunning"
                        @click="handlePublishProject"
                    >
                        {{ publishRunning ? `${publishButtonText}中...` : publishButtonText }}
                    </button>
                </div>
            </div>

            <div
                class="flex h-[36px] items-center justify-between border-b border-editor-tab-border px-4 editor-sidebar"
            >
                <div class="editor-comment flex items-center gap-1 text-xs">
                    <template
                        v-for="(segment, index) in breadcrumbSegments"
                        :key="`${segment}-${index}`"
                    >
                        <span
                            :class="index === breadcrumbSegments.length - 1 ? 'editor-text' : ''"
                            >{{ segment }}</span
                        >
                        <span
                            v-if="index < breadcrumbSegments.length - 1"
                            class="material-symbols-outlined ui-icon text-[11px]"
                        >
                            chevron_right
                        </span>
                    </template>
                </div>
                <div class="flex items-center gap-1">
                    <button
                        v-if="!isPreviewTabActive"
                        :class="[
                            'mode-toggle-btn',
                            fileViewMode === 'preview' ? 'mode-toggle-btn-active' : '',
                        ]"
                        @click="fileViewMode = 'preview'"
                    >
                        预览
                    </button>
                    <button
                        v-if="!isPreviewTabActive"
                        :class="[
                            'mode-toggle-btn',
                            fileViewMode === 'edit' ? 'mode-toggle-btn-active' : '',
                        ]"
                        @click="fileViewMode = 'edit'"
                    >
                        编辑
                    </button>
                </div>
            </div>

            <div v-if="settingsMeta.needsRegenerate" class="regenerate-banner">
                <div>
                    <div class="regenerate-banner-title">工具绑定已更新</div>
                    <div class="regenerate-banner-text">
                        建议基于最新工具配置重新生成技能草稿，使 `SKILL.md`、references
                        和脚本与当前绑定保持一致。
                    </div>
                </div>
                <button
                    class="regenerate-banner-btn"
                    :disabled="isChatSending"
                    @click="triggerRegenerateFromSettings"
                >
                    {{ isChatSending ? '生成中...' : '立即重新生成' }}
                </button>
            </div>

            <div
                :class="[
                    'flex-1',
                    isPreviewTabActive ? 'overflow-hidden' : 'custom-scrollbar overflow-y-auto',
                ]"
            >
                <template v-if="isPreviewTabActive">
                    <FrontChatWorkspace
                        class="preview-chat-workspace h-full min-h-0"
                        :adapter="previewChatAdapter"
                        :show-sidebar="true"
                        :show-header="false"
                        :enable-attachments="true"
                        :show-sidebar-toggle="true"
                        draft-placeholder="输入试运行问题..."
                        empty-title="试运行草稿技能"
                        empty-description="像正式技能一样对话、上传文件并查看产物。"
                        empty-icon="play_circle"
                        @unauthorized="handleUnauthorized"
                    />
                </template>
                <template v-else-if="isSettingsTabActive">
                    <SkillStudioProjectSettingsPanel
                        :project-id="props.projectId"
                        :refresh-token="settingsRefreshToken"
                        @settings-loaded="handleSettingsLoaded"
                        @settings-updated="handleSettingsUpdated"
                        @unauthorized="handleUnauthorized"
                    />
                </template>
                <template v-else-if="selectedFilePath">
                    <div
                        v-if="fileViewMode === 'preview' && isMarkdownFile"
                        class="markdown-preview chat-markdown px-10 py-6"
                        v-html="renderedMarkdown"
                    ></div>
                    <SkillStudioCodeEditor
                        v-else-if="fileViewMode === 'edit'"
                        v-model="editableFileContent"
                        :file-path="selectedFilePath"
                    />
                    <div
                        v-else
                        v-for="line in codeLines"
                        :key="line.n"
                        :class="['code-line', line.n === 2 ? 'code-line-highlight' : '']"
                    >
                        <span class="code-line-number">{{ line.n }}</span>
                        <span class="code-line-content">
                            <span v-if="line.type === 'keyword'" class="editor-keyword">{{
                                line.content
                            }}</span>
                            <span v-else-if="line.type === 'code-block'" class="code-block-line">{{
                                line.content
                            }}</span>
                            <template v-else-if="line.type === 'keyword-value'">
                                <span class="editor-keyword"
                                    >{{ line.content.split(':')[0] }}:</span
                                >
                                <span class="editor-text">{{
                                    line.content.slice(line.content.indexOf(':') + 1)
                                }}</span>
                            </template>
                            <span
                                v-else-if="line.type === 'string' || line.type === 'code-content'"
                                class="editor-string"
                            >
                                {{ line.content }}
                            </span>
                            <span
                                v-else-if="line.type === 'comment-heading'"
                                class="editor-keyword"
                            >
                                {{ line.content }}
                            </span>
                            <span v-else-if="line.type === 'link'" class="text-blue-400 underline">
                                {{ line.content }}
                            </span>
                            <span v-else class="editor-text">{{ line.content || ' ' }}</span>
                        </span>
                    </div>
                </template>
                <template v-else>
                    <div class="px-6 py-8 text-sm editor-comment">当前项目还没有可预览的文件。</div>
                </template>
            </div>

            <div class="status-bar">
                <span>{{
                    isPreviewTabActive
                        ? '预览试运行'
                        : isSettingsTabActive
                          ? '项目设置'
                          : selectedFilePath || '未选择文件'
                }}</span>
                <span>UTF-8</span>
            </div>
        </div>
    </div>
</template>

<style scoped>
.mirror-page {
    --editor-bg: #ffffff;
    --editor-sidebar: #f7f8fa;
    --editor-line: #eef0f5;
    --editor-text: #1a1a2e;
    --editor-keyword: #7c3aed;
    --editor-string: #16a34a;
    --editor-comment: #8a8fa3;
    --editor-tab-border: #e4e6ef;
    background: var(--editor-bg);
    color: var(--editor-text);
    font-family:
        'Regular-1_1',
        system-ui,
        -apple-system,
        BlinkMacSystemFont,
        'PingFang SC',
        sans-serif;
}

.mirror-page * {
    box-sizing: border-box;
}

.panel-header {
    flex-shrink: 0;
}

.mirror-pane-left {
    flex-basis: 31.25%;
}

.mirror-pane-middle {
    flex-basis: 15.625%;
}

.mirror-pane-right {
    flex-basis: 53.125%;
}

.ui-icon {
    font-variation-settings:
        'FILL' 0,
        'wght' 400,
        'GRAD' 0,
        'opsz' 24;
    line-height: 1;
}

.project-popover {
    color: #0f172a;
}

.popover-label {
    margin-bottom: 4px;
    color: #94a3b8;
    font-size: 11px;
    line-height: 1;
}

.popover-value {
    color: #0f172a;
    font-size: 12px;
    line-height: 1.5;
    word-break: break-word;
}

.editor-sidebar {
    background: var(--editor-sidebar);
}

.editor-text {
    color: var(--editor-text);
}

.editor-keyword {
    color: var(--editor-keyword);
}

.editor-string {
    color: var(--editor-string);
}

.editor-comment {
    color: var(--editor-comment);
}

.custom-scrollbar::-webkit-scrollbar {
    width: 4px;
    height: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
    background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
    background: var(--editor-tab-border);
    border-radius: 2px;
}

.project-badge {
    display: flex;
    height: 24px;
    width: 24px;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    background: var(--editor-line);
    font-size: 14px;
}

.icon-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 0;
    background: transparent;
    color: var(--editor-comment);
    border-radius: 6px;
    padding: 4px;
    cursor: pointer;
    transition:
        background 0.2s ease,
        color 0.2s ease;
}

.icon-btn:hover {
    background: var(--editor-line);
    color: var(--editor-text);
}

.mirror-chat-workspace :deep(.custom-scrollbar) {
    padding: 20px 20px 16px;
    background: #ffffff;
}

.mirror-chat-workspace :deep(.mx-auto.max-w-4xl) {
    max-width: 100%;
}

.mirror-chat-workspace :deep(.max-w-\[85\%\]) {
    max-width: 88%;
}

.mirror-chat-workspace :deep(.space-y-8) {
    row-gap: 20px;
}

.mode-toggle-btn {
    border: 1px solid transparent;
    border-radius: 8px;
    background: transparent;
    color: var(--editor-comment);
    font-size: 12px;
    line-height: 1;
    padding: 6px 10px;
    cursor: pointer;
    transition: all 0.2s ease;
}

.mode-toggle-btn:hover {
    background: var(--editor-line);
    color: var(--editor-text);
}

.mode-toggle-btn-active {
    border-color: var(--editor-tab-border);
    background: #ffffff;
    color: var(--editor-text);
}

.token-preview {
    color: #22c55e;
}

.token-settings {
    color: #f59e0b;
}

.token-md {
    color: #eab308;
}

.token-py {
    color: #60a5fa;
}

.token-coze {
    color: #a855f7;
}

.token-git {
    color: #ef4444;
}

.token-json {
    color: #06b6d4;
}

.token-default {
    color: #9ca3af;
}

.tab-item {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    gap: 8px;
    border-right: 1px solid var(--editor-tab-border);
    padding: 10px 16px;
    font-size: 13px;
    cursor: pointer;
    transition:
        background 0.2s ease,
        color 0.2s ease;
    min-height: 40px;
}

.tab-item-active {
    background: var(--editor-bg);
    color: var(--editor-text);
    border-bottom: 2px solid #60a5fa;
}

.tab-item-inactive {
    color: var(--editor-comment);
}

.tab-item-inactive:hover {
    background: var(--editor-line);
    color: var(--editor-text);
}

.tab-token {
    font-size: 13px;
    font-weight: 700;
    min-width: 16px;
    text-align: center;
}

.tab-name {
    max-width: 92px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.tab-close {
    font-size: 11px;
    opacity: 0.5;
}

.publish-btn {
    border: 0;
    border-radius: 8px;
    background: #111827;
    color: #ffffff;
    font-size: 13px;
    font-weight: 600;
    padding: 6px 14px;
    cursor: pointer;
}

.publish-btn:disabled {
    cursor: not-allowed;
    opacity: 0.65;
}

.code-line {
    display: flex;
    align-items: flex-start;
    min-height: 26px;
    padding: 0 16px;
    font-size: 13px;
    line-height: 26px;
}

.code-line:hover {
    background: rgba(238, 240, 245, 0.5);
}

.code-line-highlight {
    background: var(--editor-line);
}

.code-line-number {
    margin-right: 16px;
    width: 36px;
    flex-shrink: 0;
    user-select: none;
    text-align: right;
    color: var(--editor-comment);
}

.code-line-content {
    font-family:
        ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
    white-space: pre-wrap;
    word-break: break-word;
    flex: 1;
}

.code-block-line {
    color: #2563eb;
}

.markdown-preview {
    min-height: 100%;
    color: var(--editor-text);
    font-size: 14px;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
    margin: 16px 0 8px;
    color: var(--editor-text);
    font-weight: 700;
}

.markdown-preview :deep(p),
.markdown-preview :deep(li) {
    line-height: 1.85;
}

.markdown-preview :deep(code) {
    background: var(--editor-line);
    border-radius: 6px;
    padding: 2px 6px;
    font-size: 12px;
}

.markdown-preview :deep(pre) {
    overflow: auto;
    border: 1px solid var(--editor-tab-border);
    border-radius: 12px;
    background: #f8fafc;
    padding: 12px;
}

.preview-chat-workspace :deep(.w-72) {
    width: 240px;
}

.regenerate-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    border-bottom: 1px solid var(--editor-tab-border);
    background: #f8fafc;
    padding: 12px 16px;
}

.regenerate-banner-title {
    color: #0f172a;
    font-size: 13px;
    font-weight: 700;
}

.regenerate-banner-text {
    margin-top: 4px;
    color: #64748b;
    font-size: 12px;
    line-height: 1.6;
}

.regenerate-banner-btn {
    flex-shrink: 0;
    border: 0;
    border-radius: 10px;
    background: #0f172a;
    color: #ffffff;
    font-size: 12px;
    font-weight: 600;
    padding: 8px 14px;
    cursor: pointer;
}

.regenerate-banner-btn:disabled {
    cursor: not-allowed;
    opacity: 0.65;
}

.status-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-top: 1px solid var(--editor-tab-border);
    background: var(--editor-sidebar);
    color: var(--editor-comment);
    font-size: 12px;
    padding: 6px 12px;
}
</style>
