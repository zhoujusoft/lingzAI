<template>
    <div class="file-tree-pane flex h-full flex-col overflow-hidden bg-surface">
        <!-- Toolbar -->
        <div class="toolbar flex shrink-0 items-center justify-between px-3 py-2">
            <div class="flex items-center gap-2">
                <span class="material-symbols-outlined text-muted text-base">folder</span>
                <span class="text-sm font-medium text-strong">工作区</span>
                <span v-if="totalFileCount > 0" class="text-xs text-muted">
                    ({{ totalFileCount }})
                </span>
            </div>
            <button
                type="button"
                class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body disabled:opacity-50"
                :disabled="loading"
                title="刷新"
                @click="handleRefresh"
            >
                <span
                    class="material-symbols-outlined text-base"
                    :class="{ 'animate-spin': loading }"
                    >refresh</span
                >
            </button>
        </div>

        <!-- Loading State -->
        <div v-if="loading && assets.length === 0" class="flex-1 flex items-center justify-center">
            <div class="text-center">
                <span class="material-symbols-outlined text-3xl text-muted animate-spin">
                    progress_activity
                </span>
                <p class="mt-2 text-sm text-muted">加载中...</p>
            </div>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="flex-1 flex items-center justify-center px-6">
            <div class="text-center">
                <span class="material-symbols-outlined text-3xl text-danger">error</span>
                <p class="mt-2 text-sm text-danger">加载失败</p>
                <button
                    type="button"
                    class="mt-3 text-sm text-primary hover:underline"
                    @click="handleRefresh"
                >
                    重试
                </button>
            </div>
        </div>

        <!-- Empty State -->
        <div v-else-if="assets.length === 0" class="flex-1 flex items-center justify-center px-6">
            <div class="text-center">
                <span class="material-symbols-outlined text-3xl text-muted">folder_open</span>
                <p class="mt-2 text-sm text-body">暂无文件</p>
                <p class="mt-1 text-xs text-muted">上传文件或执行技能后，文件将显示在这里</p>
            </div>
        </div>

        <!-- Tree View -->
        <div v-else class="flex-1 overflow-y-auto custom-scrollbar p-2">
            <FileTreeNode
                v-for="node in treeNodes"
                :key="node.key"
                :node="node"
                :depth="0"
                @preview-file="emit('preview-file', $event)"
            />
        </div>
    </div>
</template>

<script setup>
import { toRef, computed } from 'vue';
import { useRuntimeFileAssets } from '@/composables/useRuntimeFileAssets';
import FileTreeNode from './FileTreeNode.vue';

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

const emit = defineEmits(['preview-file']);

const { loading, error, assets, pagination, loadAssets } = useRuntimeFileAssets({
    sessionCode: toRef(props, 'sessionCode'),
    enabled: toRef(props, 'enabled'),
});

// 总文件数
const totalFileCount = computed(() => pagination.value.total || 0);

/**
 * 手动刷新工作区
 */
function handleRefresh() {
    if (loading.value) return;
    loadAssets();
}

/**
 * 从文件对象提取路径
 * 优先使用 virtualPath（后端返回的虚拟路径）
 * 其次使用 objectName（MinIO 对象名，通常包含路径）
 * 最后根据 fileRole 构造默认分组
 */
function extractPath(file) {
    // 优先使用 virtualPath
    const virtualPath = file.virtualPath || '';
    if (virtualPath) {
        return virtualPath;
    }

    // 其次使用 objectName
    const objectName = file.objectName || '';
    if (objectName && objectName.includes('/')) {
        return objectName;
    }

    // 最后根据 fileRole 构造默认分组
    const displayName = file.displayName || 'unknown';
    const role = file.fileRole || 'TEMP';
    const roleLabel =
        {
            UPLOAD: '上传文件',
            ARTIFACT: '技能产物',
            TEMP: '临时文件',
        }[role] || role;

    return `${roleLabel}/${displayName}`;
}

/**
 * 构建树结构
 */
const treeNodes = computed(() => {
    const files = assets.value || [];
    if (files.length === 0) return [];

    // 构建树形结构
    const root = { children: {} };

    files.forEach(file => {
        const path = extractPath(file);
        const parts = path.split('/').filter(Boolean);

        let current = root;
        for (let i = 0; i < parts.length; i++) {
            const part = parts[i];
            const isFile = i === parts.length - 1;

            if (!current.children[part]) {
                current.children[part] = {
                    name: part,
                    key: `${file.fileCode}-${parts.slice(0, i + 1).join('/')}`,
                    isFile: false,
                    file: null,
                    children: {},
                };
            }

            if (isFile) {
                current.children[part].isFile = true;
                current.children[part].file = file;
            } else {
                current = current.children[part];
            }
        }
    });

    // 转换为数组并排序（文件夹在前，文件在后）
    function toSortedArray(nodeMap) {
        const nodes = Object.values(nodeMap);

        // 递归处理子节点
        nodes.forEach(node => {
            if (!node.isFile && Object.keys(node.children).length > 0) {
                node.children = toSortedArray(node.children);
            }
        });

        // 排序：文件夹在前，文件在后；同类型按名称排序
        return nodes.sort((a, b) => {
            if (a.isFile !== b.isFile) {
                return a.isFile ? 1 : -1;
            }
            return a.name.localeCompare(b.name, 'zh-CN');
        });
    }

    return toSortedArray(root.children);
});
</script>

<style scoped>
.file-tree-pane {
    background: rgb(var(--color-bg-surface));
}
</style>
