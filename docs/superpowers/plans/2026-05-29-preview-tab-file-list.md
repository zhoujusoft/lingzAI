# Preview Tab File List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在预览面板添加 Tab 切换功能，支持 HTML 预览和文件列表两种视图。

**Architecture:** 使用容器组件包装现有预览组件，新增文件列表组件和 composable 管理文件资产数据，懒加载方式获取数据。

**Tech Stack:** Vue 3 Composition API, Tailwind CSS, Material Symbols Icons

---

## File Structure

**新增文件:**
- `frontend/packages/web/src/api/runtimeFileAsset.js` - API 调用封装
- `frontend/packages/web/src/composables/useRuntimeFileAssets.js` - 文件资产数据管理
- `frontend/packages/web/src/views/front/components/agent-chat/FilePreviewItem.vue` - 文件列表项组件
- `frontend/packages/web/src/views/front/components/agent-chat/FilePreviewPane.vue` - 文件列表组件
- `frontend/packages/web/src/views/front/components/agent-chat/PreviewTabContainer.vue` - Tab 容器组件

**修改文件:**
- `frontend/packages/web/src/views/front/pages/FrontAgentChatPage.vue` - 集成 Tab 容器
- `frontend/packages/web/src/views/front/pages/FrontAgentChatV2Page.vue` - 集成 Tab 容器

---

### Task 1: API Layer

**Files:**
- Create: `frontend/packages/web/src/api/runtimeFileAsset.js`

- [ ] **Step 1: 创建 API 文件**

创建文件 `frontend/packages/web/src/api/runtimeFileAsset.js`:

```javascript
import request from '@lingzhou/core/http/request';

/**
 * 获取运行时文件资产列表
 * @param {Object} params
 * @param {string} [params.sessionId] - 会话ID
 * @param {string} [params.fileRole] - 文件角色: UPLOAD, ARTIFACT, TEMP
 * @param {number} [params.pageNo] - 页码
 * @param {number} [params.pageSize] - 每页大小
 * @returns {Promise<{items: Array, current: number, size: number, total: number, pages: number}>}
 */
export function listRuntimeFileAssets(params) {
    return request.get('/files/assets', { params });
}

/**
 * 获取文件预览URL
 * @param {string} fileCode - 文件编码
 * @returns {string} 预览URL
 */
export function getFilePreviewUrl(fileCode) {
    return `/api/files/assets/${fileCode}/preview`;
}

/**
 * 获取文件下载URL
 * @param {string} fileCode - 文件编码
 * @returns {string} 下载URL
 */
export function getFileDownloadUrl(fileCode) {
    return `/api/files/assets/${fileCode}/download`;
}
```

- [ ] **Step 2: 提交 API 层**

```bash
git add frontend/packages/web/src/api/runtimeFileAsset.js
git commit -m "feat(frontend): add runtimeFileAsset API functions"
```

---

### Task 2: Composable Layer

**Files:**
- Create: `frontend/packages/web/src/composables/useRuntimeFileAssets.js`

- [ ] **Step 1: 创建 composable 文件**

创建文件 `frontend/packages/web/src/composables/useRuntimeFileAssets.js`:

```javascript
import { ref, computed, watch } from 'vue';
import { listRuntimeFileAssets } from '@/api/runtimeFileAsset';

/**
 * 运行时文件资产管理 composable
 * @param {Object} options
 * @param {import('vue').Ref<string>} options.sessionCode - 会话编码
 * @param {import('vue').Ref<boolean>} options.enabled - 是否启用加载
 * @returns {{
 *   loading: import('vue').Ref<boolean>,
 *   error: import('vue').Ref<Error|null>,
 *   assets: import('vue').Ref<Array>,
 *   groupedAssets: import('vue').ComputedRef<{UPLOAD: Array, ARTIFACT: Array, TEMP: Array}>,
 *   pagination: import('vue').Ref<{current: number, size: number, total: number}>,
 *   loadAssets: (pageNo?: number) => Promise<void>
 * }}
 */
export function useRuntimeFileAssets(options) {
    const { sessionCode, enabled } = options;

    const loading = ref(false);
    const error = ref(null);
    const assets = ref([]);
    const pagination = ref({
        current: 1,
        size: 50,
        total: 0,
    });

    // 按文件角色分组
    const groupedAssets = computed(() => {
        const groups = {
            UPLOAD: [],
            ARTIFACT: [],
            TEMP: [],
        };

        for (const asset of assets.value) {
            const role = asset.fileRole || 'TEMP';
            if (groups[role]) {
                groups[role].push(asset);
            }
        }

        return groups;
    });

    /**
     * 加载文件资产列表
     * @param {number} [pageNo=1] - 页码
     */
    async function loadAssets(pageNo = 1) {
        if (!enabled.value) return;

        loading.value = true;
        error.value = null;

        try {
            const response = await listRuntimeFileAssets({
                sessionId: sessionCode.value || undefined,
                pageNo,
                pageSize: pagination.value.size,
            });

            assets.value = response.items || [];
            pagination.value = {
                current: response.current || 1,
                size: response.size || 50,
                total: response.total || 0,
            };
        } catch (err) {
            error.value = err;
            assets.value = [];
        } finally {
            loading.value = false;
        }
    }

    // 懒加载：enabled 变为 true 且数据为空时自动加载
    watch(enabled, (newVal) => {
        if (newVal && assets.value.length === 0) {
            loadAssets();
        }
    });

    return {
        loading,
        error,
        assets,
        groupedAssets,
        pagination,
        loadAssets,
    };
}
```

