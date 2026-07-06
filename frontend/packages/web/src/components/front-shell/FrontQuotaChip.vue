<script setup>
import { computed } from 'vue';

const props = defineProps({
    tokenQuota: {
        type: Object,
        default: null,
    },
    compact: {
        type: Boolean,
        default: false,
    },
});

const isVisible = computed(() => Boolean(props.tokenQuota?.enabled));
const isUnlimited = computed(() => Boolean(props.tokenQuota?.unlimited));
const BLUE_RGB = Object.freeze([30, 92, 255]);
const YELLOW_RGB = Object.freeze([245, 204, 21]);
const RED_RGB = Object.freeze([220, 38, 38]);

// 计算剩余百分比
const remainingPercent = computed(() => {
    if (isUnlimited.value) {
        return 100;
    }
    const granted = Number(props.tokenQuota?.grantedTokens || 0);
    const remaining = Number(props.tokenQuota?.remainingTokens || 0);
    if (!Number.isFinite(granted) || granted <= 0) {
        return 0;
    }
    return Math.min(100, Math.max(0, Math.round((remaining / granted) * 100)));
});

const progressRgb = computed(() => {
    if (isUnlimited.value) {
        return BLUE_RGB;
    }

    const percent = remainingPercent.value;

    // 80% 以上保持纯蓝色，避免过早进入黄蓝混合的灰色区域
    if (percent >= 80) {
        return BLUE_RGB;
    }

    // 35% - 80% 之间从黄色过渡到蓝色
    if (percent >= 35) {
        return interpolateRgb(YELLOW_RGB, BLUE_RGB, clampRatio((percent - 35) / (80 - 35)));
    }

    // 0% - 35% 之间从红色过渡到黄色
    return interpolateRgb(RED_RGB, YELLOW_RGB, clampRatio(percent / 35));
});

const progressColor = computed(() => toRgb(progressRgb.value, 1));
const progressGlowColor = computed(() => toRgb(progressRgb.value, 0.28));
const progressSoftColor = computed(() => toRgb(progressRgb.value, 0.12));
const progressBorderColor = computed(() => toRgb(progressRgb.value, 0.22));

const toneTextStyle = computed(() => ({
    color: progressColor.value,
}));

const pillStyle = computed(() => ({
    color: progressColor.value,
    backgroundColor: progressSoftColor.value,
    borderColor: progressBorderColor.value,
}));

const summaryText = computed(() => {
    if (isUnlimited.value) {
        return '不限额';
    }
    return `${remainingPercent.value}%`;
});

const compactSummaryText = computed(() => {
    if (isUnlimited.value) {
        return '∞';
    }
    return `${remainingPercent.value}%`;
});

const statusText = computed(() => {
    if (isUnlimited.value) {
        return '不限额';
    }
    const percent = remainingPercent.value;
    if (percent <= 15) {
        return '紧张';
    }
    if (percent <= 40) {
        return '注意';
    }
    return '充足';
});

const detailText = computed(() => {
    if (isUnlimited.value) {
        return 'Token 不限额';
    }
    return `剩余 ${formatTokenCount(props.tokenQuota?.remainingTokens)} / ${formatTokenCount(props.tokenQuota?.grantedTokens)}`;
});

const remainingTokensText = computed(() =>
    isUnlimited.value ? '无限' : formatTokenCount(props.tokenQuota?.remainingTokens)
);

const grantedTokensText = computed(() =>
    isUnlimited.value ? '无限' : formatTokenCount(props.tokenQuota?.grantedTokens)
);

// 计算视觉补偿后的百分比（解决球体中段视觉偏低的问题）
const visualPercent = computed(() => {
    const p = remainingPercent.value;
    // 0% 和 100% 保持绝对准确
    if (p <= 0 || p >= 100) {
        return p;
    }
    // 在 50% 附近提供约 7% 的视觉补偿，使用正弦曲线确保平滑过渡且不影响两端
    const compensation = Math.sin((p / 100) * Math.PI) * 7;
    return Math.min(99, Math.max(1, p + compensation));
});

