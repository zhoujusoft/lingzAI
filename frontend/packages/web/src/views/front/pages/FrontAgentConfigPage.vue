<template>
    <section class="custom-scrollbar h-full overflow-y-auto bg-transparent text-strong">
        <div class="mx-auto w-full max-w-4xl space-y-5 px-4 py-6 pb-12 lg:px-6">
            <header class="front-card px-6 py-5">
                <div class="flex items-center gap-3">
                    <button
                        type="button"
                        class="inline-flex h-10 items-center gap-1.5 rounded-full border border-border-soft bg-white px-3 text-sm font-medium text-muted transition hover:border-slate-300 hover:bg-slate-50 hover:text-strong"
                        @click="goBack"
                    >
                        <span class="material-symbols-outlined text-[18px]">arrow_back</span>
                        返回
                    </button>
                    <h1 class="text-xl font-bold text-strong lg:text-2xl">个人助手</h1>
                </div>
                <p class="mt-2 text-sm text-muted">设置你的个人助手。</p>
            </header>

            <section class="front-card p-6">
                <div class="flex flex-col gap-6">
                    <div
                        class="flex flex-col gap-4 rounded-2xl border border-border-soft bg-surface-alt/40 p-5 sm:flex-row sm:items-center"
                    >
                        <button
                            type="button"
                            class="group relative h-28 w-28 shrink-0 overflow-hidden rounded-full border border-border-soft bg-white shadow-sm transition hover:shadow-md disabled:cursor-not-allowed disabled:opacity-75"
                            :disabled="avatarUploading"
                            @click="triggerAvatarFileSelect"
                        >
                            <img
                                v-if="avatarVisual.type === 'image'"
                                :src="avatarVisual.value"
                                alt="个人助手头像"
                                class="h-full w-full object-cover"
                            />
                            <div
                                v-else
                                class="flex h-full w-full items-center justify-center bg-[#f0f4f9] text-strong"
                            >
                                <span
                                    v-if="avatarVisual.type === 'material'"
                                    class="material-symbols-outlined text-[42px]"
                                >
                                    {{ avatarVisual.value }}
                                </span>
                                <span v-else class="text-[42px] leading-none">
                                    {{ avatarVisual.value }}
                                </span>
                            </div>
                            <div
                                class="absolute inset-0 flex items-center justify-center bg-slate-900/55 px-3 text-center text-xs font-medium text-white transition-opacity duration-200 group-hover:opacity-100"
                                :class="avatarUploading ? 'opacity-100' : 'opacity-0'"
                            >
                                {{ avatarUploading ? '上传中...' : '更换头像' }}
                            </div>
                        </button>

                        <div class="min-w-0 flex-1 space-y-1.5">
                            <p class="text-sm font-semibold text-strong">助手头像</p>
                            <p class="text-sm text-muted">支持 JPG / PNG，文件大小不超过 2MB。</p>
                            <p v-if="avatarErrorMessage" class="text-sm font-medium text-danger">
                                {{ avatarErrorMessage }}
                            </p>
                        </div>

                        <input
                            ref="avatarInputRef"
                            type="file"
                            accept="image/png,image/jpeg"
                            class="hidden"
                            @change="handleAvatarFileChange"
                        />
                    </div>

                    <div class="grid gap-4">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-strong">助手名称</span>
                            <input
                                v-model="agentName"
                                type="text"
                                maxlength="40"
                                placeholder="请输入你的个人助手名称"
                                class="h-12 w-full rounded-2xl border border-border-soft bg-white px-4 text-sm text-strong outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                            />
                        </label>
                        <p class="text-xs text-muted">留空时将使用当前 Agent 模板默认名称。</p>
                    </div>

                    <div class="flex justify-end">
                        <button
                            type="button"
                            class="rounded-xl bg-primary px-5 py-2 text-sm font-medium text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="saving"
                            @click="saveAssistantProfile"
                        >
                            {{ saving ? '保存中...' : '保存设置' }}
                        </button>
                    </div>
                </div>
            </section>
        </div>
    </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import defaultAvatarSrc from '@/assets/images/default-avatar.svg';
import {
    getCurrentUserAgentTemplate,
    updateCurrentUserAgentProfile,
    uploadCurrentUserAgentAvatar,
} from '@/api/user-agent-config';
import { clearUserSession } from '@/composables/useCurrentUser';
import { showToast } from '@/composables/useToast';
import { ensureAgentConfigLoaded, fetchAgentConfig } from '@/composables/useAgentConfig';
import { ROUTE_PATHS } from '@/router/routePaths';
import { openImageEditorModal } from '@/utils/openImageEditorModal';
import { validateImageFile } from '@/utils/imageEditor';
import { isImageSource, isMaterialSymbolName } from '@/utils/iconDisplay';

