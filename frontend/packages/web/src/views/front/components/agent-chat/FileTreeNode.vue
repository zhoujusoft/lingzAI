<template>
    <div class="tree-node">
        <!-- Folder Node -->
        <div v-if="!node.isFile" class="folder-node">
            <button
                type="button"
                class="folder-header"
                :style="{ paddingLeft: `${depth * 12 + 8}px` }"
                @click="expanded = !expanded"
            >
                <span class="material-symbols-outlined folder-icon" :class="{ expanded }">
                    chevron_right
                </span>
                <span class="material-symbols-outlined folder-type-icon">folder</span>
                <span class="folder-name">{{ node.name }}</span>
                <span v-if="fileCount > 0" class="file-count">{{ fileCount }}</span>
            </button>
            <transition name="folder-expand">
                <div v-show="expanded" class="folder-children">
                    <FileTreeNode
                        v-for="child in node.children"
                        :key="child.key"
                        :node="child"
                        :depth="depth + 1"
                        @preview-file="emit('preview-file', $event)"
                    />
                </div>
            </transition>
        </div>

        <!-- File Node -->
        <div
            v-else
            class="file-node"
            :class="{ 'file-node-previewable': isPreviewable }"
            :style="{ paddingLeft: `${depth * 12 + 8}px` }"
            @dblclick.stop="handleFileDoubleClick"
        >
            <span class="material-symbols-outlined file-icon" :class="iconClass">{{
                iconName
            }}</span>
            <span class="file-name">{{ node.name }}</span>
            <div class="file-actions">
                <button
                    v-if="isPreviewable"
                    type="button"
                    class="action-btn"
                    title="预览"
                    aria-label="预览文件"
                    @click.stop="handlePreview"
                >
                    <span class="material-symbols-outlined text-sm">visibility</span>
                </button>
                <button
                    type="button"
                    class="action-btn"
                    title="下载"
                    aria-label="下载文件"
                    @click.stop="handleDownload"
                >
                    <span class="material-symbols-outlined text-sm">download</span>
                </button>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
    node: {
        type: Object,
        required: true,
    },
    depth: {
        type: Number,
        default: 0,
    },
});

const emit = defineEmits(['preview-file']);

const expanded = ref(true);

// 计算文件夹下的文件数量
const fileCount = computed(() => {
    if (props.node.isFile) return 0;

    function countFiles(node) {
        if (node.isFile) return 1;
        let count = 0;
        for (const child of Object.values(node.children || {})) {
            count += countFiles(child);
        }
        return count;
    }

    return countFiles(props.node);
});

// 获取文件扩展名
const fileExtension = computed(() => {
    const file = props.node.file;
    if (!file) return '';

    // 从 displayName 提取扩展名
    const displayName = file.displayName || props.node.name || '';
    const dotIndex = displayName.lastIndexOf('.');
    if (dotIndex >= 0) {
        return displayName.slice(dotIndex).toLowerCase();
    }
    return '';
});

// 文件图标名称
const iconName = computed(() => {
    const file = props.node.file;
    if (!file) return 'insert_drive_file';

    const ext = fileExtension.value;
    const contentType = (file.contentType || '').toLowerCase();

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
    if (contentType.includes('presentation') || ext === '.pptx') {
        return 'slideshow';
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
    const file = props.node.file;
    if (!file) return '';

    const ext = fileExtension.value;
    const contentType = (file.contentType || '').toLowerCase();

    if (
        contentType.startsWith('image/') ||
        ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].includes(ext)
    ) {
        return 'icon-image';
    }
    if (contentType.includes('pdf') || ext === '.pdf') {
        return 'icon-pdf';
    }
    if (['.html', '.htm', '.json'].includes(ext)) {
        return 'icon-code';
    }
    return '';
});