const orbShellStyle = computed(() => ({
    background:
        'radial-gradient(circle at 28% 24%, rgb(255 255 255 / 0.88), rgb(255 255 255 / 0.26) 20%, rgb(var(--color-bg-surface) / 0.92) 36%, rgb(var(--color-bg-surface-alt) / 0.94) 100%)',
    boxShadow: `0 10px 24px -18px rgba(15, 23, 42, 0.4), 0 0 16px ${progressGlowColor.value}, inset 0 1px 0 rgba(255, 255, 255, 0.42)`,
}));

const orbLiquidStyle = computed(() => ({
    transform: `translateY(${100 - visualPercent.value}%)`,
    background: `linear-gradient(180deg, rgba(255, 255, 255, 0.24), ${progressColor.value} 10%, ${progressColor.value} 100%)`,
    boxShadow:
        'inset 0 10px 14px rgba(255, 255, 255, 0.18), inset 0 -10px 18px rgba(2, 6, 23, 0.16)',
}));

function formatTokenCount(value) {
    const count = Number(value);
    if (!Number.isFinite(count)) {
        return '0';
    }
    return new Intl.NumberFormat('zh-CN').format(Math.max(0, Math.trunc(count)));
}

function clampRatio(value) {
    if (!Number.isFinite(value)) {
        return 0;
    }
    return Math.min(1, Math.max(0, value));
}

function interpolateRgb(start, end, ratio) {
    return start.map((channel, index) => Math.round(channel + (end[index] - channel) * ratio));
}

function toRgb(rgb, alpha = 1) {
    const [red, green, blue] = rgb;
    if (alpha >= 1) {
        return `rgb(${red}, ${green}, ${blue})`;
    }
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}
</script>

<template>
    <div v-if="isVisible" class="group relative w-full px-2">
        <!-- Compact 模式：极简科技球 -->
        <div
            v-if="compact"
            class="flex flex-col items-center gap-1.5"
            :aria-label="detailText"
            role="img"
        >
            <div
                class="relative flex h-11 w-11 items-center justify-center rounded-full border border-border-soft/70 bg-surface/84 backdrop-blur-sm transition-transform duration-200 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:hover:scale-100"
                :style="orbShellStyle"
            >
                <div
                    class="absolute inset-[4px] overflow-hidden rounded-full border border-white/20 bg-[radial-gradient(circle_at_30%_24%,rgba(255,255,255,0.18),transparent_34%),linear-gradient(180deg,rgba(255,255,255,0.1),rgba(255,255,255,0.02))]"
                ></div>
                <div class="absolute inset-[4px] overflow-hidden rounded-full">
                    <div
                        class="token-orb-liquid absolute inset-0 motion-reduce:transition-none"
                        :style="orbLiquidStyle"
                    >
                        <div class="token-orb-wave token-orb-wave-primary"></div>
                        <div class="token-orb-wave token-orb-wave-secondary"></div>
                        <div class="token-orb-bubble token-orb-bubble-a"></div>
                        <div class="token-orb-bubble token-orb-bubble-b"></div>
                        <div class="token-orb-meniscus"></div>
                        <div class="token-orb-liquid-gloss"></div>
                    </div>
                </div>
                <div
                    class="pointer-events-none absolute inset-[6px] rounded-full bg-[radial-gradient(circle_at_32%_26%,rgba(255,255,255,0.88),rgba(255,255,255,0.16)_18%,transparent_34%)]"
                ></div>
            </div>
            <span
                class="text-[9px] font-semibold tabular-nums tracking-[0.04em]"
                :style="toneTextStyle"
            >
                {{ compactSummaryText }}
            </span>
        </div>

        <!-- 非 Compact 模式 -->
        <div
            v-else
            class="inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium shadow-sm transition-colors"
            :style="pillStyle"
        >
            <span class="material-symbols-outlined text-base">auto_awesome</span>
            <span class="whitespace-nowrap">Token {{ summaryText }}</span>
        </div>

        <!-- Hover 提示 -->
        <div
            v-if="compact"
            class="pointer-events-none absolute left-[calc(100%+0.7rem)] top-1/2 z-50 hidden min-w-[196px] max-w-[220px] -translate-y-1/2 rounded-2xl border border-border-soft/80 bg-white/95 px-3 py-2.5 text-left text-[11px] text-body opacity-0 shadow-soft backdrop-blur-xl transition-all duration-150 group-hover:block group-hover:opacity-100"
        >
            <div
                class="absolute left-[-5px] top-1/2 h-2.5 w-2.5 -translate-y-1/2 rotate-45 border-b border-l border-border-soft/80 bg-white/95"
            ></div>
            <div class="flex items-center gap-2">
                <span
                    class="h-2 w-2 rounded-full"
                    :style="{ backgroundColor: progressColor }"
                ></span>
                <span class="text-[10px] font-semibold tracking-[0.14em] text-muted">
                    剩余额度
                </span>
            </div>
            <div class="mt-2 flex items-end justify-between gap-3">
                <div class="text-sm font-semibold text-strong">{{ compactSummaryText }}</div>
                <div class="text-[10px] font-semibold tracking-[0.14em]" :style="toneTextStyle">
                    {{ statusText }}
                </div>
            </div>
            <div class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-[10px]">
                <div class="text-muted">剩余</div>
                <div class="justify-self-end font-semibold text-strong">
                    {{ remainingTokensText }}
                </div>
                <div class="text-muted">总配额</div>
                <div class="justify-self-end font-semibold text-strong">
                    {{ grantedTokensText }}
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.token-orb-liquid {
    transition: transform 240ms ease-out;
}

