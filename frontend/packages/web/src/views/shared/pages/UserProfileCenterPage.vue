<template>
    <section class="custom-scrollbar h-full overflow-y-auto bg-transparent text-strong">
        <div class="mx-auto w-full max-w-4xl space-y-5 px-4 py-6 pb-12 lg:px-6">
            <div class="flex items-center gap-3">
                <button
                    type="button"
                    class="inline-flex h-10 items-center gap-1.5 rounded-full border border-border-soft bg-white px-3 text-sm font-medium text-muted transition hover:border-slate-300 hover:bg-slate-50 hover:text-strong"
                    @click="goBack"
                >
                    <span class="material-symbols-outlined text-[18px]">arrow_back</span>
                    返回
                </button>
                <h1 class="text-xl font-bold text-strong lg:text-2xl">个人中心</h1>
            </div>
            <section class="front-card p-6">
                <div class="flex items-start justify-between gap-4">
                    <div>
                        <h2 class="text-base font-semibold text-strong">个人信息</h2>
                        <p class="mt-1 text-sm text-muted">可上传头像并维护基础资料。</p>
                    </div>
                    <button
                        type="button"
                        class="rounded-xl bg-primary px-5 py-2 text-sm font-medium text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 active:scale-95"
                        @click="openEditProfileDialog"
                    >
                        编辑个人信息
                    </button>
                </div>

                <div
                    class="mt-6 flex flex-col gap-4 rounded-2xl border border-border-soft bg-surface-alt/40 p-5 sm:flex-row sm:items-center"
                >
                    <button
                        type="button"
                        class="group relative h-28 w-28 shrink-0 overflow-hidden rounded-full border border-border-soft bg-white shadow-sm transition hover:shadow-md disabled:cursor-not-allowed disabled:opacity-75"
                        :disabled="avatarUploading || !currentUserProfile?.id"
                        @click="triggerAvatarFileSelect"
                    >
                        <img
                            :src="avatarDisplayUrl"
                            alt="当前头像"
                            class="h-full w-full object-cover"
                        />
                        <div
                            class="absolute inset-0 flex items-center justify-center bg-slate-900/55 px-3 text-center text-xs font-medium text-white transition-opacity duration-200 group-hover:opacity-100"
                            :class="avatarUploading ? 'opacity-100' : 'opacity-0'"
                        >
                            {{ avatarUploading ? '上传中...' : '更换头像' }}
                        </div>
                    </button>
                    <div class="min-w-0 space-y-1.5">
                        <p class="text-sm font-semibold text-strong">个人头像</p>
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

                <div class="mt-6 grid gap-4 md:grid-cols-2">
                    <div class="rounded-xl border border-border-soft bg-surface-alt/50 px-4 py-3">
                        <p class="text-xs font-medium text-muted">姓名</p>
                        <p class="mt-1 text-sm font-semibold text-strong">
                            {{ currentUserProfile?.name || '-' }}
                        </p>
                    </div>
                    <div class="rounded-xl border border-border-soft bg-surface-alt/50 px-4 py-3">
                        <p class="text-xs font-medium text-muted">账号</p>
                        <p class="mt-1 text-sm font-semibold text-strong">
                            {{ currentUserProfile?.code || '-' }}
                        </p>
                    </div>
                    <div class="rounded-xl border border-border-soft bg-surface-alt/50 px-4 py-3">
                        <p class="text-xs font-medium text-muted">手机号</p>
                        <p class="mt-1 text-sm font-semibold text-strong">
                            {{ currentUserProfile?.mobile || '-' }}
                        </p>
                    </div>
                    <div class="rounded-xl border border-border-soft bg-surface-alt/50 px-4 py-3">
                        <p class="text-xs font-medium text-muted">邮箱</p>
                        <p class="mt-1 text-sm font-semibold text-strong">
                            {{ currentUserProfile?.email || '-' }}
                        </p>
                    </div>
                    <div class="rounded-xl border border-border-soft bg-surface-alt/50 px-4 py-3">
                        <p class="text-xs font-medium text-muted">用户类型</p>
                        <p class="mt-1 text-sm font-semibold text-strong">{{ userTypeText }}</p>
                    </div>
                    <div class="rounded-xl border border-border-soft bg-surface-alt/50 px-4 py-3">
                        <p class="text-xs font-medium text-muted">角色</p>
                        <p class="mt-1 text-sm font-semibold text-strong">
                            {{ currentUserProfile?.roleName || '-' }}
                        </p>
                    </div>
                </div>
            </section>

            <section class="front-card p-6">
                <div class="flex items-start justify-between gap-4">
                    <div>
                        <h2 class="text-base font-semibold text-strong">账号安全</h2>
                        <p class="mt-1 text-sm text-muted">点击按钮完成密码修改。</p>
                    </div>
                    <button
                        type="button"
                        class="rounded-xl bg-primary px-5 py-2 text-sm font-medium text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 active:scale-95"
                        @click="openChangePasswordDialog"
                    >
                        修改密码
                    </button>
                </div>
            </section>
        </div>
    </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import defaultAvatarSrc from '@/assets/images/default-avatar.svg';