- [ ] **Step 2: 提交 composable 层**

```bash
git add frontend/packages/web/src/composables/useRuntimeFileAssets.js
git commit -m "feat(frontend): add useRuntimeFileAssets composable"
```

---

### Task 3: FilePreviewItem Component

**Files:**
- Create: `frontend/packages/web/src/views/front/components/agent-chat/FilePreviewItem.vue`

- [ ] **Step 1: 创建文件列表项组件**

创建文件 `frontend/packages/web/src/views/front/components/agent-chat/FilePreviewItem.vue`:

```vue
<template>
    <div
        class="file-item group flex items-center gap-3 rounded-lg px-3 py-2 cursor-pointer transition-colors hover:bg-slate-100"
        @click="handleClick"
    >
        <!-- File Icon -->
        <div
            class="file-icon flex-shrink-0 w-9 h-9 rounded-lg flex items-center justify-center"
            :class="iconClass"
        >
            <span class="material-symbols-outlined text-lg">{{ iconName }}</span>
        </div>

        <!-- File Info -->
        <div class="flex-1 min-w-0">
            <p class="file-name text-sm font-medium text-slate-800 truncate">
                {{ file.displayName }}
            </p>
            <p class="file-meta text-xs text-slate-400 mt-0.5">
                {{ formatSize(file.sizeBytes) }}
                <span v-if="file.createdAt" class="mx-1">·</span>
                {{ formatDate(file.createdAt) }}
            </p>
        </div>

        <!-- Actions -->
        <div class="file-actions flex items-center gap-1">
            <button
                v-if="isPreviewable"
                type="button"
                class="action-btn"
                title="预览"
                @click.stop="handlePreview"
            >
                <span class="material-symbols-outlined text-base">visibility</span>
            </button>
            <button
                type="button"
                class="action-btn"
                title="下载"
                @click.stop="handleDownload"
            >
                <span class="material-symbols-outlined text-base">download</span>
            </button>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue';
import { getFilePreviewUrl, getFileDownloadUrl } from '@/api/runtimeFileAsset';

const props = defineProps({
    file: {
        type: Object,
        required: true,
    },
});

// 文件图标名称
const iconName = computed(() => {
    const ext = (props.file.extension || '').toLowerCase();
    const contentType = (props.file.contentType || '').toLowerCase();

    if (contentType.startsWith('image/') || ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].includes(ext)) {
        return 'image';
    }
    if (contentType.includes('pdf') || ext === '.pdf') {
        return 'picture_as_pdf';
    }
    if (contentType.includes('spreadsheet') || ['.xlsx', '.xls', '.csv'].includes(ext)) {
        return 'table';
    }
    if (contentType.includes('document') || ['.docx', '.doc'].includes(ext)) {
        return 'description';
    }
    if (['.html', '.htm'].includes(ext)) {
        return 'code';
    }
    if (['.json'].includes(ext)) {
        return 'data_object';
    }
    if (['.md', '.txt', '.log'].includes(ext)) {
        return 'article';
    }
    return 'insert_drive_file';
});

// 文件图标样式类
const iconClass = computed(() => {
    const ext = (props.file.extension || '').toLowerCase();
    const contentType = (props.file.contentType || '').toLowerCase();

    if (contentType.startsWith('image/') || ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].includes(ext)) {
        return 'bg-emerald-100 text-emerald-600';
    }
    if (contentType.includes('pdf') || ext === '.pdf') {
        return 'bg-rose-100 text-rose-600';
    }
    if (['.html', '.htm', '.json'].includes(ext)) {
        return 'bg-violet-100 text-violet-600';
    }
    return 'bg-slate-100 text-slate-500';
});

// 是否可预览
const isPreviewable = computed(() => {
    const contentType = (props.file.contentType || '').toLowerCase();
    const ext = (props.file.extension || '').toLowerCase();

    return (
        contentType.startsWith('text/') ||
        contentType === 'application/json' ||
        contentType.startsWith('image/') ||
        [
            '.html',
            '.htm',
            '.json',
            '.md',
            '.txt',
            '.log',
            '.csv',
            '.png',
            '.jpg',
            '.jpeg',
            '.gif',
            '.webp',
            '.svg',
        ].includes(ext)
    );
});

/**
 * 格式化文件大小
 * @param {number} bytes - 字节数
 * @returns {string} 格式化后的大小
 */
function formatSize(bytes) {
    if (!bytes) return '-';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

/**
 * 格式化日期
 * @param {string} dateStr - 日期字符串
 * @returns {string} 格式化后的日期
 */
function formatDate(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-CN', {
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

/**
 * 点击文件项
 */
function handleClick() {
    if (isPreviewable.value) {
        handlePreview();
    } else {
        handleDownload();
    }
}

/**
 * 预览文件
 */
function handlePreview() {
    const url = getFilePreviewUrl(props.file.fileCode);
    window.open(url, '_blank');
}

/**
 * 下载文件
 */
function handleDownload() {
    const url = getFileDownloadUrl(props.file.fileCode);
    window.open(url, '_blank');
}
</script>

<style scoped>
.file-item:hover .file-actions {
    opacity: 1;
}

.action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 6px;
    border: none;
    background: transparent;
    color: #64748b;
    cursor: pointer;
    transition: all 0.15s ease;
}

.action-btn:hover {
    background: #e2e8f0;
    color: #334155;
}
</style>
```

