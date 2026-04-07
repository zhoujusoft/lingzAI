<script setup>
import { computed } from 'vue';
import AppSelect from '@/components/AppSelect.vue';

const props = defineProps({
    page: {
        type: Number,
        default: 1,
    },
    pageSize: {
        type: Number,
        default: 10,
    },
    total: {
        type: Number,
        default: 0,
    },
    pageSizeOptions: {
        type: Array,
        default: () => [10, 20, 50],
    },
});

const emit = defineEmits(['page-change', 'page-size-change']);

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));
const selectedPageSize = computed({
    get() {
        return props.pageSize;
    },
    set(value) {
        const nextSize = Number(value);
        if (!Number.isFinite(nextSize) || nextSize <= 0 || nextSize === props.pageSize) {
            return;
        }
        emit('page-size-change', nextSize);
    },
});

const pageSizeSelectOptions = computed(() =>
    props.pageSizeOptions.map(size => ({
        value: size,
        label: `${size} / 页`,
    }))
);

const pageTokens = computed(() => {
    const current = props.page;
    const totalPageCount = totalPages.value;
    if (totalPageCount <= 7) {
        return Array.from({ length: totalPageCount }, (_, index) => index + 1);
    }

    const tokens = [1];
    const left = Math.max(2, current - 1);
    const right = Math.min(totalPageCount - 1, current + 1);

    if (left > 2) {
        tokens.push('ellipsis-left');
    }
    for (let i = left; i <= right; i += 1) {
        tokens.push(i);
    }
    if (right < totalPageCount - 1) {
        tokens.push('ellipsis-right');
    }
    tokens.push(totalPageCount);
    return tokens;
});

function goToPage(nextPage) {
    const safePage = Math.max(1, Math.min(Number(nextPage) || 1, totalPages.value));
    if (safePage === props.page) {
        return;
    }
    emit('page-change', safePage);
}
</script>

<template>
    <div class="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white/95 px-2.5 py-1.5 shadow-[0_10px_28px_-22px_rgba(15,23,42,0.38)] backdrop-blur-sm">
        <AppSelect
            v-model="selectedPageSize"
            :options="pageSizeSelectOptions"
            size="sm"
            menu-placement="top"
            :full-width="false"
            button-class="min-w-[96px] shadow-none"
        />

        <button
            type="button"
            class="h-9 rounded-xl border border-slate-200 px-3 text-xs font-medium text-slate-600 transition-colors hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
            :disabled="page <= 1"
            @click="goToPage(page - 1)"
        >
            上一页
        </button>

        <div class="flex items-center gap-1">
            <template
                v-for="token in pageTokens"
                :key="String(token)"
            >
                <span
                    v-if="typeof token === 'string'"
                    class="px-1 text-xs text-slate-400"
                >...</span>
                <button
                    v-else
                    type="button"
                    class="h-9 min-w-9 rounded-xl px-2.5 text-xs font-medium transition-colors"
                    :class="
                        token === page
                            ? 'bg-primary font-semibold text-white shadow-sm shadow-blue-500/20'
                            : 'text-slate-600 hover:bg-slate-50'
                    "
                    @click="goToPage(token)"
                >
                    {{ token }}
                </button>
            </template>
        </div>

        <button
            type="button"
            class="h-9 rounded-xl border border-slate-200 px-3 text-xs font-medium text-slate-600 transition-colors hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
            :disabled="page >= totalPages"
            @click="goToPage(page + 1)"
        >
            下一页
        </button>
    </div>
</template>
