<template>
    <section
        class="flex h-full min-h-0 flex-col overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm"
    >
        <header class="shrink-0 border-b border-slate-200 px-6 py-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h2 class="text-base font-semibold text-slate-900">文件夹</h2>
                    <p class="mt-1 text-sm text-slate-500">
                        统一查看上传文件、运行产物和临时文件，按虚拟目录结构组织。
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <label class="relative block min-w-0 sm:w-[340px]">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-slate-400"
                            >search</span
                        >
                        <input
                            v-model="searchKeyword"
                            type="text"
                            placeholder="搜索文件名、路径、消息 ID"
                            class="w-full rounded-2xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-primary focus:bg-white focus:ring-1 focus:ring-primary"
                        />
                    </label>
                    <button
                        type="button"
                        class="inline-flex h-[42px] items-center justify-center rounded-2xl border border-slate-200 bg-white px-4 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="loading"
                        @click="loadAssets"
                    >
                        {{ loading ? '刷新中...' : '刷新列表' }}
                    </button>
                </div>
            </div>

            <div class="mt-4 flex flex-wrap items-center gap-2">
                <span
                    class="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600"
                >
                    全部 {{ totalCount }}
                </span>
                <span
                    v-for="summary in roleSummaries"
                    :key="summary.key"
                    :class="[
                        'inline-flex items-center rounded-full px-3 py-1 text-xs font-medium',
                        summary.toneClass,
                    ]"
                >
                    {{ summary.label }} {{ summary.count }}
                </span>
            </div>

            <div
                v-if="loadError"
                class="mt-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
            >
                {{ loadError }}
            </div>
        </header>

        <div class="min-h-0 flex-1 overflow-auto">
            <table class="min-w-full divide-y divide-slate-200 text-sm">
                <thead
                    class="bg-slate-50/80 text-left text-xs uppercase tracking-[0.16em] text-slate-400"
                >
                    <tr>
                        <th class="px-6 py-4 font-medium">文件名称</th>
                        <th class="px-6 py-4 font-medium">创建时间</th>
                        <th class="px-6 py-4 text-right font-medium">文件大小</th>
                        <th class="px-6 py-4 text-right font-medium">操作</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                    <tr v-if="loading && !treeRows.length">
                        <td colspan="4" class="px-6 py-14 text-center text-sm text-slate-400">
                            正在加载文件列表...
                        </td>
                    </tr>
                    <tr v-else-if="!treeRows.length">
                        <td colspan="4" class="px-6 py-14 text-center text-sm text-slate-400">
                            {{ searchKeyword ? '没有匹配的文件结果。' : '当前还没有文件。' }}
                        </td>
                    </tr>
                    <tr
                        v-for="row in treeRows"
                        :key="row.key"
                        class="transition-colors hover:bg-slate-50/80"
                    >
                        <template v-if="row.kind === 'folder'">
                            <td class="px-6 py-2.5">
                                <button
                                    type="button"
                                    class="flex w-full items-center gap-3 text-left"
                                    @click="toggleFolder(row.id)"
                                >
                                    <span
                                        class="material-symbols-outlined text-[18px] text-slate-400"
                                        :style="{ marginLeft: `${row.level * 20}px` }"
                                    >
                                        {{ isExpanded(row.id) ? 'expand_more' : 'chevron_right' }}
                                    </span>
                                    <span
                                        class="material-symbols-outlined text-[20px]"
                                        :class="row.virtualRoot ? 'text-sky-500' : 'text-amber-500'"
                                    >
                                        {{ row.virtualRoot ? row.icon : 'folder' }}
                                    </span>
                                    <div class="min-w-0">
                                        <div class="flex items-center gap-2">
                                            <span class="truncate font-medium text-slate-800">
                                                {{ row.name }}
                                            </span>
                                            <span
                                                v-if="row.virtualRoot"
                                                :class="[
                                                    'rounded-full px-2 py-0.5 text-[11px] font-medium',
                                                    row.roleToneClass,
                                                ]"
                                            >
                                                {{ row.roleLabel }}
                                            </span>
                                        </div>
                                        <div
                                            v-if="row.pathText && row.pathText !== '/'"
                                            class="mt-1 text-xs text-slate-400"
                                        >
                                            {{ row.pathText }}
                                        </div>
                                    </div>
                                </button>
                            </td>
                            <td class="px-6 py-2.5 text-slate-500">
                                {{ formatDate(row.latestTime) }}
                            </td>
                            <td class="px-6 py-2.5 text-right text-slate-300">-</td>
                            <td class="px-6 py-2.5 text-right text-slate-300">-</td>
                        </template>

                        <template v-else>
                            <td class="px-6 py-2.5">
                                <div
                                    class="flex items-center gap-3"
                                    :style="{ paddingLeft: `${row.level * 20 + 38}px` }"
                                >
                                    <div
                                        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-500"
                                    >
                                        <span class="material-symbols-outlined text-[18px]">
                                            {{ resolveFileIcon(row.contentType, row.displayName) }}
                                        </span>
                                    </div>
                                    <div class="min-w-0">
                                        <div class="flex items-center gap-2">
                                            <span class="truncate font-medium text-slate-900">
                                                {{ formatDisplayName(row) }}
                                            </span>
                                            <span
                                                :class="[
                                                    'rounded-full px-2 py-0.5 text-[11px] font-medium',
                                                    resolveRoleMeta(row.fileRole).toneClass,
                                                ]"
                                            >
                                                {{ resolveRoleMeta(row.fileRole).label }}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </td>
                            <td class="px-6 py-2.5 text-slate-500">
                                <div>{{ formatDate(row.updatedAt || row.createdAt) }}</div>
                            </td>
                            <td class="px-6 py-2.5 text-right font-medium text-slate-700">
                                {{ formatSize(row.sizeBytes) }}
                            </td>
                            <td class="px-6 py-2.5 text-right">
                                <div class="flex items-center justify-end gap-2">
                                    <button
                                        v-if="isPreviewable(row)"
                                        type="button"
                                        class="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 transition hover:bg-slate-50"
                                        @click="handlePreview(row)"
                                    >
                                        预览
                                    </button>
                                    <button
                                        type="button"
                                        class="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 transition hover:bg-slate-50"
                                        @click="handleDownload(row)"
                                    >
                                        下载
                                    </button>
                                </div>
                            </td>
                        </template>
                    </tr>
                </tbody>
            </table>
        </div>
    </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    downloadCurrentUserFileAsset,
    listCurrentUserFileAssets,
    previewCurrentUserFileAsset,
} from '@/api/chat-files';
import { ROUTE_PATHS } from '@/router/routePaths';

