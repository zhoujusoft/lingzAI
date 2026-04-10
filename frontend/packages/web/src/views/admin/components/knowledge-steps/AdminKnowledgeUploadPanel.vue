<script setup>
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import KnowledgeStepProgress from './KnowledgeStepProgress.vue';
import PageLayout from '@/components/PageLayout.vue';
import { uploadDocument } from '@/api/datasets';
import { createKnowledgeBase, createKnowledgeBaseWithDocument, updateKnowledgeBase } from '@/api/knowledge-bases';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
});

const emit = defineEmits(['back', 'next-step']);

const router = useRouter();
const knowledgeName = ref(props.knowledge.name || '');
const knowledgeCode = ref(props.knowledge.kbCode || '');
const knowledgeDescription = ref(props.knowledge.description || '');
const selectedType = ref('rag');
const uploads = ref([]);
const fileInputRef = ref(null);
const isUploading = ref(false);
const uploadError = ref('');
const fieldErrors = ref({
    knowledgeName: '',
    knowledgeCode: '',
});
const activeKnowledgeId = ref(props.knowledge?.id || null);
const KB_CODE_PATTERN = /^[A-Za-z0-9._-]+$/;

const processingFile = computed(() => uploads.value.find(item => item.isUploading));
const isEditMode = computed(() => Boolean(activeKnowledgeId.value));
const currentTargetPath = computed(() => props.knowledge?.path || props.knowledge?.name || '根目录');
const submitButtonText = computed(() => {
    if (isUploading.value) {
        return '处理中...';
    }
    if (isEditMode.value) {
        return uploads.value.length ? '保存并上传' : '保存修改';
    }
    return uploads.value.length ? '创建并导入' : '创建';
});

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function triggerFileSelect() {
    fileInputRef.value?.click();
}