- [ ] **Step 2: 提交文件列表项组件**

```bash
git add frontend/packages/web/src/views/front/components/agent-chat/FilePreviewItem.vue
git commit -m "feat(frontend): add FilePreviewItem component"
```

---

### Task 4: FilePreviewPane Component

**Files:**
- Create: `frontend/packages/web/src/views/front/components/agent-chat/FilePreviewPane.vue`

- [ ] **Step 1: 创建文件列表组件**

创建文件 `frontend/packages/web/src/views/front/components/agent-chat/FilePreviewPane.vue`:

```vue
<template>
    <div class="file-preview-pane flex h-full flex-col overflow-hidden">
        <!-- Toolbar -->
        <div
            class="toolbar flex shrink-0 items-center justify-between px-4 py-3 border-b border-slate-100"
        >
            <div class="flex items-center gap-2">
                <span class="material-symbols-outlined text-slate-400 text-lg">folder</span>
                <span class="text-sm font-medium text-slate-700">文件列表</span>
                <span v-if="pagination.total > 0" class="text-xs text-slate-400">
                    ({{ pagination.total }} 个文件)
                </span>
            </div>
            <button
                type="button"
                class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
                :disabled="loading"
                @click="refresh"
            >
                <span class="material-symbols-outlined text-sm">refresh</span>
                <span>刷新</span>
            </button>
        </div>

        <!-- Loading State -->
        <div
            v-if="loading && assets.length === 0"
            class="flex-1 flex items-center justify-center"
        >
            <div class="text-center">
                <span class="material-symbols-outlined text-4xl text-slate-300 animate-spin">
                    progress_activity
                </span>
                <p class="mt-2 text-sm text-slate-400">加载中...</p>
            </div>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="flex-1 flex items-center justify-center px-6">
            <div class="text-center">
                <span class="material-symbols-outlined text-4xl text-rose-300">error</span>
                <p class="mt-2 text-sm text-rose-500">加载失败</p>
                <button
                    type="button"
                    class="mt-3 text-sm text-violet-600 hover:underline"
                    @click="refresh"
                >
                    重试
                </button>
            </div>
        </div>

        <!-- Empty State -->
        <div v-else-if="assets.length === 0" class="flex-1 flex items-center justify-center px-6">
            <div class="text-center">
                <span class="material-symbols-outlined text-4xl text-slate-300">folder_open</span>
                <p class="mt-2 text-sm text-slate-500">暂无文件</p>
                <p class="mt-1 text-xs text-slate-400">上传文件或执行技能后，文件将显示在这里</p>
            </div>
        </div>

        <!-- File List -->
        <div v-else class="flex-1 overflow-y-auto custom-scrollbar p-2">
            <!-- Upload Files Group -->
            <div v-if="groupedAssets.UPLOAD.length > 0" class="file-group mb-4">
                <div
                    class="group-header flex items-center gap-2 px-2 py-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider"
                >
                    <span class="material-symbols-outlined text-sm">upload</span>
                    <span>上传文件</span>
                </div>
                <FilePreviewItem
                    v-for="file in groupedAssets.UPLOAD"
                    :key="file.fileCode"
                    :file="file"
                />
            </div>

            <!-- Artifact Files Group -->
            <div v-if="groupedAssets.ARTIFACT.length > 0" class="file-group mb-4">
                <div
                    class="group-header flex items-center gap-2 px-2 py-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider"
                >
                    <span class="material-symbols-outlined text-sm">auto_awesome</span>
                    <span>技能产物</span>
                </div>
                <FilePreviewItem
                    v-for="file in groupedAssets.ARTIFACT"
                    :key="file.fileCode"
                    :file="file"
                />
            </div>

            <!-- Temp Files Group -->
            <div v-if="groupedAssets.TEMP.length > 0" class="file-group">
                <div
                    class="group-header flex items-center gap-2 px-2 py-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider"
                >
                    <span class="material-symbols-outlined text-sm">temp_preferences_custom</span>
                    <span>临时文件</span>
                </div>
                <FilePreviewItem
                    v-for="file in groupedAssets.TEMP"
                    :key="file.fileCode"
                    :file="file"
                />
            </div>
        </div>
    </div>
</template>

<script setup>
import { toRef } from 'vue';
import { useRuntimeFileAssets } from '@/composables/useRuntimeFileAssets';
import FilePreviewItem from './FilePreviewItem.vue';

const props = defineProps({
    sessionCode: {
        type: String,
        default: '',
    },
    enabled: {
        type: Boolean,
        default: false,
    },
});

const { loading, error, assets, groupedAssets, pagination, loadAssets: refresh } =
    useRuntimeFileAssets({
        sessionCode: toRef(props, 'sessionCode'),
        enabled: toRef(props, 'enabled'),
    });
</script>

<style scoped>
.file-preview-pane {
    background: #fafbfc;
}

.group-header {
    background: #f1f5f9;
    border-radius: 6px;
    margin-bottom: 4px;
}
</style>
```