import { uploadCurrentUserAvatar } from '@/api/users';
import { openModal } from '@/composables/useModal';
import {
    clearUserSession,
    currentUserState,
    ensureCurrentUserLoaded,
    mergeCurrentUserProfile,
} from '@/composables/useCurrentUser';
import { showToast } from '@/composables/useToast';
import { resolveUserTypeLabel } from '@/model/enums/user-type';
import { ROUTE_PATHS } from '@/router/routePaths';
import { openImageEditorModal } from '@/utils/openImageEditorModal';
import { validateImageFile } from '@/utils/imageEditor';
import { resolveUserAvatarUrl } from '@/utils/userAvatar';
import UserPasswordChangeModalContent from '../components/UserPasswordChangeModalContent.vue';
import UserPasswordChangeModalFooter from '../components/UserPasswordChangeModalFooter.vue';
import UserProfileEditModalContent from '../components/UserProfileEditModalContent.vue';
import UserProfileEditModalFooter from '../components/UserProfileEditModalFooter.vue';

const router = useRouter();
const currentUserProfile = computed(() => currentUserState.profile);
const userTypeText = computed(() => resolveUserTypeLabel(currentUserProfile.value?.userType, '-'));
const avatarInputRef = ref(null);
const avatarUploading = ref(false);
const avatarErrorMessage = ref('');
const avatarPreviewUrl = ref('');
const avatarDisplayUrl = computed(
    () => avatarPreviewUrl.value || resolveUserAvatarUrl(currentUserProfile.value, defaultAvatarSrc)
);

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

async function refreshCurrentUser() {
    await ensureCurrentUserLoaded({
        force: true,
        onUnauthorized: () => handleUnauthorized(),
    });
}

function setAvatarPreviewUrl(nextUrl = '') {
    if (avatarPreviewUrl.value && avatarPreviewUrl.value.startsWith('blob:')) {
        URL.revokeObjectURL(avatarPreviewUrl.value);
    }
    avatarPreviewUrl.value = nextUrl;
}

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
        title: '调整头像',
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
        fileNameStem: 'avatar',
        helperText: '拖动图片调整显示区域，系统会导出为 512 x 512 的头像。',
    });
    if (!editedImage?.file) {
        return;
    }

    setAvatarPreviewUrl(URL.createObjectURL(editedImage.blob || editedImage.file));
    avatarUploading.value = true;

    try {
        const result = await uploadCurrentUserAvatar(editedImage.file, () => handleUnauthorized());
        mergeCurrentUserProfile({
            avatarUrl: result?.avatarUrl || null,
        });
        setAvatarPreviewUrl('');
        try {
            await refreshCurrentUser();
        } catch (error) {
            // keep optimistic avatar state when profile refresh fails
        }
        showToast('头像已更新');
    } catch (error) {
        setAvatarPreviewUrl('');
        avatarErrorMessage.value = error?.message || '头像上传失败，请稍后重试';
    } finally {
        avatarUploading.value = false;
    }
}

async function openEditProfileDialog() {
    const profile = currentUserProfile.value;
    if (!profile?.id) {
        return;
    }
    const updated = await openModal({
        title: '编辑个人信息',
        content: {
            component: UserProfileEditModalContent,
        },
        footer: {
            component: UserProfileEditModalFooter,
            props: {
                confirmText: '保存',
                cancelText: '取消',
            },
        },
        confirmText: '保存',
        cancelText: '取消',
        showCancel: true,
        showClose: true,
        context: {
            id: profile.id,
            name: profile.name || '',
            mobile: profile.mobile || '',
            email: profile.email || '',
            userType: profile.userType,
            roleId: profile.roleId ?? null,
            formErrors: {},
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });

    if (updated) {
        await refreshCurrentUser();
        showToast('个人信息已更新');
    }
}

async function openChangePasswordDialog() {
    const updated = await openModal({
        title: '修改密码',
        content: {
            component: UserPasswordChangeModalContent,
        },
        footer: {
            component: UserPasswordChangeModalFooter,
            props: {
                confirmText: '确认修改',
                cancelText: '取消',
            },
        },
        confirmText: '确认修改',
        cancelText: '取消',
        showCancel: true,
        showClose: true,
        context: {
            oldPassword: '',
            password: '',
            confirmPassword: '',
            formErrors: {},
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });

    if (updated) {
        showToast('密码修改成功');
    }
}

onBeforeUnmount(() => {
    setAvatarPreviewUrl('');
});
</script>
