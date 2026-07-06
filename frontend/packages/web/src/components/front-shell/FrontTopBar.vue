<script setup>
import FrontQuotaChip from './FrontQuotaChip.vue';
import FrontUserMenu from './FrontUserMenu.vue';

const props = defineProps({
    branding: {
        type: Object,
        required: true,
    },
    pageTitle: {
        type: String,
        default: '',
    },
    pageDescription: {
        type: String,
        default: '',
    },
    tokenQuota: {
        type: Object,
        default: null,
    },
    user: {
        type: Object,
        default: null,
    },
});

const emit = defineEmits(['logout']);
</script>

<template>
    <header
        class="relative z-20 border-b border-border-soft/70 bg-page/88 backdrop-blur-xl lg:hidden"
    >
        <div class="flex h-[56px] items-center gap-3 px-4">
            <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-border-soft/80 bg-surface/80 shadow-sm"
            >
                <img
                    :src="branding.logoUrl"
                    :alt="branding.systemName"
                    class="h-6 w-6 object-contain"
                />
            </div>

            <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-semibold text-strong">{{ pageTitle }}</div>
                <div v-if="pageDescription" class="truncate text-[11px] text-muted">
                    {{ pageDescription }}
                </div>
            </div>

            <div class="ml-auto flex items-center gap-2">
                <div class="hidden sm:block">
                    <FrontQuotaChip :token-quota="tokenQuota" />
                </div>
                <FrontUserMenu compact :user="user" @logout="emit('logout')" />
            </div>
        </div>
    </header>
</template>