// 是否可预览
const isPreviewable = computed(() => {
    const file = props.node.file;
    if (!file) return false;

    const contentType = (file.contentType || '').toLowerCase();
    const ext = fileExtension.value;

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
 * 将 objectName 转换为 artifactId (Base64 URL 编码)
 * 使用 UTF-8 编码以支持中文等 Unicode 字符
 */
function toArtifactId(objectName) {
    if (!objectName) return '';
    // 使用 TextEncoder 获取 UTF-8 字节数组
    const bytes = new TextEncoder().encode(objectName);
    // 将字节数组转换为二进制字符串
    let binary = '';
    for (let i = 0; i < bytes.length; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    // 进行 Base64 编码并转换为 URL-safe 格式
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * 预览文件
 */
function handlePreview() {
    const file = props.node.file;
    if (!file) {
        console.warn('无法预览：文件信息缺失');
        return;
    }

    const objectName = file.objectName;
    const fileName = file.displayName || file.name || 'preview';

    if (!objectName) {
        console.warn('无法预览：文件对象名缺失');
        alert('该文件暂不支持预览');
        return;
    }

    try {
        const artifactId = toArtifactId(objectName);
        const previewUrl = `/api/files/artifacts/${artifactId}/preview?fileName=${encodeURIComponent(fileName)}`;
        const downloadUrl = `/api/files/artifacts/${artifactId}/download?fileName=${encodeURIComponent(fileName)}`;

        emit('preview-file', {
            title: fileName,
            fileName,
            src: previewUrl,
            downloadUrl,
            contentType: file.contentType || '',
            size: file.sizeBytes || file.fileSize || file.size || 0,
        });
    } catch (error) {
        console.error('预览失败:', error);
        alert('预览失败，请稍后重试');
    }
}

/**
 * 下载文件
 */
function handleDownload() {
    const file = props.node.file;
    if (!file) {
        console.warn('无法下载：文件信息缺失');
        return;
    }

    const objectName = file.objectName;
    const fileName = file.displayName || file.name || 'download';

    if (!objectName) {
        console.warn('无法下载：文件对象名缺失');
        alert('该文件暂不支持下载');
        return;
    }

    try {
        const artifactId = toArtifactId(objectName);
        const url = `/api/files/artifacts/${artifactId}/download?fileName=${encodeURIComponent(fileName)}`;

        // 使用隐藏的 <a> 元素触发下载，避免打开新标签页
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    } catch (error) {
        console.error('下载失败:', error);
        alert('下载失败，请稍后重试');
    }
}

function handleFileDoubleClick() {
    if (!isPreviewable.value) {
        return;
    }
    handlePreview();
}
</script>

<style scoped>
.tree-node {
    user-select: none;
}

/* Folder styles */
.folder-header {
    display: flex;
    align-items: center;
    gap: 4px;
    width: 100%;
    padding: 6px 8px;
    border: none;
    background: transparent;
    cursor: pointer;
    transition: background 0.15s ease;
    border-radius: 6px;
    margin: 1px 0;
}

.folder-header:hover {
    background: rgb(var(--color-bg-surface-alt));
}

.folder-icon {
    font-size: 18px;
    color: rgb(var(--color-text-muted));
    transition: transform 0.2s ease;
}

.folder-icon.expanded {
    transform: rotate(90deg);
}

.folder-type-icon {
    font-size: 18px;
    color: rgb(var(--color-accent) / 0.65);
}

.folder-name {
    flex: 1;
    text-align: left;
    font-size: 13px;
    font-weight: 500;
    color: rgb(var(--color-text-strong));
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.file-count {
    font-size: 11px;
    color: rgb(var(--color-text-muted));
    background: rgb(var(--color-bg-surface-alt));
    padding: 1px 6px;
    border-radius: 10px;
}

.folder-children {
    overflow: hidden;
}

/* File styles */
.file-node {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    transition: background 0.15s ease;
    border-radius: 6px;
    margin: 1px 0;
}

.file-node-previewable {
    cursor: pointer;
}

.file-node:hover {
    background: rgb(var(--color-bg-surface-alt));
}

.file-icon {
    font-size: 18px;
    color: rgb(var(--color-text-muted));
}

.file-icon.icon-image {
    color: rgb(var(--color-success) / 0.85);
}

.file-icon.icon-pdf {
    color: rgb(var(--color-danger) / 0.85);
}

.file-icon.icon-code {
    color: rgb(var(--color-accent) / 0.85);
}

.file-name {
    flex: 1;
    font-size: 13px;
    color: rgb(var(--color-text-body));
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.file-actions {
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity 0.15s ease;
}

.file-node:hover .file-actions {
    opacity: 1;
}

.action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border-radius: 6px;
    border: none;
    background: transparent;
    color: rgb(var(--color-text-muted));
    cursor: pointer;
    transition: all 0.15s ease;
}

.action-btn:hover {
    background: rgb(var(--color-bg-surface-alt));
    color: rgb(var(--color-text-body));
}

/* Folder expand animation */
.folder-expand-enter-active,
.folder-expand-leave-active {
    transition: all 0.2s ease;
}

.folder-expand-enter-from,
.folder-expand-leave-to {
    opacity: 0;
    max-height: 0;
}

.folder-expand-enter-to,
.folder-expand-leave-from {
    opacity: 1;
    max-height: 2000px;
}
</style>