- [ ] **Step 2: 提交文件列表组件**

```bash
git add frontend/packages/web/src/views/front/components/agent-chat/FilePreviewPane.vue
git commit -m "feat(frontend): add FilePreviewPane component"
```

---

### Task 5: PreviewTabContainer Component

**Files:**
- Create: `frontend/packages/web/src/views/front/components/agent-chat/PreviewTabContainer.vue`

- [ ] **Step 1: 创建 Tab 容器组件**

创建文件 `frontend/packages/web/src/views/front/components/agent-chat/PreviewTabContainer.vue`:

```vue
<template>
    <div class="preview-tab-container flex h-full flex-col overflow-hidden rounded-2xl bg-white border border-slate-200 shadow-sm">
        <!-- Tab Header -->
        <div class="tab-header flex shrink-0 border-b border-slate-200 bg-slate-50 rounded-t-2xl">
            <button
                v-for="tab in tabs"
                :key="tab.key"
                type="button"
                class="tab-item flex items-center gap-2 px-5 py-3 text-sm font-medium transition-colors"
                :class="[
                    activeTab === tab.key
                        ? 'border-b-2 border-violet-500 text-violet-700 bg-white'
                        : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100',
                ]"
                @click="activeTab = tab.key"
            >
                <span class="material-symbols-outlined text-base">{{ tab.icon }}</span>
                <span>{{ tab.label }}</span>
            </button>
        </div>

        <!-- Tab Content -->
        <div class="tab-content flex-1 overflow-hidden">
            <!-- HTML 预览 Tab -->
            <div v-show="activeTab === 'preview'" class="h-full">
                <FrontAgentChatPreview
                    :render-payload="renderPayload"
                    @close="handleClose"
                />
            </div>

            <!-- 文件列表 Tab -->
            <div v-show="activeTab === 'files'" class="h-full">
                <FilePreviewPane
                    :session-code="sessionCode"
                    :enabled="activeTab === 'files'"
                />
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import FrontAgentChatPreview from './FrontAgentChatPreview.vue';
import FilePreviewPane from './FilePreviewPane.vue';

const props = defineProps({
    renderPayload: {
        type: Object,
        default: null,
    },
    sessionCode: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['close']);

const tabs = [
    { key: 'preview', label: 'HTML 预览', icon: 'overview' },
    { key: 'files', label: '文件列表', icon: 'folder' },
];

const activeTab = ref('preview');

// 当有新的预览内容时，自动切换到预览 tab
watch(
    () => props.renderPayload,
    (newVal) => {
        if (newVal) {
            activeTab.value = 'preview';
        }
    }
);

function handleClose() {
    emit('close');
}
</script>

<style scoped>
.preview-tab-container {
    background: #ffffff;
}

.tab-header {
    padding-left: 0;
}

.tab-item {
    position: relative;
    border-bottom: 2px solid transparent;
}

.tab-item:first-child {
    border-top-left-radius: 1rem;
}
</style>
```

