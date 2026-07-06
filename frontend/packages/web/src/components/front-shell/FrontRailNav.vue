<script setup>
import { computed } from 'vue';
import sidebarCollapseIcon from '@/assets/images/sidebar-collapse.svg';
import sidebarExpandIcon from '@/assets/images/sidebar-expand.svg';

const props = defineProps({
    items: {
        type: Array,
        default: () => [],
    },
    activePath: {
        type: String,
        default: '',
    },
    orientation: {
        type: String,
        default: 'vertical',
    },
    showLabels: {
        type: Boolean,
        default: true,
    },
    conversationCollapsed: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['navigate']);

const isVertical = computed(() => props.orientation === 'vertical');
const shouldRenderLabels = computed(() => props.showLabels || !isVertical.value);

function isActive(item) {
    return Boolean(item?.path) && item.path === props.activePath;
}

function isConversationItem(item) {
    return item?.id === 'chat';
}

function resolveItemTitle(item) {
    if (isConversationItem(item) && isActive(item)) {
        return props.conversationCollapsed ? '展开会话历史' : '折叠会话历史';
    }
    return item?.label || '';
}

function resolveConversationActionIconStyle() {
    const iconUrl = props.conversationCollapsed ? sidebarExpandIcon : sidebarCollapseIcon;
    return {
        WebkitMaskImage: `url(${iconUrl})`,
        maskImage: `url(${iconUrl})`,
        WebkitMaskRepeat: 'no-repeat',
        maskRepeat: 'no-repeat',
        WebkitMaskPosition: 'center',
        maskPosition: 'center',
        WebkitMaskSize: 'contain',
        maskSize: 'contain',
    };
}

function handleNavigate(item) {
    const path = item?.path;
    if (!path || (path === props.activePath && !isConversationItem(item))) {
        return;
    }
    emit('navigate', path);
}
</script>

<template>
    <nav
        :class="[
            'min-w-0',
            isVertical
                ? 'flex h-full w-full flex-col items-center gap-1 px-1 py-3'
                : 'grid grid-cols-5 gap-2',
        ]"
        aria-label="前台主导航"
    >
        <button
            v-for="item in items"
            :key="item.id"
            type="button"
            :title="isVertical ? undefined : resolveItemTitle(item)"
            :aria-label="item.label"
            :aria-current="isActive(item) ? 'page' : undefined"
            :class="[
                'group relative flex min-w-0 flex-col items-center justify-center gap-0.5 text-center transition-colors duration-150',
                isVertical ? 'w-14 py-2' : 'min-h-[64px] rounded-[18px] px-2 py-2',
                isActive(item) ? 'text-primary' : 'text-muted hover:text-strong',
            ]"
            @click="handleNavigate(item)"
        >
            <span
                class="material-symbols-outlined text-[22px] transition-opacity duration-150"
                :class="{
                    'fill-1': isActive(item),
                    'group-hover:opacity-0':
                        isVertical && isConversationItem(item) && isActive(item),
                }"
            >
                {{ item.icon }}
            </span>
            <span
                v-if="isVertical && isConversationItem(item) && isActive(item)"
                class="absolute top-2 h-[22px] w-[22px] bg-current opacity-0 transition-opacity duration-150 group-hover:opacity-100"
                :style="resolveConversationActionIconStyle()"
            />
            <span class="text-[10px] font-medium leading-tight">
                {{ item.shortLabel }}
            </span>
            <span class="sr-only">{{ item.label }}</span>
            <span
                v-if="isVertical"
                class="pointer-events-none absolute left-[calc(100%+8px)] top-1/2 z-50 -translate-y-1/2 whitespace-nowrap rounded-lg bg-slate-900 px-2.5 py-1.5 text-[11px] font-medium text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover:opacity-100"
            >
                {{ resolveItemTitle(item) }}
            </span>
        </button>
    </nav>
</template>
