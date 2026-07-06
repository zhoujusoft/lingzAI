<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import defaultAvatarSrc from '@/assets/images/default-avatar.svg';
import { hasAnyAdminPermission } from '@/model/admin-menu-permissions';
import { resolveUserTypeLabel } from '@/model/enums/user-type';
import { ROUTE_PATHS } from '@/router/routePaths';
import { resolveUserAvatarUrl } from '@/utils/userAvatar';

const props = defineProps({
    user: {
        type: Object,
        default: null,
    },
    compact: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['logout']);

const router = useRouter();
const rootRef = ref(null);
const isMenuOpen = ref(false);

const displayName = computed(() => props.user?.name || props.user?.code || '当前用户');
const avatarSrc = computed(() => resolveUserAvatarUrl(props.user, defaultAvatarSrc));
const planText = computed(() => {
    if (props.user?.userType == null) {
        return '普通用户';
    }
    return resolveUserTypeLabel(props.user.userType, '普通用户');
});
const canSwitchToAdmin = computed(() => hasAnyAdminPermission(props.user));
const triggerClass = computed(() =>
    props.compact
        ? 'flex h-11 w-11 items-center justify-center rounded-full border border-border-soft/40 bg-surface/82 backdrop-blur-xl shadow-sm transition-all duration-300 hover:border-transparent hover:bg-surface/98 hover:shadow-[0_8px_25px_-8px_rgba(15,23,42,0.12),inset_0_1px_1px_rgba(255,255,255,0.8)]'
        : 'inline-flex items-center gap-3 rounded-full border border-border-soft/40 bg-surface/82 backdrop-blur-xl px-2.5 py-1.5 text-left shadow-sm transition-all duration-300 hover:border-transparent hover:bg-surface/98 hover:shadow-[0_8px_25px_-8px_rgba(15,23,42,0.12),inset_0_1px_1px_rgba(255,255,255,0.8)]'
);
const menuClass = computed(() =>
    props.compact
        ? 'absolute bottom-0 left-[calc(100%+0.75rem)] z-40 w-60 overflow-hidden rounded-3xl border border-border-soft/80 bg-surface/95 p-2 shadow-[0_20px_50px_-28px_rgba(15,23,42,0.45)] backdrop-blur-xl'
        : 'absolute right-0 top-[calc(100%+0.75rem)] z-40 w-56 overflow-hidden rounded-3xl border border-border-soft/80 bg-surface/95 p-2 shadow-[0_20px_50px_-28px_rgba(15,23,42,0.45)] backdrop-blur-xl'
);

function toggleMenu() {
    isMenuOpen.value = !isMenuOpen.value;
}

function closeMenu() {
    isMenuOpen.value = false;
}

function handleClickOutside(event) {
    if (!rootRef.value?.contains(event.target)) {
        closeMenu();
    }
}

function openProfile() {
    closeMenu();
    if (router.currentRoute.value.path === ROUTE_PATHS.frontProfile) {
        return;
    }
    router.push(ROUTE_PATHS.frontProfile);
}

function openAdmin() {
    closeMenu();
    const resolved = router.resolve(ROUTE_PATHS.adminHome);
    window.open(resolved.href, '_blank', 'noopener');
}

function handleLogout() {
    closeMenu();
    emit('logout');
}

onMounted(() => {
    document.addEventListener('mousedown', handleClickOutside);
});

onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleClickOutside);
});
</script>

<template>
    <div ref="rootRef" class="relative">
        <button type="button" :class="triggerClass" :title="displayName" @click="toggleMenu">
            <img :src="avatarSrc" alt="avatar" class="h-9 w-9 rounded-full object-cover" />
            <div v-if="!compact" class="hidden min-w-0 sm:block">
                <div class="truncate text-sm font-semibold text-strong">{{ displayName }}</div>
                <div class="truncate text-[11px] text-muted">{{ planText }}</div>
            </div>
            <span
                v-if="!compact"
                class="material-symbols-outlined hidden text-lg text-muted transition-transform sm:block"
                :class="{ 'rotate-180': isMenuOpen }"
            >
                expand_more
            </span>
        </button>

        <div v-if="isMenuOpen" :class="menuClass">
            <div class="px-3 pb-2 pt-2">
                <div class="text-sm font-semibold text-strong">{{ displayName }}</div>
                <div class="mt-1 text-xs text-muted">{{ planText }}</div>
            </div>

            <div class="space-y-1">
                <button
                    type="button"
                    class="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-sm text-body transition-colors hover:bg-surface-alt hover:text-strong"
                    @click="openProfile"
                >
                    <span class="material-symbols-outlined text-xl">person</span>
                    <span>个人中心</span>
                </button>

                <button
                    v-if="canSwitchToAdmin"
                    type="button"
                    class="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-sm text-body transition-colors hover:bg-surface-alt hover:text-strong"
                    @click="openAdmin"
                >
                    <span class="material-symbols-outlined text-xl">dashboard</span>
                    <span>跳转后台</span>
                </button>

                <button
                    type="button"
                    class="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-sm text-danger transition-colors hover:bg-danger/10"
                    @click="handleLogout"
                >
                    <span class="material-symbols-outlined text-xl">logout</span>
                    <span>退出登录</span>
                </button>
            </div>
        </div>
    </div>
</template>