- [ ] **Step 2: 提交 Tab 容器组件**

```bash
git add frontend/packages/web/src/views/front/components/agent-chat/PreviewTabContainer.vue
git commit -m "feat(frontend): add PreviewTabContainer component"
```

---

### Task 6: Integrate with FrontAgentChatPage

**Files:**
- Modify: `frontend/packages/web/src/views/front/pages/FrontAgentChatPage.vue`

- [ ] **Step 1: 分析现有代码获取 sessionCode**

阅读 `FrontAgentChatPage.vue`，找到 sessionCode 的来源。查看 `FrontAgentChatWorkspace` 组件是否暴露 sessionCode，或者从路由参数获取。

检查现有代码中 sessionCode 的获取方式：
- 可能从 `useRoute()` 获取
- 可能从 workspace 组件 ref 获取
- 可能需要新增状态

- [ ] **Step 2: 导入新组件并替换**

修改 `frontend/packages/web/src/views/front/pages/FrontAgentChatPage.vue`:

1. 在 import 区域添加：
```javascript
import PreviewTabContainer from '../components/agent-chat/PreviewTabContainer.vue';
```

2. 找到 `FrontAgentChatPreview` 的使用位置，替换为 `PreviewTabContainer`

3. 如果需要 sessionCode，添加获取逻辑

- [ ] **Step 3: 验证修改**

检查页面是否正常工作，Tab 切换是否正常。

- [ ] **Step 4: 提交页面集成**

```bash
git add frontend/packages/web/src/views/front/pages/FrontAgentChatPage.vue
git commit -m "feat(frontend): integrate PreviewTabContainer in FrontAgentChatPage"
```

---

### Task 7: Integrate with FrontAgentChatV2Page

**Files:**
- Modify: `frontend/packages/web/src/views/front/pages/FrontAgentChatV2Page.vue`

- [ ] **Step 1: 导入新组件并替换**

修改 `frontend/packages/web/src/views/front/pages/FrontAgentChatV2Page.vue`:

1. 在 import 区域添加：
```javascript
import PreviewTabContainer from '../components/agent-chat/PreviewTabContainer.vue';
```

2. 找到 `FrontAgentChatPreview` 的使用位置，替换为 `PreviewTabContainer`

3. 传递必要的 props (renderPayload, sessionCode)

- [ ] **Step 2: 验证修改**

检查页面是否正常工作。

- [ ] **Step 3: 提交页面集成**

```bash
git add frontend/packages/web/src/views/front/pages/FrontAgentChatV2Page.vue
git commit -m "feat(frontend): integrate PreviewTabContainer in FrontAgentChatV2Page"
```

---

### Task 8: Manual Testing and Verification

- [ ] **Step 1: 启动前端开发服务器**

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

- [ ] **Step 2: 测试 Tab 切换**

1. 打开聊天页面
2. 触发一个有预览内容的请求
3. 验证预览面板显示
4. 点击"文件列表" Tab，验证懒加载
5. 切换回"HTML 预览" Tab，验证状态保持

- [ ] **Step 3: 测试文件列表功能**

1. 验证文件分组显示
2. 验证文件预览功能
3. 验证文件下载功能
4. 验证刷新功能

- [ ] **Step 4: 测试边界情况**

1. 无文件时的空状态
2. 加载失败时的错误状态
3. 无预览内容时面板不显示

- [ ] **Step 5: 最终提交**

如果所有测试通过，创建一个汇总提交：

```bash
git add -A
git commit -m "feat(frontend): add preview pane tab with file list support"
```

---

## Notes

1. **sessionCode 获取**: Task 6 和 Task 7 需要根据实际代码结构确定 sessionCode 的获取方式。如果现有代码没有暴露 sessionCode，可能需要修改 `FrontAgentChatWorkspace` 组件或从路由参数获取。

2. **样式一致性**: 所有新增组件的样式与现有 UI 风格保持一致，使用 Tailwind CSS 和 Material Symbols 图标。

3. **懒加载**: 文件列表仅在首次切换到"文件列表" Tab 时加载，后续切换保持状态。
