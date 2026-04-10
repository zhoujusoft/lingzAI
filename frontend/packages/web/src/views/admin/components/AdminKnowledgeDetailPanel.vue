<script setup>
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import PageLayout from '@/components/PageLayout.vue';
import DirectoryActionsMenu from './DirectoryActionsMenu.vue';
import { alert, confirm, prompt } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    createKnowledgeFolder,
    deleteDocument,
    deleteKnowledgeFolder,
    listKnowledgeChildren,
    listKnowledgeTree,
    moveKnowledgeNode,
    processDocument,
    renameKnowledgeFolder,
    retryDocument,
    uploadDocument,
} from '@/api/datasets';

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
});

const emit = defineEmits([
    'back',
    'edit-knowledge',
    'upload-document',
    'process-document',
    'open-document',
]);

const router = useRouter();
const items = ref([]);
const treeNodes = ref([]);
const currentParentId = ref(null);
const loading = ref(false);
const loadError = ref('');
const fileInputRef = ref(null);
const uploadingFile = ref(false);
const moveDialogOpen = ref(false);
const moveTargetParentId = ref('');
const movingItem = ref(null);
const moving = ref(false);
const ERROR_MESSAGE_SUMMARY_LIMIT = 96;

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function summarizeErrorMessage(message) {
    if (!message) {
        return '';
    }
    const normalized = String(message).replace(/\s+/g, ' ').trim();
    if (normalized.length <= ERROR_MESSAGE_SUMMARY_LIMIT) {
        return normalized;
    }
    return `${normalized.slice(0, ERROR_MESSAGE_SUMMARY_LIMIT)}...`;
}

function normalizeDocuments(list) {
    return list.map(item => ({
        docId: item.docId,
        kbId: item.kbId,
        parentId: item.parentId ?? null,
        isFolder: Number(item.isFolder || 0),
        name: item.name,
        path: item.path || '',
        fileType: item.fileType || '-',
        fileSize: item.fileSize,
        status: Number(item.status),
        errorMessage: item.errorMessage || '',
        errorMessageSummary: summarizeErrorMessage(item.errorMessage),
        uploadTime: item.uploadTime || '',
    }));
}

const nodeIndex = computed(() => {
    const map = new Map();
    const visit = (nodes = []) => {
        nodes.forEach(node => {
            map.set(Number(node.docId), node);
            visit(node.children || []);
        });
    };
    visit(treeNodes.value);
    return map;
});

const currentFolder = computed(() => {
    if (currentParentId.value == null) {
        return null;
    }
    return nodeIndex.value.get(Number(currentParentId.value)) || null;
});

const breadcrumbs = computed(() => {
    if (!currentFolder.value) {
        return [];
    }
    const trail = [];
    let cursor = currentFolder.value;
    while (cursor) {
        trail.unshift({
            docId: cursor.docId,
            name: cursor.name,
        });
        cursor = cursor.parentId == null ? null : nodeIndex.value.get(Number(cursor.parentId));
    }
    return trail;
});

const documentCount = computed(() => items.value.filter(item => item.isFolder !== 1).length);
const moveTargetFolders = computed(() => {
    const currentId = movingItem.value?.docId ?? null;
    const currentPath = movingItem.value?.path || '';
    const filterNodes = (nodes = []) =>
        nodes
            .filter(node => Number(node.isFolder || 0) === 1)
            .filter(node => node.docId !== currentId)
            .filter(
                node =>
                    !(
                        movingItem.value?.isFolder === 1 &&
                        currentPath &&
                        node.path?.startsWith(`${currentPath}/`)
                    )
            )
            .map(node => ({
                ...node,
                children: filterNodes(node.children || []),
            }));

    return filterNodes(treeNodes.value);
});

function statusText(status) {
    switch (Number(status)) {
        case 0:
            return '待处理';
        case 1:
            return '处理中';
        case 2:
            return '已完成';
        case 3:
            return '失败';
        default:
            return '未知';
    }
}

