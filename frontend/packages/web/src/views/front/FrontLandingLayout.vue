<template>
    <div class="h-screen overflow-hidden bg-slate-50 text-slate-900">
        <div class="flex h-full overflow-hidden">
            <aside class="z-20 hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
                <div class="flex items-center gap-3 p-6">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white">
                        <span class="material-symbols-outlined fill-1 text-xl">auto_awesome</span>
                    </div>
                    <span class="text-xl font-bold tracking-tight">灵洲 AI</span>
                </div>

                <nav class="flex-1 space-y-1 px-3">
                    <button
                        v-for="item in sidebarItems"
                        :key="item.id"
                        type="button"
                        :class="[
                            'flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left text-sm font-medium transition-all',
                            isActivePath(item.path) ? 'bg-primary/10 text-primary' : 'text-slate-600 hover:bg-slate-50',
                        ]"
                        @click="navigateTo(item.path)"
                    >
                        <span class="material-symbols-outlined">{{ item.icon }}</span>
                        <span>{{ item.label }}</span>
                    </button>
                </nav>

                <div class="border-t border-slate-200 p-4">
                    <button
                        v-if="showCreateAgentButton"
                        type="button"
                        class="flex w-full items-center justify-center gap-2 rounded-xl bg-primary py-3 text-white shadow-lg shadow-blue-500/20 transition-all hover:bg-primary-hover"
                    >
                        <span class="material-symbols-outlined text-lg">add</span>
                        <span class="font-medium">创建智能体</span>
                    </button>
                    <UserProfileCard
                        :user="currentUserProfile"
                        :name="currentUserProfile?.name"
                        avatar-alt="avatar"
                        mode="front"
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
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { confirm } from '@/composables/useModal';
import { clearUserSession, currentUserState, logoutCurrentUser } from '@/composables/useCurrentUser';
import { FRONT_SIDEBAR_ITEMS } from '@/model/front-sidebar';
import { ROUTE_PATHS } from '@/router/routePaths';
import UserProfileCard from '@/components/UserProfileCard.vue';

const router = useRouter();
const route = useRoute();
const showCreateAgentButton = ref(false);
const sidebarItems = computed(() => FRONT_SIDEBAR_ITEMS);
const currentUserProfile = computed(() => currentUserState.profile);

function isActivePath(path) {
    if (!path) {
        return false;
    }
    return route.path === path;
}

function navigateTo(path) {
    if (!path) {
        return;
    }
    if (route.path === path) {
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
