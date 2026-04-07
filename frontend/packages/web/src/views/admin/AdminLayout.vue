<template>
    <div class="admin-root">
        <div
            class="flex h-screen overflow-hidden bg-slate-50 text-slate-800 transition-colors duration-200"
        >
            <aside class="z-10 flex w-64 shrink-0 flex-col border-r border-slate-200 bg-white">
                <div class="flex items-center gap-3 p-6">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
                        <span class="material-symbols-outlined fill-1 text-xl text-white"
                            >auto_awesome</span
                        >
                    </div>
                    <h1 class="text-xl font-bold tracking-tight text-slate-900">灵洲 AI 后台</h1>
                </div>

                <nav class="flex-1 space-y-1 px-3">
                    <button
                        v-for="item in sidebarItems"
                        :key="item.id"
                        type="button"
                        class="relative flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left text-sm font-medium transition-all"
                        :class="
                            isActivePath(item.path)
                                ? 'bg-[#f0f7ff] text-primary font-bold'
                                : 'text-slate-600 hover:bg-slate-50'
                        "
                        @click="navigateTo(item.path)"
                    >
                        <span
                            class="material-symbols-outlined"
                            :class="isActivePath(item.path) ? 'text-primary' : 'text-slate-400'"
                        >
                            {{ item.icon }}
                        </span>
                        <span>{{ item.label }}</span>
                    </button>

                    <div
                        v-for="item in expandedSidebarGroups"
                        :key="`${item.id}-children`"
                        class="relative ml-2 pl-8"
                    >
                        <span class="absolute bottom-0 left-3 top-0 w-0.5 bg-blue-100" />
                        <button
                            v-for="child in item.children"
                            :key="child.id"
                            type="button"
                            class="relative z-10 flex w-full items-center rounded-lg py-2.5 pr-4 text-left text-sm font-bold text-blue-600 transition-colors"
                            :class="
                                isActivePath(child.path)
                                    ? 'text-blue-600'
                                    : 'text-slate-500 hover:text-blue-600'
                            "
                            @click="navigateTo(child.path)"
                        >
                            {{ child.label }}
                        </button>
                    </div>
                </nav>

                <div class="border-t border-slate-200 p-4">
                    <UserProfileCard
                        :user="currentUserProfile"
                        :name="currentUserProfile?.name"
                        avatar-alt="User avatar"
                        mode="admin"
                        logout-title="退出登录"
                        @logout="logout"
                    />
                </div>
            </aside>

            <main class="flex min-w-0 flex-1 flex-col overflow-hidden">
                <router-view v-slot="{ Component }">
                    <component
                        v-if="Component"
                        :is="Component"
                        @unauthorized="handleUnauthorized"
                    />
                    <div
                        v-else
                        class="flex flex-1 items-center justify-center text-sm text-slate-400"
                    >
                        页面加载中...
                    </div>
                </router-view>
            </main>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { confirm } from '@/composables/useModal';
import {
    clearUserSession,
    currentUserState,
    logoutCurrentUser,
} from '@/composables/useCurrentUser';
import { ADMIN_SIDEBAR_ITEMS } from '@/model/admin-sidebar';
import { ROUTE_PATHS } from '@/router/routePaths';
import UserProfileCard from '@/components/UserProfileCard.vue';

const router = useRouter();
const route = useRoute();
const sidebarItems = computed(() => ADMIN_SIDEBAR_ITEMS);
const expandedSidebarGroups = computed(() =>
    ADMIN_SIDEBAR_ITEMS.filter(
        item =>
            Array.isArray(item.children) &&
            item.children.length &&
            item.children.some(child => isActivePath(child.path))
    )
);
const currentUserProfile = computed(() => currentUserState.profile);

function isActivePath(path) {
    if (!path) {
        return false;
    }
    if (route.path === path) {
        return true;
    }
    if (path === ROUTE_PATHS.adminHome) {
        return false;
    }
    return route.path.startsWith(path.endsWith('/') ? path : `${path}/`);
}

function navigateTo(path) {
    if (!path || route.path === path) {
        return;
    }
    router.push({ path });
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function logout() {
    const shouldLogout = await confirm({
        title: '退出登录',
        message: '确认退出当前账号吗？',
        confirmText: '退出',
        cancelText: '取消',
    });
    if (!shouldLogout) {
        return;
    }

    await logoutCurrentUser({
        onUnauthorized: () => handleUnauthorized(),
    });
    router.replace(ROUTE_PATHS.login);
}
</script>
