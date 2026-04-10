<script setup>
import { computed } from 'vue';

const props = defineProps({
    payload: {
        type: Object,
        default: () => ({}),
    },
});

const componentProps = computed(() => {
    const value = props.payload?.componentProps;
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
});

const items = computed(() => {
    const rows = props.payload?.data?.items;
    return Array.isArray(rows) ? rows : [];
});

function resolveRank(item, index) {
    const rank = Number(item?.rank);
    return Number.isFinite(rank) && rank > 0 ? rank : index + 1;
}

function resolveName(item) {
    return String(item?.name || item?.label || item?.title || '-').trim() || '-';
}

function resolveValue(item) {
    const raw = item?.value ?? item?.score ?? item?.amount ?? '';
    if (raw == null || raw === '') {
        return '-';
    }
    return String(raw);
}

function medalClass(rank) {
    if (rank === 1) {
        return 'bg-amber-100 text-amber-700';
    }
    if (rank === 2) {
        return 'bg-slate-200 text-slate-700';
    }
    if (rank === 3) {
        return 'bg-orange-100 text-orange-700';
    }
    return 'bg-slate-100 text-slate-500';
}
</script>

<template>
    <div class="w-full max-w-full space-y-4 rounded-2xl border border-sky-200 bg-sky-50/70 p-4 sm:w-[500px]">
        <div class="min-w-0">
            <h4 class="truncate text-sm font-semibold text-slate-900">
                {{ componentProps.title || payload.title || '排行榜' }}
            </h4>
            <p
                v-if="componentProps.subtitle || payload.notes"
                class="mt-1 text-xs leading-5 text-slate-500"
            >
                {{ componentProps.subtitle || payload.notes }}
            </p>
        </div>

        <div class="space-y-2 rounded-xl border border-white/90 bg-white/90 p-3 shadow-sm">
            <div
                v-for="(item, index) in items"
                :key="`${resolveRank(item, index)}-${resolveName(item)}`"
                class="flex items-center gap-3 rounded-xl border border-slate-100 bg-slate-50/70 px-3 py-3"
            >
                <div
                    :class="[
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold',
                        medalClass(resolveRank(item, index)),
                    ]"
                >
                    {{ resolveRank(item, index) }}
                </div>
                <div class="min-w-0 flex-1">
                    <p class="truncate text-sm font-medium text-slate-800">
                        {{ resolveName(item) }}
                    </p>
                    <p
                        v-if="item?.description"
                        class="mt-0.5 truncate text-xs leading-5 text-slate-400"
                    >
                        {{ item.description }}
                    </p>
                </div>
                <div class="ml-2 shrink-0 text-right">
                    <div class="inline-flex items-baseline justify-end gap-1 whitespace-nowrap">
                        <p class="text-base font-semibold tabular-nums text-slate-900">
                            {{ resolveValue(item) }}
                        </p>
                        <p
                            v-if="componentProps.unit"
                            class="text-[11px] font-medium uppercase tracking-wide text-slate-400"
                        >
                            {{ componentProps.unit }}
                        </p>
                    </div>
                </div>
            </div>

            <p v-if="!items.length" class="py-6 text-center text-sm text-slate-400">
                暂无排行数据
            </p>
        </div>
    </div>
</template>