const emit = defineEmits(['stats-change']);
const router = useRouter();

const loading = ref(false);
const loadError = ref('');
const items = ref([]);
const searchKeyword = ref('');
const expandedFolderIds = ref([]);

const ROLE_DEFINITIONS = Object.freeze({
    UPLOAD: {
        key: 'UPLOAD',
        label: '上传',
        icon: 'upload_file',
        toneClass: 'bg-sky-50 text-sky-700',
    },
    ARTIFACT: {
        key: 'ARTIFACT',
        label: '产物',
        icon: 'deployed_code',
        toneClass: 'bg-emerald-50 text-emerald-700',
    },
    TEMP: {
        key: 'TEMP',
        label: '临时',
        icon: 'data_object',
        toneClass: 'bg-amber-50 text-amber-700',
    },
});
const totalCount = computed(() => items.value.length);
const totalSizeBytes = computed(() =>
    items.value.reduce((sum, item) => sum + Number(item.sizeBytes || 0), 0)
);
const duplicatedNameCountMap = computed(() => {
    const map = new Map();
    items.value.forEach(item => {
        const key = String(item.displayName || '').trim();
        if (!key) {
            return;
        }
        map.set(key, (map.get(key) || 0) + 1);
    });
    return map;
});
const roleSummaries = computed(() =>
    Object.values(ROLE_DEFINITIONS).map(meta => ({
        ...meta,
        count: items.value.filter(item => item.fileRole === meta.key).length,
    }))
);
const filteredItems = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    if (!keyword) {
        return items.value;
    }
    return items.value.filter(item =>
        [
            item.displayName,
            item.virtualPath,
            item.contentType,
            item.fileRole,
            item.originMessageId,
            item.originEventId,
        ].some(value =>
            String(value || '')
                .toLowerCase()
                .includes(keyword)
        )
    );
});
const treeRows = computed(() => flattenTree(buildRoleTree(filteredItems.value)));

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function normalizeItem(item) {
    const virtualPath = String(item?.virtualPath || '').trim() || '/';
    const normalizedVirtualPath = virtualPath.startsWith('/') ? virtualPath : `/${virtualPath}`;
    const slashIndex = normalizedVirtualPath.lastIndexOf('/');
    const folderPath = slashIndex > 0 ? normalizedVirtualPath.slice(0, slashIndex) : '/';
    const fileName = normalizedVirtualPath.split('/').filter(Boolean).pop() || '未命名文件';
    return {
        fileCode: item?.fileCode || '',
        fileRole: String(item?.fileRole || '')
            .trim()
            .toUpperCase(),
        status: item?.status || '',
        displayName: item?.displayName || fileName,
        virtualPath: normalizedVirtualPath,
        folderPath,
        folderSegments: folderPath === '/' ? [] : folderPath.split('/').filter(Boolean),
        contentType: item?.contentType || '',
        sizeBytes:
            typeof item?.sizeBytes === 'number' ? item.sizeBytes : Number(item?.sizeBytes || 0),
        originMessageId: item?.originMessageId ?? null,
        originEventId: item?.originEventId ?? null,
        localStatus: item?.localStatus || '',
        minioStatus: item?.minioStatus || '',
        createdAt: item?.createdAt || '',
        updatedAt: item?.updatedAt || '',
    };
}