function handleFileChange(event) {
    const files = Array.from(event.target.files || []);
    if (!files.length) return;

    const maxSize = 20 * 1024 * 1024;
    const allowedExtensions = ['pdf', 'txt', 'md', 'docx'];
    const validFiles = [];

    files.forEach(file => {
        const ext = file.name.split('.').pop().toLowerCase();
        if (file.size > maxSize) {
            uploadError.value = `文件 "${file.name}" 超过 20MB 限制`;
            return;
        }
        if (!allowedExtensions.includes(ext)) {
            uploadError.value = `不支持的文件格式：${ext}`;
            return;
        }
        validFiles.push(file);
    });

    validFiles.forEach(file => {
        uploads.value.push({
            id: `file-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
            file,
            name: file.name,
            size: formatFileSize(file.size),
            ext: file.name.split('.').pop().toLowerCase(),
            status: 'pending',
            docId: null,
            isUploading: false,
            errorMessage: '',
        });
    });

    if (validFiles.length) {
        uploadError.value = '';
    }
    event.target.value = '';
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const index = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${Math.round((bytes / 1024 ** index) * 100) / 100} ${units[index]}`;
}

function removeUpload(id) {
    const item = uploads.value.find(entry => entry.id === id);
    if (item?.isUploading) {
        uploadError.value = '上传中的文件不能删除';
        return;
    }
    uploads.value = uploads.value.filter(entry => entry.id !== id);
}

async function startUpload() {
    fieldErrors.value.knowledgeName = '';
    fieldErrors.value.knowledgeCode = '';

    if (!knowledgeName.value.trim()) {
        fieldErrors.value.knowledgeName = '请输入知识库名称';
        return;
    }
    if (knowledgeCode.value.trim() && !KB_CODE_PATTERN.test(knowledgeCode.value.trim())) {
        fieldErrors.value.knowledgeCode = '编码仅支持字母、数字、点、下划线和中划线';
        return;
    }

    uploadError.value = '';

    try {
        if (!uploads.value.length) {
            isUploading.value = true;
            const result = isEditMode.value
                ? await updateKnowledgeBase(
                    {
                        kbId: activeKnowledgeId.value,
                        kbName: knowledgeName.value.trim(),
                        kbCode: knowledgeCode.value.trim(),
                        description: knowledgeDescription.value.trim(),
                    },
                    handleUnauthorized,
                )
                : await createKnowledgeBase(
                    {
                        kbName: knowledgeName.value.trim(),
                        kbCode: knowledgeCode.value.trim(),
                        description: knowledgeDescription.value.trim(),
                    },
                    handleUnauthorized,
                );
            if (!isEditMode.value && !result?.kbId) {
                throw new Error('知识库创建失败');
            }
            activeKnowledgeId.value = result?.kbId ?? activeKnowledgeId.value;
            knowledgeCode.value = result?.kbCode || knowledgeCode.value.trim();
            isUploading.value = false;
            await alert({
                title: isEditMode.value ? '保存成功' : '创建成功',
                message: isEditMode.value ? '知识库信息已保存。' : '知识库已创建成功。',
            });
            emit('next-step', {
                id: activeKnowledgeId.value,
                name: knowledgeName.value.trim(),
                kbCode: knowledgeCode.value.trim(),
                description: knowledgeDescription.value.trim(),
                parentId: null,
                path: '',
            });
            return;
        }

        const pendingFile = uploads.value.find(item => item.status === 'pending' || item.status === 'failed');
        if (!pendingFile) {
            return;
        }

        isUploading.value = true;
        pendingFile.status = 'uploading';
        pendingFile.isUploading = true;
        pendingFile.errorMessage = '';

        let result;
        if (!activeKnowledgeId.value) {
            result = await createKnowledgeBaseWithDocument(
                {
                    kbName: knowledgeName.value.trim(),
                    kbCode: knowledgeCode.value.trim(),
                    description: knowledgeDescription.value.trim(),
                    file: pendingFile.file,
                },
                handleUnauthorized,
            );
            if (!result?.kbId) {
                throw new Error('知识库创建失败');
            }
            activeKnowledgeId.value = result.kbId;
            knowledgeCode.value = result?.kbCode || knowledgeCode.value.trim();
        } else {
            await updateKnowledgeBase(
                {
                    kbId: activeKnowledgeId.value,
                    kbName: knowledgeName.value.trim(),
                    kbCode: knowledgeCode.value.trim(),
                    description: knowledgeDescription.value.trim(),
                },
                handleUnauthorized,
            );
            result = await uploadDocument({
                kbId: activeKnowledgeId.value,
                parentId: props.knowledge?.parentId ?? null,
                file: pendingFile.file,
                onUnauthorized: handleUnauthorized,
            });
        }

        pendingFile.docId = result.docId;
        pendingFile.status = 'completed';
        pendingFile.isUploading = false;
        isUploading.value = false;

        emit('next-step', {
            id: activeKnowledgeId.value,
            name: knowledgeName.value.trim(),
            kbCode: knowledgeCode.value.trim(),
            description: knowledgeDescription.value.trim(),
            parentId: props.knowledge?.parentId ?? null,
            path: props.knowledge?.path || '',
            document: {
                docId: result.docId,
                name: pendingFile.name,
            },
        });
    } catch (error) {
        const pendingFile = uploads.value.find(item => item.status === 'uploading');
        if (pendingFile) {
            pendingFile.status = 'failed';
            pendingFile.isUploading = false;
            pendingFile.errorMessage = error.message || '上传失败，请重试';
            uploadError.value = pendingFile.errorMessage;
        } else {
            uploadError.value = error.message || '创建失败，请重试';
        }
        isUploading.value = false;
    }
}

function handleRetry() {
    uploadError.value = '';
    startUpload();
}

function getFileIcon(ext) {
    if (ext === 'pdf') return 'picture_as_pdf';
    if (ext === 'docx' || ext === 'doc') return 'description';
    return 'article';
}

function getFileIconClass(ext) {
    if (ext === 'pdf') return 'bg-red-50 text-red-500';
    if (ext === 'docx' || ext === 'doc') return 'bg-blue-50 text-blue-600';
    return 'bg-slate-100 text-slate-600';
}

function getStatusColor(status) {
    if (status === 'uploading') return 'text-blue-500';
    if (status === 'completed') return 'text-green-500';
    if (status === 'failed') return 'text-red-500';
    return 'text-slate-400';
}

function getStatusLabel(status) {
    if (status === 'uploading') return '上传中...';
    if (status === 'completed') return '已上传，下一步配置分块';
    if (status === 'failed') return '失败';
    return '等待上传';
}

watch(
    () => props.knowledge,
    knowledge => {
        knowledgeName.value = knowledge?.name || '';
        knowledgeCode.value = knowledge?.kbCode || '';
        knowledgeDescription.value = knowledge?.description || '';
        activeKnowledgeId.value = knowledge?.id || null;
    },
    { immediate: true, deep: true },
);
</script>

<template>
    <PageLayout
        data-component="AdminKnowledgeUploadPanel"
        class="admin-page admin-page--knowledge-upload-step1"
        footer-class="px-8 py-6"
        body-class="min-h-0 flex flex-col bg-white"
    >
        <template #left>
            <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-50"
                @click="emit('back')"
            >
                <span class="material-symbols-outlined">arrow_back</span>
            </button>
        </template>

        <template #center>
            <KnowledgeStepProgress :current-step="1" />
        </template>

        <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto px-8 py-8">
            <div class="mx-auto w-full max-w-4xl space-y-10">
                <section class="space-y-4">
                    <label class="block text-base font-bold text-slate-800">知识库名称</label>
                    <input
                        v-model="knowledgeName"
                        type="text"
                        placeholder="请输入知识库名称"
                        class="w-full rounded-xl border px-4 py-3 outline-none transition-all placeholder:text-slate-400 focus:ring-2"
                        :class="fieldErrors.knowledgeName
                            ? 'border-red-300 bg-red-50/40 focus:border-red-300 focus:ring-red-100'
                            : 'border-slate-200 focus:border-primary focus:ring-primary/20'"
                    />
                    <p v-if="fieldErrors.knowledgeName" class="text-sm text-red-500">
                        {{ fieldErrors.knowledgeName }}
                    </p>
                    <div class="space-y-2">
                        <label class="block text-base font-bold text-slate-800">知识库编码</label>
                        <input
                            v-model="knowledgeCode"
                            type="text"
                            placeholder="留空则自动生成，例如 support_center_v1"
                            class="w-full rounded-xl border px-4 py-3 font-mono outline-none transition-all placeholder:text-slate-400 focus:ring-2"
                            :class="fieldErrors.knowledgeCode
                                ? 'border-red-300 bg-red-50/40 focus:border-red-300 focus:ring-red-100'
                                : 'border-slate-200 focus:border-primary focus:ring-primary/20'"
                        />
                        <p class="text-sm text-slate-500">
                            发布成工具后会用于稳定工具标识，建议使用可读且长期稳定的编码。
                        </p>
                        <p v-if="fieldErrors.knowledgeCode" class="text-sm text-red-500">
                            {{ fieldErrors.knowledgeCode }}
                        </p>
                    </div>
                    <p v-if="props.knowledge?.parentId" class="text-sm text-slate-500">
                        当前上传目录：{{ props.knowledge?.path || props.knowledge?.name }}
                    </p>
                    <div class="space-y-2">
                        <label class="block text-base font-bold text-slate-800">知识库描述</label>
                        <textarea
                            v-model="knowledgeDescription"
                            rows="4"
                            placeholder="请输入知识库描述"
                            class="w-full rounded-xl border border-slate-200 px-4 py-3 outline-none transition-all placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                        />
                    </div>
                </section>

                <section class="space-y-4">
                    <label class="block text-base font-bold text-slate-800">知识库类型</label>
                    <div class="grid grid-cols-2 gap-4">
                        <button
                            type="button"
                            class="relative flex items-center gap-4 rounded-2xl border-2 p-6 text-left"
                            :class="selectedType === 'rag' ? 'border-primary bg-blue-50/30' : 'border-slate-200 bg-slate-50/30'"
                            @click="selectedType = 'rag'"
                        >
                            <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-blue-100 text-primary">
                                <span class="material-symbols-outlined text-2xl">description</span>
                            </div>
                            <div>
                                <div class="font-bold text-slate-800">通用 RAG 知识库</div>
                                <div class="text-sm text-slate-500">基础检索增强生成</div>
                            </div>
                        </button>

                        <div class="relative flex cursor-not-allowed items-center gap-4 rounded-2xl border border-slate-200 bg-slate-50/30 p-6">
                            <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-slate-100 text-slate-400">
                                <span class="material-symbols-outlined text-2xl">psychology</span>
                            </div>
                            <div>
                                <div class="font-bold text-slate-800">深度版知识库</div>
                                <div class="text-sm text-slate-500">高精度语义理解与推理</div>
                            </div>
                            <div class="absolute right-4 top-4">
                                <span class="rounded bg-slate-200 px-2 py-0.5 text-[10px] font-bold text-slate-500">Coming Soon</span>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="space-y-4">
                    <div class="space-y-2">
                        <label class="block text-base font-bold text-slate-800">
                            {{ isEditMode ? '上传到当前知识库' : '上传文本文件' }}
                        </label>
                        <p v-if="isEditMode" class="text-sm text-slate-500">
                            当前目录：{{ currentTargetPath }}
                        </p>
                    </div>

                    <div v-if="uploadError" class="rounded-xl border border-red-200 bg-red-50 p-4">
                        <div class="flex items-start gap-3">
                            <span class="material-symbols-outlined text-red-500">error</span>
                            <div class="flex-1">
                                <div class="text-sm font-medium text-red-800">{{ uploadError }}</div>
                                <button
                                    v-if="processingFile && processingFile.status === 'failed'"
                                    type="button"
                                    class="mt-2 text-sm font-medium text-red-600 hover:underline"
                                    @click="handleRetry"
                                >
                                    重试
                                </button>
                            </div>
                        </div>
                    </div>

                    <button
                        type="button"
                        class="group flex w-full cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/30 p-12 text-center transition-colors hover:bg-slate-50"
                        :class="{ 'opacity-50 cursor-not-allowed': isUploading }"
                        :disabled="isUploading"
                        @click="triggerFileSelect"
                    >
                        <input
                            ref="fileInputRef"
                            type="file"
                            class="hidden"
                            accept=".pdf,.txt,.md,.docx"
                            multiple
                            @change="handleFileChange"
                        />
                        <div class="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-50 text-blue-400 transition-transform group-hover:scale-110">
                            <span class="material-symbols-outlined text-3xl">cloud_upload</span>
                        </div>
                        <div class="font-medium text-slate-600">
                            将文件拖拽至此，或 <span class="text-primary hover:underline">选择文件</span>
                        </div>
                        <div class="mt-2 text-sm text-slate-400">支持 PDF, TXT, MD, DOCX (最大 20MB)</div>
                    </button>

                    <div v-if="uploads.length" class="mt-6 space-y-3">
                        <div
                            v-for="item in uploads"
                            :key="item.id"
                            class="flex items-center justify-between rounded-2xl border border-slate-200 bg-white px-5 py-4"
                        >
                            <div class="flex min-w-0 items-center gap-4">
                                <div
                                    class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                                    :class="getFileIconClass(item.ext)"
                                >
                                    <span class="material-symbols-outlined text-xl">{{ getFileIcon(item.ext) }}</span>
                                </div>
                                <div class="min-w-0">
                                    <div class="truncate font-medium text-slate-800">{{ item.name }}</div>
                                    <div class="text-sm text-slate-400">{{ item.size }}</div>
                                    <div v-if="item.errorMessage" class="text-xs text-red-500">{{ item.errorMessage }}</div>
                                </div>
                            </div>

                            <div class="ml-4 flex items-center gap-4">
                                <span class="text-sm font-medium" :class="getStatusColor(item.status)">
                                    {{ getStatusLabel(item.status) }}
                                </span>
                                <button
                                    v-if="!item.isUploading"
                                    type="button"
                                    class="text-slate-400 transition-colors hover:text-red-500"
                                    @click="removeUpload(item.id)"
                                >
                                    <span class="material-symbols-outlined">delete</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </section>
            </div>
        </div>

        <template #footer>
            <div class="flex items-center justify-between">
                <button
                    type="button"
                    class="rounded-lg border border-slate-200 px-6 py-2 font-medium text-slate-600 transition-colors hover:bg-slate-50"
                    @click="emit('back')"
                >
                    取消
                </button>
                <button
                    type="button"
                    class="rounded-lg bg-primary px-8 py-2.5 font-bold text-white shadow-lg shadow-blue-500/20 transition-all hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    :disabled="isUploading"
                    @click="startUpload"
                >
                    {{ submitButtonText }}
                </button>
            </div>
        </template>
    </PageLayout>
</template>