function statusClass(status) {
    switch (Number(status)) {
        case 0:
            return 'bg-slate-100 text-slate-600';
        case 1:
            return 'bg-amber-50 text-amber-700';
        case 2:
            return 'bg-green-50 text-green-700';
        case 3:
            return 'bg-red-50 text-red-600';
        default:
            return 'bg-slate-100 text-slate-500';
    }
}

function fileIcon(fileType) {
    const type = String(fileType || '').toLowerCase();
    if (type === 'pdf') {
        return 'picture_as_pdf';
    }
    if (type === 'doc' || type === 'docx') {
        return 'description';
    }
    return 'article';
}

function formatFileSize(fileSize) {
    if (typeof fileSize !== 'number' || !Number.isFinite(fileSize) || fileSize <= 0) {
        return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    const index = Math.min(Math.floor(Math.log(fileSize) / Math.log(1024)), units.length - 1);
    const value = fileSize / 1024 ** index;
    return `${Math.round(value * 100) / 100} ${units[index]}`;
}

async function loadTree() {
    if (!props.knowledge?.id) {
        treeNodes.value = [];
        return;
    }
    treeNodes.value = await listKnowledgeTree(props.knowledge.id, handleUnauthorized);
}

async function loadChildren() {
    if (!props.knowledge?.id) {
        items.value = [];
        return;
    }

    loading.value = true;
    loadError.value = '';
    try {
        const data = await listKnowledgeChildren(
            {
                kbId: props.knowledge.id,
                parentId: currentParentId.value,
            },
            handleUnauthorized
        );
        items.value = normalizeDocuments(Array.isArray(data) ? data : []);
    } catch (error) {
        items.value = [];
        loadError.value = error?.message || '文档列表加载失败';
    } finally {
        loading.value = false;
    }
}

async function refreshView() {
    await loadTree();
    if (currentParentId.value != null && !nodeIndex.value.has(Number(currentParentId.value))) {
        currentParentId.value = null;
    }
    await loadChildren();
}

function openFolder(item) {
    if (item.isFolder !== 1) {
        return;
    }
    currentParentId.value = item.docId;
}

function openDocument(item) {
    if (item.isFolder === 1) {
        openFolder(item);
        return;
    }
    emit('open-document', {
        ...item,
        parentId: currentParentId.value,
    });
}

function goToCrumb(item) {
    currentParentId.value = item?.docId ?? null;
}

function triggerFileUpload() {
    if (uploadingFile.value) {
        return;
    }
    fileInputRef.value?.click();
}

async function handleFileChange(event) {
    const [file] = Array.from(event.target.files || []);
    event.target.value = '';
    if (!file) {
        return;
    }

    const ext = file.name.split('.').pop()?.toLowerCase();
    const allowedExtensions = ['pdf', 'txt', 'md', 'doc', 'docx'];
    if (!allowedExtensions.includes(ext || '')) {
        await alert({
            title: '上传失败',
            message: '仅支持 PDF、TXT、MD、DOC、DOCX 文件。',
        });
        return;
    }

    uploadingFile.value = true;
    try {
        const result = await uploadDocument({
            kbId: props.knowledge.id,
            parentId: currentParentId.value,
            file,
            onUnauthorized: handleUnauthorized,
        });
        emit('upload-document', {
            ...props.knowledge,
            parentId: currentParentId.value,
            path: currentFolder.value?.path || '',
            document: {
                docId: result.docId,
                name: file.name,
            },
        });
    } catch (error) {
        await alert({
            title: '上传失败',
            message: error?.message || '文件上传失败，请稍后重试。',
        });
    } finally {
        uploadingFile.value = false;
    }
}

async function createFolder() {
    const name = await prompt({
        title: '新建目录',
        message: '请输入目录名称',
        confirmText: '创建',
        cancelText: '取消',
        placeholder: '例如：技术规范书',
    });
    if (!name) {
        return;
    }
    try {
        await createKnowledgeFolder(
            {
                kbId: props.knowledge.id,
                parentId: currentParentId.value,
                name,
            },
            handleUnauthorized
        );
        await refreshView();
    } catch (error) {
        await alert({
            title: '创建失败',
            message: error?.message || '目录创建失败，请稍后重试。',
        });
    }
}

async function renameFolder(item) {
    const name = await prompt({
        title: '重命名目录',
        message: '请输入新的目录名称',
        confirmText: '保存',
        cancelText: '取消',
        placeholder: '请输入目录名称',
        initialValue: item.name || '',
    });
    if (!name) {
        return;
    }
    try {
        await renameKnowledgeFolder(
            {
                kbId: props.knowledge.id,
                docId: item.docId,
                name,
            },
            handleUnauthorized
        );
        await refreshView();
    } catch (error) {
        await alert({
            title: '重命名失败',
            message: error?.message || '目录重命名失败，请稍后重试。',
        });
    }
}

async function removeFolder(item) {
    const confirmed = await confirm({
        title: '删除目录',
        message: `确认删除目录“${item.name || ''}”吗？目录下文档会一并删除。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }

    try {
        await deleteKnowledgeFolder(
            {
                kbId: props.knowledge.id,
                docId: item.docId,
            },
            handleUnauthorized
        );
        await refreshView();
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除目录失败，请稍后重试。',
        });
    }
}

async function removeDocument(item) {
    const confirmed = await confirm({
        title: '删除文档',
        message: `确认删除文档“${item.name || ''}”吗？`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }

    try {
        await deleteDocument(props.knowledge.id, item.docId, handleUnauthorized);
        await refreshView();
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除文档失败，请稍后重试。',
        });
    }
}

async function rerunDocument(item) {
    try {
        await retryDocument(item.docId, handleUnauthorized);
        await refreshView();
    } catch (error) {
        await alert({
            title: '重试失败',
            message: error?.message || '重新处理失败，请稍后重试。',
        });
    }
}

function processPendingDocument(item) {
    emit('process-document', {
        ...props.knowledge,
        parentId: currentParentId.value,
        path: currentFolder.value?.path || '',
        document: {
            docId: item.docId,
            name: item.name,
        },
    });
}

function openMoveDialog(item) {
    movingItem.value = item;
    moveTargetParentId.value = item.parentId == null ? '' : String(item.parentId);
    moveDialogOpen.value = true;
}

function closeMoveDialog() {
    moveDialogOpen.value = false;
    movingItem.value = null;
    moveTargetParentId.value = '';
}

async function confirmMove() {
    if (!movingItem.value) {
        return;
    }
    moving.value = true;
    try {
        await moveKnowledgeNode(
            {
                kbId: props.knowledge.id,
                docId: movingItem.value.docId,
                targetParentId:
                    moveTargetParentId.value === '' ? null : Number(moveTargetParentId.value),
            },
            handleUnauthorized
        );
        closeMoveDialog();
        await refreshView();
    } catch (error) {
        await alert({
            title: '移动失败',
            message: error?.message || '移动目录失败，请稍后重试。',
        });
    } finally {
        moving.value = false;
    }
}

function selectMoveTarget(value) {
    moveTargetParentId.value = value == null ? '' : String(value);
}

watch(
    () => [props.knowledge?.id, props.knowledge?.parentId],
    () => {
        currentParentId.value = props.knowledge?.parentId ?? null;
        refreshView();
    },
    { immediate: true }
);

watch(currentParentId, () => {
    loadChildren();
});
</script>

<template>
    <PageLayout
        data-component="AdminKnowledgeDetailPanel"
        class="admin-page admin-page--knowledge-detail"
        header-class="h-auto py-5"
        body-class="bg-slate-50"
    >
        <template #left>
            <div class="flex items-center gap-3">
                <button
                    type="button"
                    class="flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-50"
                    @click="emit('back')"
                >
                    <span class="material-symbols-outlined">arrow_back</span>
                </button>
                <div>
                    <h1 class="text-2xl font-semibold text-primary">{{ props.knowledge.name }}</h1>
                    <p class="text-sm text-slate-500">
                        {{ currentFolder ? currentFolder.name : props.knowledge.name }}
                    </p>
                    <p v-if="props.knowledge.kbCode" class="mt-1 text-xs font-mono text-slate-400">
                        Code: {{ props.knowledge.kbCode }}
                    </p>
                </div>
            </div>
        </template>

        <template #right>
            <div class="flex items-center gap-2">
                <button
                    type="button"
                    class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-5 py-2 font-medium text-slate-700 shadow-sm transition-all hover:border-slate-300 hover:bg-slate-50"
                    @click="emit('edit-knowledge', props.knowledge)"
                >
                    <span class="material-symbols-outlined fill-0 text-xl">edit_square</span>
                    <span>编辑信息</span>
                </button>
                <button
                    type="button"
                    class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-5 py-2 font-medium text-slate-700 shadow-sm transition-all hover:border-slate-300 hover:bg-slate-50"
                    @click="createFolder"
                >
                    <span class="material-symbols-outlined fill-0 text-xl">create_new_folder</span>
                    <span>新建目录</span>
                </button>
                <button
                    type="button"
                    class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-5 py-2 font-medium text-slate-700 shadow-sm transition-all hover:border-slate-300 hover:bg-slate-50"
                    :disabled="uploadingFile"
                    @click="triggerFileUpload"
                >
                    <input
                        ref="fileInputRef"
                        type="file"
                        class="hidden"
                        accept=".pdf,.txt,.md,.doc,.docx"
                        @change="handleFileChange"
                    />
                    <span class="material-symbols-outlined fill-0 text-xl">upload</span>
                    <span>{{ uploadingFile ? '上传中...' : '上传到当前目录' }}</span>
                </button>
            </div>
        </template>

        <div class="flex h-full min-h-0 flex-col overflow-hidden bg-white">
            <div class="border-b border-slate-100 px-8 py-4 text-sm text-slate-500">
                <button
                    type="button"
                    class="font-medium text-slate-700 hover:text-primary"
                    @click="goToCrumb(null)"
                >
                    {{ props.knowledge.name }}
                </button>
                <template v-for="crumb in breadcrumbs" :key="crumb.docId">
                    <span class="px-2 text-slate-300">/</span>
                    <button
                        type="button"
                        class="font-medium text-slate-700 hover:text-primary"
                        @click="goToCrumb(crumb)"
                    >
                        {{ crumb.name }}
                    </button>
                </template>
            </div>

            <div
                v-if="loadError"
                class="mx-8 mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
            >
                {{ loadError }}
            </div>

            <div
                v-if="!loading && items.length === 0 && !loadError"
                class="flex flex-1 items-center justify-center px-8"
            >
                <div
                    class="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-10 py-12 text-center"
                >
                    <div
                        class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-white text-slate-400 shadow-sm"
                    >
                        <span class="material-symbols-outlined text-3xl">folder_open</span>
                    </div>
                    <h3 class="text-lg font-semibold text-slate-800">当前目录为空</h3>
                    <p class="mt-2 text-sm text-slate-500">
                        可以先新建目录，或者直接上传文件到当前目录。
                    </p>
                </div>
            </div>

            <div v-else class="custom-scrollbar flex-1 overflow-y-auto">
                <table class="w-full border-collapse text-left">
                    <thead class="sticky top-0 z-10 bg-white">
                        <tr class="border-b border-slate-100">
                            <th class="px-8 py-4 text-sm font-semibold text-slate-500">名称</th>
                            <th class="px-8 py-4 text-sm font-semibold text-slate-500">类型</th>
                            <th class="px-8 py-4 text-sm font-semibold text-slate-500">大小</th>
                            <th class="px-8 py-4 text-sm font-semibold text-slate-500">状态</th>
                            <th class="px-8 py-4 text-sm font-semibold text-slate-500">上传时间</th>
                            <th class="px-8 py-4 text-sm font-semibold text-slate-500">操作</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-50">
                        <tr
                            v-for="item in items"
                            :key="item.docId"
                            class="transition-colors hover:bg-slate-50"
                            :class="'cursor-pointer'"
                            @click="openDocument(item)"
                        >
                            <td class="px-8 py-4">
                                <div class="flex items-center gap-3">
                                    <template v-if="item.isFolder === 1">
                                        <span class="material-symbols-outlined text-amber-500"
                                            >folder</span
                                        >
                                        <span class="truncate text-sm font-medium text-slate-700">{{
                                            item.name
                                        }}</span>
                                    </template>
                                    <template v-else>
                                        <span class="material-symbols-outlined text-slate-400">{{
                                            fileIcon(item.fileType)
                                        }}</span>
                                        <div class="min-w-0">
                                            <div
                                                class="truncate text-sm font-medium text-slate-700"
                                            >
                                                {{ item.name }}
                                            </div>
                                            <div
                                                v-if="item.errorMessage"
                                                :title="item.errorMessage"
                                                class="mt-1 max-w-[32rem] truncate text-xs text-red-500"
                                            >
                                                {{ item.errorMessageSummary }}
                                            </div>
                                        </div>
                                    </template>
                                </div>
                            </td>
                            <td class="px-8 py-4 text-sm text-slate-500">
                                {{ item.isFolder === 1 ? '文件夹' : item.fileType || '-' }}
                            </td>
                            <td class="px-8 py-4 text-sm text-slate-500">
                                {{ item.isFolder === 1 ? '-' : formatFileSize(item.fileSize) }}
                            </td>
                            <td class="px-8 py-4">
                                <span
                                    v-if="item.isFolder !== 1"
                                    class="rounded-full px-2.5 py-1 text-xs font-medium"
                                    :class="statusClass(item.status)"
                                >
                                    {{ statusText(item.status) }}
                                </span>
                                <span
                                    v-else
                                    class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600"
                                >
                                    目录
                                </span>
                            </td>
                            <td class="px-8 py-4 text-sm text-slate-500">
                                {{ item.uploadTime || '-' }}
                            </td>
                            <td class="px-8 py-4">
                                <div class="flex items-center gap-2">
                                    <DirectoryActionsMenu
                                        v-if="item.isFolder === 1"
                                        @click.stop
                                        @move="openMoveDialog(item)"
                                        @rename="renameFolder(item)"
                                        @delete="removeFolder(item)"
                                    />
                                    <template v-else>
                                        <button
                                            v-if="item.status === 0"
                                            type="button"
                                            class="rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-700 transition-colors hover:bg-blue-100"
                                            @click.stop="processPendingDocument(item)"
                                        >
                                            处理
                                        </button>
                                        <button
                                            v-if="item.status === 3"
                                            type="button"
                                            class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700 transition-colors hover:bg-amber-100"
                                            @click.stop="rerunDocument(item)"
                                        >
                                            重试
                                        </button>
                                        <button
                                            type="button"
                                            class="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50"
                                            @click.stop="openMoveDialog(item)"
                                        >
                                            移动
                                        </button>
                                        <button
                                            type="button"
                                            class="rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
                                            @click.stop="removeDocument(item)"
                                        >
                                            删除
                                        </button>
                                    </template>
                                </div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <div
            v-if="moveDialogOpen"
            class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/30 px-4"
            @click.self="closeMoveDialog"
        >
            <div class="w-full max-w-md rounded-3xl bg-white p-6 shadow-2xl">
                <div class="mb-4">
                    <h3 class="text-lg font-semibold text-slate-800">移动到目录</h3>
                    <p class="mt-1 text-sm text-slate-500">
                        {{ movingItem?.name }} 将被移动到新的目录层级。
                    </p>
                </div>

                <div class="space-y-2">
                    <label class="block text-sm font-medium text-slate-700">目标目录</label>
                    <div
                        class="custom-scrollbar max-h-72 overflow-y-auto rounded-2xl border border-slate-200 bg-slate-50/80 p-3"
                    >
                        <button
                            type="button"
                            class="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm transition-colors"
                            :class="
                                moveTargetParentId === ''
                                    ? 'bg-blue-50 font-medium text-primary'
                                    : 'text-slate-700 hover:bg-white'
                            "
                            @click="selectMoveTarget(null)"
                        >
                            <span class="material-symbols-outlined text-[18px] text-slate-400"
                                >home_storage</span
                            >
                            <span>根目录</span>
                        </button>

                        <div v-if="moveTargetFolders.length" class="mt-2 space-y-1">
                            <template v-for="node in moveTargetFolders" :key="node.docId">
                                <div class="space-y-1">
                                    <button
                                        type="button"
                                        class="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm transition-colors"
                                        :class="
                                            moveTargetParentId === String(node.docId)
                                                ? 'bg-blue-50 font-medium text-primary'
                                                : 'text-slate-700 hover:bg-white'
                                        "
                                        @click="selectMoveTarget(node.docId)"
                                    >
                                        <span
                                            class="material-symbols-outlined text-[18px] text-amber-500"
                                            >folder</span
                                        >
                                        <span class="truncate">{{ node.name }}</span>
                                    </button>

                                    <div
                                        v-if="node.children?.length"
                                        class="ml-5 space-y-1 border-l border-slate-200 pl-3"
                                    >
                                        <template v-for="child in node.children" :key="child.docId">
                                            <div class="space-y-1">
                                                <button
                                                    type="button"
                                                    class="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm transition-colors"
                                                    :class="
                                                        moveTargetParentId === String(child.docId)
                                                            ? 'bg-blue-50 font-medium text-primary'
                                                            : 'text-slate-700 hover:bg-white'
                                                    "
                                                    @click="selectMoveTarget(child.docId)"
                                                >
                                                    <span
                                                        class="material-symbols-outlined text-[18px] text-amber-500"
                                                        >folder</span
                                                    >
                                                    <span class="truncate">{{ child.name }}</span>
                                                </button>

                                                <div
                                                    v-if="child.children?.length"
                                                    class="ml-5 space-y-1 border-l border-slate-200 pl-3"
                                                >
                                                    <button
                                                        v-for="grandChild in child.children"
                                                        :key="grandChild.docId"
                                                        type="button"
                                                        class="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm transition-colors"
                                                        :class="
                                                            moveTargetParentId ===
                                                            String(grandChild.docId)
                                                                ? 'bg-blue-50 font-medium text-primary'
                                                                : 'text-slate-700 hover:bg-white'
                                                        "
                                                        @click="selectMoveTarget(grandChild.docId)"
                                                    >
                                                        <span
                                                            class="material-symbols-outlined text-[18px] text-amber-500"
                                                            >folder</span
                                                        >
                                                        <span class="truncate">{{
                                                            grandChild.name
                                                        }}</span>
                                                    </button>
                                                </div>
                                            </div>
                                        </template>
                                    </div>
                                </div>
                            </template>
                        </div>
                        <div v-else class="px-3 py-6 text-center text-sm text-slate-500">
                            当前没有可移动到的目录。
                        </div>
                    </div>
                </div>

                <div class="mt-6 flex items-center justify-end gap-3">
                    <button
                        type="button"
                        class="rounded-lg border border-slate-200 px-5 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50"
                        @click="closeMoveDialog"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="rounded-lg bg-primary px-5 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                        :disabled="moving"
                        @click="confirmMove"
                    >
                        {{ moving ? '移动中...' : '确认移动' }}
                    </button>
                </div>
            </div>
        </div>
    </PageLayout>
</template>