async function loadAssets() {
    loading.value = true;
    loadError.value = '';
    try {
        const data = await listCurrentUserFileAssets(
            {
                pageNo: 1,
                pageSize: 200,
            },
            handleUnauthorized
        );
        const rawItems = Array.isArray(data?.items) ? data.items : [];
        items.value = rawItems.map(normalizeItem);
        seedExpandedFolders();
    } catch (error) {
        loadError.value = error?.message || '加载文件列表失败';
    } finally {
        loading.value = false;
    }
}

function formatDate(value) {
    if (!value) {
        return '-';
    }
    const text = String(value).trim();
    if (!text) {
        return '-';
    }
    const parsed = new Date(text);
    if (Number.isNaN(parsed.getTime())) {
        return text;
    }
    const yyyy = parsed.getFullYear();
    const mm = `${parsed.getMonth() + 1}`.padStart(2, '0');
    const dd = `${parsed.getDate()}`.padStart(2, '0');
    const hh = `${parsed.getHours()}`.padStart(2, '0');
    const mi = `${parsed.getMinutes()}`.padStart(2, '0');
    return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
}

function formatSize(value) {
    const size = Number(value || 0);
    if (!Number.isFinite(size) || size <= 0) {
        return '0 B';
    }
    if (size < 1024) {
        return `${size} B`;
    }
    if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} KB`;
    }
    if (size < 1024 * 1024 * 1024) {
        return `${(size / (1024 * 1024)).toFixed(1)} MB`;
    }
    return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function resolveRoleMeta(roleKey) {
    return (
        ROLE_DEFINITIONS[roleKey] || {
            key: roleKey || 'UNKNOWN',
            label: roleKey || 'UNKNOWN',
            icon: 'folder',
            toneClass: 'bg-slate-100 text-slate-600',
        }
    );
}

function resolveFileIcon(contentType, displayName) {
    const normalizedContentType = String(contentType || '').toLowerCase();
    const normalizedName = String(displayName || '').toLowerCase();
    if (normalizedContentType.startsWith('image/')) {
        return 'image';
    }
    if (normalizedContentType.includes('pdf') || normalizedName.endsWith('.pdf')) {
        return 'picture_as_pdf';
    }
    if (
        normalizedContentType.includes('sheet') ||
        normalizedName.endsWith('.xlsx') ||
        normalizedName.endsWith('.csv')
    ) {
        return 'table_chart';
    }
    if (
        normalizedContentType.includes('zip') ||
        normalizedName.endsWith('.zip') ||
        normalizedName.endsWith('.tar')
    ) {
        return 'folder_zip';
    }
    if (
        normalizedContentType.startsWith('text/') ||
        normalizedName.endsWith('.md') ||
        normalizedName.endsWith('.txt') ||
        normalizedName.endsWith('.json') ||
        normalizedName.endsWith('.log')
    ) {
        return 'description';
    }
    return 'draft';
}

function isPreviewable(row) {
    const normalizedContentType = String(row?.contentType || '').toLowerCase();
    const normalizedName = String(row?.displayName || '').toLowerCase();
    return (
        normalizedContentType.startsWith('text/') ||
        normalizedContentType === 'application/json' ||
        normalizedName.endsWith('.md') ||
        normalizedName.endsWith('.txt') ||
        normalizedName.endsWith('.html') ||
        normalizedName.endsWith('.htm') ||
        normalizedName.endsWith('.json') ||
        normalizedName.endsWith('.log') ||
        normalizedName.endsWith('.csv')
    );
}

function formatDisplayName(row) {
    const baseName = String(row?.displayName || '').trim() || '未命名文件';
    if ((duplicatedNameCountMap.value.get(baseName) || 0) <= 1) {
        return baseName;
    }
    if (row?.originMessageId) {
        return `${baseName} · M${row.originMessageId}`;
    }
    if (row?.originEventId) {
        return `${baseName} · E${row.originEventId}`;
    }
    if (row?.fileCode) {
        return `${baseName} · ${String(row.fileCode).slice(-6)}`;
    }
    return baseName;
}

function downloadBlobFile(filename, blob) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename || 'download';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
}

async function handleDownload(row) {
    try {
        const result = await downloadCurrentUserFileAsset(row.fileCode, handleUnauthorized);
        downloadBlobFile(result.filename || row.displayName, result.blob);
    } catch (error) {
        await alert({
            title: '下载失败',
            message: error?.message || '下载文件失败',
        });
    }
}

async function handlePreview(row) {
    try {
        const result = await previewCurrentUserFileAsset(row.fileCode, handleUnauthorized);
        const blob = new Blob([result.blob], {
            type: result.contentType || row.contentType || 'text/plain',
        });
        const objectUrl = URL.createObjectURL(blob);
        const child = window.open(objectUrl, '_blank', 'noopener,noreferrer');
        if (!child) {
            URL.revokeObjectURL(objectUrl);
            throw new Error('浏览器拦截了预览窗口');
        }
        window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
        await alert({
            title: '预览失败',
            message: error?.message || '预览文件失败',
        });
    }
}

function createFolderNode({ id, name, roleKey, virtualRoot = false, pathText = '/' }) {
    const roleMeta = resolveRoleMeta(roleKey);
    return {
        kind: 'folder',
        key: `folder:${id}`,
        id,
        name,
        roleKey,
        roleLabel: roleMeta.label,
        roleToneClass: roleMeta.toneClass,
        icon: roleMeta.icon,
        virtualRoot,
        pathText,
        itemCount: 0,
        totalSizeBytes: 0,
        latestTime: '',
        folders: new Map(),
        files: [],
    };
}

function buildRoleTree(list) {
    const roleRoots = [];
    const rootMap = new Map();
    list.forEach(item => {
        const roleMeta = resolveRoleMeta(item.fileRole);
        let roleRoot = rootMap.get(roleMeta.key);
        if (!roleRoot) {
            roleRoot = createFolderNode({
                id: `role:${roleMeta.key}`,
                name: `${roleMeta.label}文件`,
                roleKey: roleMeta.key,
                virtualRoot: true,
                pathText: '/',
            });
            rootMap.set(roleMeta.key, roleRoot);
            roleRoots.push(roleRoot);
        }

        updateFolderAggregate(roleRoot, item);
        roleRoot.files.push(item);
    });
    return roleRoots.sort(sortFolderNodes);
}

function updateFolderAggregate(folder, item) {
    folder.itemCount += 1;
    folder.totalSizeBytes += Number(item.sizeBytes || 0);
    folder.latestTime = pickLatestTime(folder.latestTime, item.updatedAt || item.createdAt);
}

function pickLatestTime(left, right) {
    return timeRank(left) >= timeRank(right) ? left : right;
}

function timeRank(value) {
    if (!value) {
        return 0;
    }
    const parsed = new Date(String(value).trim()).getTime();
    return Number.isNaN(parsed) ? 0 : parsed;
}

function sortFolderNodes(left, right) {
    return left.name.localeCompare(right.name, 'zh-Hans-CN');
}

function sortFileItems(left, right) {
    const timeDelta =
        timeRank(right.updatedAt || right.createdAt) - timeRank(left.updatedAt || left.createdAt);
    if (timeDelta !== 0) {
        return timeDelta;
    }
    return String(left.displayName || '').localeCompare(
        String(right.displayName || ''),
        'zh-Hans-CN'
    );
}

function flattenTree(roleRoots) {
    const rows = [];
    roleRoots.forEach(root => appendFolderRows(root, 0, rows));
    return rows;
}

function appendFolderRows(folder, level, rows) {
    rows.push({
        kind: 'folder',
        key: folder.key,
        id: folder.id,
        name: folder.name,
        level,
        roleLabel: folder.roleLabel,
        roleToneClass: folder.roleToneClass,
        totalSizeBytes: folder.totalSizeBytes,
        itemCount: folder.itemCount,
        latestTime: folder.latestTime,
        pathText: folder.pathText,
        icon: folder.icon,
        virtualRoot: folder.virtualRoot,
    });
    if (!isExpanded(folder.id)) {
        return;
    }
    Array.from(folder.folders.values())
        .sort(sortFolderNodes)
        .forEach(child => appendFolderRows(child, level + 1, rows));
    folder.files
        .slice()
        .sort(sortFileItems)
        .forEach(file => {
            rows.push({
                ...file,
                kind: 'file',
                key: `file:${file.fileCode}`,
                level: level + 1,
            });
        });
}

function isExpanded(folderId) {
    if (searchKeyword.value.trim()) {
        return true;
    }
    return expandedFolderIds.value.includes(folderId);
}

function toggleFolder(folderId) {
    if (searchKeyword.value.trim()) {
        return;
    }
    if (expandedFolderIds.value.includes(folderId)) {
        expandedFolderIds.value = expandedFolderIds.value.filter(id => id !== folderId);
        return;
    }
    expandedFolderIds.value = [...expandedFolderIds.value, folderId];
}

function seedExpandedFolders() {
    expandedFolderIds.value = Array.from(
        new Set(
            items.value
                .map(item => resolveRoleMeta(item.fileRole).key)
                .filter(Boolean)
                .map(roleKey => `role:${roleKey}`)
        )
    );
}

watch(
    () => [totalCount.value, totalSizeBytes.value],
    () => {
        emit('stats-change', {
            totalCount: totalCount.value,
            totalSizeBytes: totalSizeBytes.value,
        });
    },
    {
        immediate: true,
    }
);

onMounted(() => {
    loadAssets();
});
</script>
