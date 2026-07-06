<script setup>
import { computed } from 'vue';
import { FRONT_CONTENT_MAX_WIDTH, FRONT_RAIL_WIDTH_DESKTOP } from '@/model/front-shell';
import FrontPageHeader from './FrontPageHeader.vue';
import FrontQuotaChip from './FrontQuotaChip.vue';
import FrontRailNav from './FrontRailNav.vue';
import FrontTopBar from './FrontTopBar.vue';
import FrontUserMenu from './FrontUserMenu.vue';

const props = defineProps({
    branding: {
        type: Object,
        required: true,
    },
    railItems: {
        type: Array,
        default: () => [],
    },
    activePath: {
        type: String,
        default: '',
    },
    pageTitle: {
        type: String,
        default: '',
    },
    pageDescription: {
        type: String,
        default: '',
    },
    showPageHeader: {
        type: Boolean,
        default: false,
    },
    fullBleedContent: {
        type: Boolean,
        default: false,
    },
    contentInset: {
        type: Boolean,
        default: false,
    },
    tokenQuota: {
        type: Object,
        default: null,
    },
    user: {
        type: Object,
        default: null,
    },
    conversationCollapsed: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['navigate', 'logout']);

const shellMaxWidthStyle = computed(() => ({
    maxWidth: `${FRONT_CONTENT_MAX_WIDTH}px`,
}));

const shellContentClass = computed(() => {
    if (props.fullBleedContent) {
        return 'flex h-full min-w-0 flex-col';
    }
    return props.contentInset
        ? 'mx-auto flex h-full min-w-0 flex-col px-4 py-4 lg:px-5 lg:py-5'
        : 'mx-auto flex h-full min-w-0 flex-col';
});

const shellContentStyle = computed(() => {
    if (props.fullBleedContent) {
        return undefined;
    }
    return shellMaxWidthStyle.value;
});

const shellMainClass = computed(() => 'min-w-0 flex-1 overflow-hidden rounded-[28px] bg-white');

const railWidthStyle = computed(() => ({
    width: `${FRONT_RAIL_WIDTH_DESKTOP}px`,
}));

function handleNavigate(path) {
    emit('navigate', path);
}
</script>

<template>
    <div class="relative flex h-screen min-h-screen overflow-hidden bg-page text-strong">
        <aside class="relative z-20 hidden shrink-0 bg-[#f7f8fa] lg:flex" :style="railWidthStyle">
            <div class="flex h-full w-full flex-col items-center py-3">
                <div
                    class="flex h-12 w-12 shrink-0 items-center justify-center"
                    :title="branding.systemName"
                >
                    <img
                        :src="branding.logoUrl"
                        :alt="branding.systemName"
                        class="h-12 w-12 object-contain"
                    />
                </div>

                <div class="mt-4 flex min-h-0 flex-1 flex-col items-center">
                    <FrontRailNav
                        orientation="vertical"
                        :items="railItems"
                        :active-path="activePath"
                        :conversation-collapsed="conversationCollapsed"
                        :show-labels="false"
                        @navigate="handleNavigate"
                    />
                </div>

                <div class="flex flex-col items-center gap-3 pb-1">
                    <FrontQuotaChip compact :token-quota="tokenQuota" />
                    <FrontUserMenu compact :user="user" @logout="emit('logout')" />
                </div>
            </div>
        </aside>

        <div
            class="relative z-10 flex min-w-0 flex-1 flex-col overflow-hidden p-3 lg:py-3 lg:pr-3 lg:pl-0"
        >
            <FrontTopBar
                :branding="branding"
                :page-title="pageTitle"
                :page-description="pageDescription"
                :token-quota="tokenQuota"
                :user="user"
                @logout="emit('logout')"
            />

            <div
                class="border-b border-border-soft/60 bg-page/82 px-3 py-2 backdrop-blur-xl lg:hidden"
            >
                <FrontRailNav
                    orientation="horizontal"
                    :items="railItems"
                    :active-path="activePath"
                    :conversation-collapsed="conversationCollapsed"
                    :show-labels="true"
                    @navigate="handleNavigate"
                />
            </div>

            <main :class="shellMainClass">
                <div :class="shellContentClass" :style="shellContentStyle">
                    <FrontPageHeader
                        v-if="showPageHeader"
                        :title="pageTitle"
                        :description="pageDescription"
                    />

                    <div class="min-h-0 flex-1 overflow-hidden">
                        <slot />
                    </div>
                </div>
            </main>
        </div>
    </div>
</template>