const router = useRouter();
const avatarInputRef = ref(null);
const avatarUploading = ref(false);
const avatarErrorMessage = ref('');
const avatarPreviewUrl = ref('');
const saving = ref(false);
const agentName = ref('');
const assistantTemplate = ref(null);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function goBack() {
    if (window.history.length > 1) {
        router.back();
        return;
    }
    router.push(ROUTE_PATHS.frontChat);
}

function setAvatarPreviewUrl(nextUrl = '') {
    if (avatarPreviewUrl.value && avatarPreviewUrl.value.startsWith('blob:')) {
        URL.revokeObjectURL(avatarPreviewUrl.value);
    }
    avatarPreviewUrl.value = nextUrl;
}

const avatarVisual = computed(() => {
    const preview = String(avatarPreviewUrl.value || '').trim();
    if (preview) {
        return { type: 'image', value: preview };
    }

    const customAvatarUrl = String(assistantTemplate.value?.avatarUrl || '').trim();
    if (customAvatarUrl) {
        return { type: 'image', value: customAvatarUrl };
    }

    const templateIcon = String(assistantTemplate.value?.icon || '').trim();
    if (isImageSource(templateIcon)) {
        return { type: 'image', value: templateIcon };
    }
    if (isMaterialSymbolName(templateIcon)) {
        return { type: 'material', value: templateIcon };
    }
    if (templateIcon) {
        return { type: 'emoji', value: templateIcon };
    }

    return { type: 'image', value: '/general-chat-assistant-logo.png' || defaultAvatarSrc };
});

function triggerAvatarFileSelect() {
    if (avatarUploading.value) {
        return;
    }
    avatarInputRef.value?.click();
}

function validateAvatarFile(file) {
    return validateImageFile(file, {
        maxBytes: 2 * 1024 * 1024,
    });
}

async function loadAssistantProfile() {
    await ensureAgentConfigLoaded({
        force: true,
        onUnauthorized: handleUnauthorized,
    });
    assistantTemplate.value = await getCurrentUserAgentTemplate(handleUnauthorized);
    agentName.value = assistantTemplate.value?.displayName || '';
}

async function handleAvatarFileChange(event) {
    const input = event?.target;
    const file = input?.files?.[0];
    if (input) {
        input.value = '';
    }
    if (!file || avatarUploading.value) {
        return;
    }

    const validationError = validateAvatarFile(file);
    if (validationError) {
        avatarErrorMessage.value = validationError;
        return;
    }

    avatarErrorMessage.value = '';
    const editedImage = await openImageEditorModal({
        title: '调整助手头像',
        confirmText: '应用头像',
        file,
        aspectRatio: 1,
        cropShape: 'circle',
        outputSize: 512,
        preferredMimeType: 'image/jpeg',
        preferredExtension: '.jpg',
        maxOutputBytes: 400 * 1024,
        initialQuality: 0.88,
        minQuality: 0.7,
        minZoom: 0.7,
        maxZoom: 3.2,
        fileNameStem: 'assistant-avatar',
        helperText: '拖动图片调整显示区域，系统会导出为 512 x 512 的头像。',
    });
    if (!editedImage?.file) {
        return;
    }

    setAvatarPreviewUrl(URL.createObjectURL(editedImage.blob || editedImage.file));
    avatarUploading.value = true;

    try {
        const result = await uploadCurrentUserAgentAvatar(editedImage.file, handleUnauthorized);
        assistantTemplate.value = {
            ...(assistantTemplate.value || {}),
            avatarUrl: result?.avatarUrl || '',
            avatarObjectName: result?.avatarObjectName || '',
        };
        setAvatarPreviewUrl('');
        await fetchAgentConfig({ onUnauthorized: handleUnauthorized });
        showToast('助手头像已更新');
    } catch (error) {
        setAvatarPreviewUrl('');
        avatarErrorMessage.value = error?.message || '头像上传失败，请稍后重试';
    } finally {
        avatarUploading.value = false;
    }
}

async function saveAssistantProfile() {
    if (saving.value) {
        return;
    }
    saving.value = true;
    try {
        await updateCurrentUserAgentProfile(
            {
                agentName: agentName.value.trim(),
            },
            handleUnauthorized
        );
        await loadAssistantProfile();
        await fetchAgentConfig({ onUnauthorized: handleUnauthorized });
        showToast('个人助手已更新');
    } catch (error) {
        avatarErrorMessage.value = error?.message || '保存失败，请稍后重试';
    } finally {
        saving.value = false;
    }
}

onMounted(() => {
    loadAssistantProfile().catch(() => {});
});

onBeforeUnmount(() => {
    setAvatarPreviewUrl('');
});
</script>
