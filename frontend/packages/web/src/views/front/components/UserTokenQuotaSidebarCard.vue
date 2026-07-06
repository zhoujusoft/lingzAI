<script setup>
import { computed } from 'vue';

const props = defineProps({
    tokenQuota: {
        type: Object,
        default: null,
    },
});

const showCard = computed(() => Boolean(props.tokenQuota?.enabled));
const isUnlimited = computed(() => Boolean(props.tokenQuota?.unlimited));

const usagePercent = computed(() => {
    if (isUnlimited.value) {
        return 0;
    }
    const granted = Number(props.tokenQuota?.grantedTokens);
    const consumed = Number(props.tokenQuota?.consumedTokens);
    if (!Number.isFinite(granted) || granted <= 0 || !Number.isFinite(consumed) || consumed <= 0) {
        return 0;
    }
    return Math.min(100, Math.max(0, Math.round((consumed / granted) * 100)));
});

const quotaStatusText = computed(() => {
    if (usagePercent.value >= 90) {
        return '额度偏紧';
    }
    if (usagePercent.value >= 60) {
        return '持续使用中';
    }
    return '额度充足';
});

function formatTokenCount(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '0';
    }
    return new Intl.NumberFormat('zh-CN').format(Math.max(0, Math.trunc(number)));
}
</script>

<template>
    <div
        v-if="showCard"
        class="mb-3 overflow-hidden rounded-2xl border border-slate-200 bg-[linear-gradient(160deg,rgba(248,250,252,0.98),rgba(239,246,255,0.96))] p-4 shadow-sm"
    >
        <div class="flex items-start justify-between gap-3">
            <div>
                <p class="text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-400">
                    Token 配额
                </p>
                <p class="mt-1 text-sm font-semibold text-slate-800">
                    {{
                        isUnlimited
                            ? '无限制'
                            : `剩余 ${formatTokenCount(tokenQuota?.remainingTokens)}`
                    }}
                </p>
            </div>
            <div
                class="rounded-full bg-white/80 px-2.5 py-1 text-[11px] font-semibold text-sky-600"
            >
                {{ isUnlimited ? '不限量' : quotaStatusText }}
            </div>
        </div>

        <div v-if="!isUnlimited" class="mt-3">
            <div class="relative h-2 overflow-hidden rounded-full bg-slate-200/90">
                <div
                    class="absolute inset-y-0 left-0 rounded-full bg-[linear-gradient(90deg,#0891b2,#22c55e,#38bdf8)] transition-all duration-300 ease-out"
                    :style="{ width: `${usagePercent}%` }"
                ></div>
            </div>
        </div>

        <div class="mt-3 grid grid-cols-2 gap-2 text-xs">
            <div class="rounded-xl bg-white/75 px-3 py-2">
                <p class="text-slate-400">已用</p>
                <p class="mt-1 font-semibold text-slate-700">
                    {{ formatTokenCount(tokenQuota?.consumedTokens) }}
                </p>
            </div>
            <div class="rounded-xl bg-white/75 px-3 py-2">
                <p class="text-slate-400">总额度</p>
                <p class="mt-1 font-semibold text-slate-700">
                    {{ isUnlimited ? '无限制' : formatTokenCount(tokenQuota?.grantedTokens) }}
                </p>
            </div>
        </div>

        <div class="mt-2 flex items-center justify-between text-[11px] text-slate-500">
            <span>{{ isUnlimited ? '当前用户不受额度限制' : `已使用 ${usagePercent}%` }}</span>
            <span>聊天时自动更新</span>
        </div>
    </div>
</template>
