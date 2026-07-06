<template>
    <FrontWorkbenchShell
        :branding="brandingState"
        :rail-items="sidebarItems"
        :active-path="activePath"
        :page-title="pageTitle"
        :page-description="pageDescription"
        :show-page-header="showPageHeader"
        :full-bleed-content="fullBleedContent"
        :content-inset="contentInset"
        :token-quota="currentUserProfile?.tokenQuota"
        :user="currentUserProfile"
        :conversation-collapsed="chatHistoryCollapsed"
        @navigate="navigateTo"
        @logout="logout"
    >
        <router-view v-slot="{ Component }">
            <component
                v-if="Component"
                :is="Component"
                v-bind="routeComponentProps"
                @unauthorized="handleUnauthorized"
                @update:sidebar-collapsed="chatHistoryCollapsed = $event"
            />
            <div
                v-else
                class="front-card flex h-full min-h-0 items-center justify-center text-sm text-muted"
            >
                页面加载中...
            </div>
        </router-view>
    </FrontWorkbenchShell>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { brandingState, ensureBrandingLoaded } from '@/composables/useBranding';
import { confirm } from '@/composables/useModal';
import {
    clearUserSession,
    currentUserState,
    logoutCurrentUser,
} from '@/composables/useCurrentUser';
import { FRONT_SIDEBAR_ITEMS } from '@/model/front-sidebar';
import { ROUTE_PATHS } from '@/router/routePaths';
import FrontWorkbenchShell from '@/components/front-shell/FrontWorkbenchShell.vue';

const router = useRouter();
const route = useRoute();
const sidebarItems = computed(() => FRONT_SIDEBAR_ITEMS);
const currentUserProfile = computed(() => currentUserState.profile);
const chatHistoryCollapsed = ref(false);
ensureBrandingLoaded();

const activePath = computed(() => {
    const match = FRONT_SIDEBAR_ITEMS.find(
        item => route.path === item.path || route.path.startsWith(`${item.path}/`)
    );
    return match?.path || '';
});

const pageTitle = computed(() =>
    String(route.meta?.pageTitle || brandingState.systemName || '').trim()
);
const pageDescription = computed(() => String(route.meta?.pageDescription || '').trim());
const showPageHeader = computed(() => Boolean(route.meta?.showPageHeader));
const fullBleedContent = computed(() => Boolean(route.meta?.fullBleedContent));
const contentInset = computed(() => Boolean(route.meta?.contentInset));
const isChatRoute = computed(() => route.path === ROUTE_PATHS.frontChat);
const routeComponentProps = computed(() =>
    isChatRoute.value
        ? {
              sidebarCollapsed: chatHistoryCollapsed.value,
          }
        : {}
);

function navigateTo(path) {
    if (!path) {
        return;
    }
    if (route.path === path) {
        if (path === ROUTE_PATHS.frontChat) {
            chatHistoryCollapsed.value = !chatHistoryCollapsed.value;
        }
        return;
    }
    if (path === ROUTE_PATHS.frontChat) {
        chatHistoryCollapsed.value = false;
    }
    router.push({ path });
}

watch(
    () => route.path,
    path => {
        if (path === ROUTE_PATHS.frontChat) {
            chatHistoryCollapsed.value = false;
        }
    }
);

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