.token-orb-wave {
    position: absolute;
    left: -30%;
    border-radius: 42%;
    width: 160%;
}

.token-orb-wave-primary {
    top: -8px;
    height: 18px;
    background: rgb(255 255 255 / 0.28);
    animation: token-orb-wave-drift 4.6s ease-in-out infinite;
}

.token-orb-wave-secondary {
    top: -5px;
    height: 14px;
    background: rgb(255 255 255 / 0.16);
    animation: token-orb-wave-drift-reverse 6.2s ease-in-out infinite;
}

.token-orb-bubble {
    position: absolute;
    bottom: 4px;
    border-radius: 9999px;
    background: rgb(255 255 255 / 0.26);
    box-shadow: 0 0 6px rgb(255 255 255 / 0.18);
}

.token-orb-bubble-a {
    left: 11px;
    width: 4px;
    height: 4px;
    animation: token-orb-bubble-rise 4.8s ease-in infinite;
}

.token-orb-bubble-b {
    left: 21px;
    width: 3px;
    height: 3px;
    animation: token-orb-bubble-rise 5.9s ease-in infinite 1.2s;
}

.token-orb-liquid-gloss {
    position: absolute;
    inset: auto 4px 5px 4px;
    height: 45%;
    border-radius: 9999px;
    background: linear-gradient(180deg, rgb(255 255 255 / 0.02), rgb(255 255 255 / 0.14));
    pointer-events: none;
}

.token-orb-meniscus {
    position: absolute;
    left: -6%;
    top: -1px;
    width: 112%;
    height: 8px;
    border-radius: 9999px;
    background: linear-gradient(180deg, rgb(255 255 255 / 0.42), rgb(255 255 255 / 0.08));
    filter: blur(0.2px);
}

@keyframes token-orb-wave-drift {
    0%,
    100% {
        transform: translateX(-4%) rotate(0deg);
    }

    50% {
        transform: translateX(6%) rotate(5deg);
    }
}

@keyframes token-orb-wave-drift-reverse {
    0%,
    100% {
        transform: translateX(5%) rotate(0deg);
    }

    50% {
        transform: translateX(-6%) rotate(-4deg);
    }
}

@keyframes token-orb-bubble-rise {
    0% {
        transform: translateY(6px) scale(0.88);
        opacity: 0;
    }

    20% {
        opacity: 0.72;
    }

    100% {
        transform: translateY(-20px) scale(1.08);
        opacity: 0;
    }
}

@media (prefers-reduced-motion: reduce) {
    .token-orb-liquid {
        transition: none;
    }

    .token-orb-wave-primary,
    .token-orb-wave-secondary,
    .token-orb-bubble-a,
    .token-orb-bubble-b {
        animation: none;
    }
}
</style>
