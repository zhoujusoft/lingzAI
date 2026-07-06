<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import defaultAvatarSrc from '@/assets/images/default-avatar.svg';
import { resolveUserTypeLabel } from '@/model/enums/user-type';
import { hasAnyAdminPermission } from '@/model/admin-menu-permissions';
import { resolveUserAvatarUrl } from '@/utils/userAvatar';

// 深色模式开关 - 与 useTheme.js 保持同步
const DARK_MODE_ENABLED = false;

const props = defineProps({
    user: {
        type: Object,
        default: null,
    },
    name: {
        type: String,
    },
    planText: {
        type: String,
        default: '',
    },
    avatarAlt: {
        type: String,
        default: 'avatar',
    },
    logoutTitle: {
        type: String,
        default: '退出登录',
    },
    mode: {
        type: String,
        default: 'front',
    },
});

const emit = defineEmits(['logout']);
const router = useRouter();
const cardRef = ref(null);
const isMenuOpen = ref(false);
const isDisplayModeMenuOpen = ref(false);
const displayName = computed(() => props.user?.name || props.user?.code || props.name || '');
const avatarSrc = computed(() => resolveUserAvatarUrl(props.user, defaultAvatarSrc));
const displayPlanText = computed(() => {
    if (props.user?.userType != null) {
        return resolveUserTypeLabel(props.user.userType, props.planText || '普通用户');
    }
    return props.planText;
});
const canSwitchToAdmin = computed(() => {
    if (props.mode === 'admin') {
        return true;
    }
    return hasAnyAdminPermission(props.user);
});
const showSwitchButton = computed(() => props.mode === 'admin' || canSwitchToAdmin.value);
const profilePath = computed(() =>
    props.mode === 'admin' ? ROUTE_PATHS.adminProfile : ROUTE_PATHS.frontProfile
);

function toggleMenu() {
    isMenuOpen.value = !isMenuOpen.value;
    if (!isMenuOpen.value) {
        isDisplayModeMenuOpen.value = false;
    }
}

function toggleDisplayModeMenu() {
    isDisplayModeMenuOpen.value = !isDisplayModeMenuOpen.value;
}

function closeMenus() {
    isMenuOpen.value = false;
    isDisplayModeMenuOpen.value = false;
}

function handleClickOutside(event) {
    if (!cardRef.value?.contains(event.target)) {
        closeMenus();
    }
}

function handleSwitchPage() {
    const targetPath = props.mode === 'admin' ? ROUTE_PATHS.frontHome : ROUTE_PATHS.adminHome;
    closeMenus();
    if (props.mode === 'front') {
        const resolved = router.resolve(targetPath);
        window.open(resolved.href, '_blank', 'noopener');
        return;
    }
    router.push(targetPath);
}

function handleOpenProfile() {
    closeMenus();
    if (!profilePath.value) {
        return;
    }
    if (router.currentRoute.value.path === profilePath.value) {
        return;
    }
    router.push(profilePath.value);
}

function handleLogoutClick() {
    closeMenus();
    emit('logout');
}

function handleDisplayModeClick() {
    closeMenus();
}

onMounted(() => {
    document.addEventListener('mousedown', handleClickOutside);
});

onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleClickOutside);
});
</script>

<template>
    <div ref="cardRef" class="relative rounded-xl transition-shadow duration-200 hover:shadow-sm">
        <div class="flex items-center justify-between p-2">
            <div class="flex min-w-0 items-center gap-3 text-left">
                <img
                    :alt="avatarAlt"
                    class="h-10 w-10 rounded-full border-2 border-slate-200 object-cover"
                    :src="avatarSrc"
                />
                <div class="min-w-0">
                    <div class="truncate text-sm font-semibold text-slate-900">
                        {{ displayName }}
                    </div>
                    <div
                        v-if="displayPlanText"
                        class="mt-0.5 inline-flex rounded bg-blue-50 px-1.5 py-0.5 text-[10px] font-medium text-blue-500"
                    >
                        {{ displayPlanText }}
                    </div>
                </div>
            </div>

            <button
                type="button"
                class="rounded p-1 text-slate-400 transition-colors hover:text-slate-600"
                :title="logoutTitle"
                @click="toggleMenu"
            >
                <span class="material-symbols-outlined">settings</span>
            </button>
        </div>

        <div
            v-if="isMenuOpen"
            class="popover-shadow absolute bottom-full left-4 z-50 mb-2 w-56 rounded-2xl border border-slate-200 bg-white"
        >
            <div class="space-y-1 p-2">
                <!-- 显示模式切换 - 深色模式禁用时隐藏 -->
                <div v-if="DARK_MODE_ENABLED" class="relative">
                    <button
                        type="button"
                        class="flex w-full items-center justify-between rounded-xl px-3 py-2.5 text-slate-700 transition-colors hover:bg-slate-50"
                        @click="toggleDisplayModeMenu"
                    >
                        <div class="flex items-center gap-3">
                            <span class="material-symbols-outlined text-xl">desktop_windows</span>
                            <span class="text-sm">显示模式</span>
                        </div>
                        <span class="material-symbols-outlined text-sm text-slate-400"
                            >chevron_right</span
                        >
                    </button>

                    <div
                        v-if="isDisplayModeMenuOpen"
                        class="popover-shadow absolute left-full top-0 z-[60] ml-2 w-44 rounded-2xl border border-slate-200 bg-white p-1.5"
                    >
                        <button
                            type="button"
                            class="flex w-full items-center gap-3 rounded-lg px-3 py-2 transition-colors hover:bg-slate-50"
                            @click="handleDisplayModeClick"
                        >
                            <span class="material-symbols-outlined text-xl text-slate-500"
                                >light_mode</span
                            >
                            <span class="text-sm">亮色模式</span>
                        </button>
                        <button
                            type="button"
                            class="flex w-full items-center gap-3 rounded-lg px-3 py-2 transition-colors hover:bg-slate-50"
                            @click="handleDisplayModeClick"
                        >
                            <span class="material-symbols-outlined text-xl text-slate-500"
                                >dark_mode</span
                            >
                            <span class="text-sm">暗色模式</span>
                        </button>
                    </div>
                </div>

                <button
                    type="button"
                    class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-slate-700 transition-colors hover:bg-slate-50"
                    @click="handleOpenProfile"
                >
                    <span class="material-symbols-outlined text-xl">person</span>
                    <span class="text-sm">个人中心</span>
                </button>

                <button
                    v-if="showSwitchButton"
                    type="button"
                    class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-slate-700 transition-colors hover:bg-slate-50"
                    @click="handleSwitchPage"
                >
                    <span class="material-symbols-outlined text-xl">dashboard</span>
                    <span class="text-sm">{{ mode === 'admin' ? '跳转前台' : '跳转后台' }}</span>
                </button>

                <button
                    type="button"
                    class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-red-500 transition-colors hover:bg-red-50"
                    @click="handleLogoutClick"
                >
                    <span class="material-symbols-outlined text-xl">logout</span>
                    <span class="text-sm">{{ logoutTitle }}</span>
                </button>
            </div>
        </div>
    </div>
</template>

<style scoped>
.popover-shadow {
    box-shadow:
        0 4px 24px -2px rgba(0, 0, 0, 0.1),
        0 2px 8px -2px rgba(0, 0, 0, 0.06);
}
</style>
