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
            <button type="button" class="action-btn" title="下载" @click.stop="handleDownload">
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

    if (
        contentType.startsWith('image/') ||
        ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].includes(ext)
    ) {
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

    if (
        contentType.startsWith('image/') ||
        ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].includes(ext)
    ) {
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
        contentType.includes('pdf') ||
        contentType.startsWith('image/') ||
        [
            '.html',
            '.htm',
            '.pdf',
            '.docx',
            '.xlsx',
            '.xls',
            '.pptx',
            '.json',
            '.md',
            '.markdown',
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
 * 将 objectName 转换为 artifactId (Base64 URL 编码)
 */
function toArtifactId(objectName) {
    if (!objectName) return '';
    // Base64 URL 编码 (无填充)
    return btoa(objectName).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * 预览文件
 */
function handlePreview() {
    const objectName = props.file.objectName;
    const fileName = props.file.displayName || 'preview';

    console.log('[FilePreviewItem] handlePreview:', {
        file: props.file,
        objectName,
        fileName,
    });

    if (objectName) {
        // 使用 artifacts API (不需要认证)
        const artifactId = toArtifactId(objectName);
        const url = `/api/files/artifacts/${artifactId}/preview?fileName=${encodeURIComponent(fileName)}`;
        console.log('[FilePreviewItem] preview URL:', url);
        window.open(url, '_blank');
    } else {
        console.error('[FilePreviewItem] No objectName, fallback to fileCode');
        // Fallback: 使用 fileCode API (需要认证)
        handleDownloadWithAuth();
    }
}

/**
 * 下载文件
 */
function handleDownload() {
    const objectName = props.file.objectName;
    const fileName = props.file.displayName || 'download';

    console.log('[FilePreviewItem] handleDownload:', {
        file: props.file,
        objectName,
        fileName,
    });

    if (objectName) {
        // 使用 artifacts API (不需要认证)
        const artifactId = toArtifactId(objectName);
        const url = `/api/files/artifacts/${artifactId}/download?fileName=${encodeURIComponent(fileName)}`;
        console.log('[FilePreviewItem] download URL:', url);
        window.open(url, '_blank');
    } else {
        console.error('[FilePreviewItem] No objectName, fallback to fileCode');
        // Fallback: 使用 fileCode API (需要认证)
        handleDownloadWithAuth();
    }
}

/**
 * 使用认证方式下载 (fallback)
 */
async function handleDownloadWithAuth() {
    const fileCode = props.file.fileCode;
    if (!fileCode) {
        console.error('[FilePreviewItem] No fileCode');
        return;
    }

    try {
        const response = await fetch(`/api/files/assets/${fileCode}/download`, {
            credentials: 'include',
            headers: {
                Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}`,
            },
        });

        if (!response.ok) {
            console.error('[FilePreviewItem] Download failed:', response.status);
            return;
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = props.file.displayName || 'download';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch (err) {
        console.error('[FilePreviewItem] Download error:', err);
    }
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
