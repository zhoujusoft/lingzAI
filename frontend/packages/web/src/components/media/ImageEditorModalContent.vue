<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { clampNumber } from '@/utils/imageEditor';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const viewportRef = ref(null);
const imageRef = ref(null);
const isDragging = ref(false);
const previewScale = computed(() => {
    if (!props.context.viewportWidth) {
        return 1;
    }
    return 88 / props.context.viewportWidth;
});
const cropSurfaceClass = computed(() => {
    if (props.context.cropShape === 'circle' && props.context.aspectRatio === 1) {
        return 'rounded-full';
    }
    return 'rounded-[28px]';
});
const totalScale = computed(() => (props.context.baseScale || 1) * (props.context.zoom || 1));
const imageStyle = computed(() => buildImageStyle(totalScale.value, 1));
const previewImageStyle = computed(() => buildImageStyle(totalScale.value, previewScale.value));
const previewHeight = computed(() => {
    const aspectRatio = props.context.aspectRatio || 1;
    return Math.max(56, Math.round(88 / aspectRatio));
});

let pointerState = null;
let resizeObserver = null;

function buildImageStyle(currentScale, scaleFactor) {
    const safeScale = Math.max(currentScale * scaleFactor, 0.0001);
    const translateX = (props.context.offsetX || 0) * scaleFactor;
    const translateY = (props.context.offsetY || 0) * scaleFactor;
    return {
        width: `${props.context.naturalWidth || 0}px`,
        height: `${props.context.naturalHeight || 0}px`,
        transform: `translate3d(-50%, -50%, 0) translate3d(${translateX}px, ${translateY}px, 0) scale(${safeScale})`,
        transformOrigin: 'center center',
    };
}

function ensureSourceUrl() {
    if (props.context.sourceUrl || !props.context.file) {
        return;
    }
    props.context.sourceUrl = URL.createObjectURL(props.context.file);
    props.context.ownsSourceUrl = true;
}

function revokeSourceUrl() {
    if (props.context.ownsSourceUrl && props.context.sourceUrl) {
        URL.revokeObjectURL(props.context.sourceUrl);
        props.context.sourceUrl = '';
        props.context.ownsSourceUrl = false;
    }
}

function handleImageLoad() {
    props.context.naturalWidth = imageRef.value?.naturalWidth || 0;
    props.context.naturalHeight = imageRef.value?.naturalHeight || 0;
    props.context.imageReady = props.context.naturalWidth > 0 && props.context.naturalHeight > 0;
    props.context.zoom = 1;
    props.context.offsetX = 0;
    props.context.offsetY = 0;
    props.context.submitError = '';
    nextTick(() => syncViewportMetrics());
}

function syncViewportMetrics() {
    const viewport = viewportRef.value;
    if (!viewport) {
        return;
    }
    const nextWidth = Math.round(viewport.clientWidth || 0);
    const nextHeight = Math.round(viewport.clientHeight || 0);
    if (!nextWidth || !nextHeight || !props.context.imageReady) {
        props.context.viewportWidth = nextWidth || props.context.viewportWidth;
        props.context.viewportHeight = nextHeight || props.context.viewportHeight;
        return;
    }
    props.context.viewportWidth = nextWidth;
    props.context.viewportHeight = nextHeight;
    props.context.baseScale = Math.max(
        nextWidth / props.context.naturalWidth,
        nextHeight / props.context.naturalHeight
    );
    clampOffsets();
}

function clampOffsets(nextX = props.context.offsetX, nextY = props.context.offsetY) {
    if (!props.context.imageReady) {
        return;
    }
    const renderedWidth = props.context.naturalWidth * totalScale.value;
    const renderedHeight = props.context.naturalHeight * totalScale.value;
    const maxOffsetX = Math.max(0, (renderedWidth - props.context.viewportWidth) / 2);
    const maxOffsetY = Math.max(0, (renderedHeight - props.context.viewportHeight) / 2);
    props.context.offsetX = clampNumber(nextX, -maxOffsetX, maxOffsetX);
    props.context.offsetY = clampNumber(nextY, -maxOffsetY, maxOffsetY);
}

function updateZoom(nextValue) {
    props.context.zoom = clampNumber(
        Number(nextValue) || 1,
        props.context.minZoom || 0.6,
        props.context.maxZoom || 3
    );
    clampOffsets();
}

function adjustZoom(delta) {
    updateZoom((props.context.zoom || 1) + delta);
}

function handlePointerDown(event) {
    if (!props.context.imageReady || props.context.rendering) {
        return;
    }
    event.preventDefault();
    pointerState = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        originX: props.context.offsetX || 0,
        originY: props.context.offsetY || 0,
    };
    isDragging.value = true;
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
}

function handlePointerMove(event) {
    if (!pointerState || event.pointerId !== pointerState.pointerId) {
        return;
    }
    const deltaX = event.clientX - pointerState.startX;
    const deltaY = event.clientY - pointerState.startY;
    clampOffsets(pointerState.originX + deltaX, pointerState.originY + deltaY);
}

function handlePointerUp(event) {
    if (!pointerState || event.pointerId !== pointerState.pointerId) {
        return;
    }
    pointerState = null;
    isDragging.value = false;
    window.removeEventListener('pointermove', handlePointerMove);
    window.removeEventListener('pointerup', handlePointerUp);
    window.removeEventListener('pointercancel', handlePointerUp);
}

watch(
    () => props.context.zoom,
    nextValue => {
        updateZoom(nextValue);
    }
);

onMounted(() => {
    ensureSourceUrl();
    nextTick(() => syncViewportMetrics());
    if (typeof ResizeObserver !== 'undefined') {
        resizeObserver = new ResizeObserver(() => syncViewportMetrics());
        if (viewportRef.value) {
            resizeObserver.observe(viewportRef.value);
        }
    } else {
        window.addEventListener('resize', syncViewportMetrics);
    }
});

onBeforeUnmount(() => {
    handlePointerUp({ pointerId: pointerState?.pointerId });
    if (resizeObserver) {
        resizeObserver.disconnect();
        resizeObserver = null;
    } else {
        window.removeEventListener('resize', syncViewportMetrics);
    }
    revokeSourceUrl();
});
</script>

<template>
    <div class="flex flex-col gap-6 p-6 lg:flex-row">
        <div class="min-w-0 flex-1 space-y-4">
            <div
                ref="viewportRef"
                class="relative mx-auto aspect-square w-full max-w-[360px] overflow-hidden bg-[radial-gradient(circle_at_top,_rgba(96,165,250,0.12),_transparent_58%),linear-gradient(135deg,#eff6ff,#e2e8f0)] shadow-inner touch-none"
                :class="[cropSurfaceClass, isDragging ? 'cursor-grabbing' : 'cursor-grab']"
                :style="
                    props.context.aspectRatio !== 1
                        ? { aspectRatio: String(props.context.aspectRatio) }
                        : null
                "
                @pointerdown="handlePointerDown"
            >
                <img
                    v-if="context.sourceUrl"
                    ref="imageRef"
                    :src="context.sourceUrl"
                    alt="待裁剪图片"
                    class="absolute left-1/2 top-1/2 max-w-none select-none"
                    :style="imageStyle"
                    draggable="false"
                    @load="handleImageLoad"
                />
            </div>

            <div
                class="rounded-[26px] border border-slate-200 bg-[linear-gradient(180deg,rgba(255,255,255,0.94),rgba(241,245,249,0.9))] p-4 shadow-[0_20px_46px_-34px_rgba(15,23,42,0.35)]"
            >
                <div
                    class="flex items-center justify-between gap-3 border-b border-slate-200/80 pb-3"
                >
                    <div>
                        <p class="text-sm font-semibold tracking-[0.02em] text-slate-800">
                            缩放与定位
                        </p>
                        <p class="mt-1 text-xs text-slate-500">
                            拖动图片调整位置，使用缩放控制主体大小。
                        </p>
                    </div>
                    <div
                        class="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700 shadow-[0_12px_26px_-20px_rgba(15,23,42,0.48)]"
                    >
                        {{ Math.round((context.zoom || 1) * 100) }}%
                    </div>
                </div>
                <div class="mt-4 flex items-center gap-3">
                    <button
                        type="button"
                        class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-600 shadow-[0_14px_30px_-20px_rgba(15,23,42,0.38)] transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:text-slate-900 hover:shadow-[0_18px_36px_-20px_rgba(15,23,42,0.44)] disabled:translate-y-0 disabled:cursor-not-allowed disabled:opacity-45"
                        :disabled="context.rendering"
                        @click="adjustZoom(-0.08)"
                    >
                        <span class="material-symbols-outlined text-[18px]">remove</span>
                    </button>
                    <div
                        class="flex min-w-0 flex-1 items-center gap-3 rounded-2xl border border-slate-200/90 bg-white/95 px-3 py-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.92)]"
                    >
                        <span
                            class="shrink-0 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-400"
                        >
                            小
                        </span>
                        <input
                            :value="context.zoom"
                            type="range"
                            :min="context.minZoom || 0.6"
                            :max="context.maxZoom || 3"
                            step="0.01"
                            class="image-editor-slider h-2.5 w-full cursor-pointer appearance-none rounded-full"
                            :disabled="context.rendering"
                            @input="updateZoom($event.target.value)"
                        />
                        <span
                            class="shrink-0 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-400"
                        >
                            大
                        </span>
                    </div>
                    <button
                        type="button"
                        class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-600 shadow-[0_14px_30px_-20px_rgba(15,23,42,0.38)] transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:text-slate-900 hover:shadow-[0_18px_36px_-20px_rgba(15,23,42,0.44)] disabled:translate-y-0 disabled:cursor-not-allowed disabled:opacity-45"
                        :disabled="context.rendering"
                        @click="adjustZoom(0.08)"
                    >
                        <span class="material-symbols-outlined text-[18px]">add</span>
                    </button>
                </div>
            </div>
        </div>

        <aside class="w-full shrink-0 space-y-4 lg:w-[260px]">
            <div class="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                <p class="text-sm font-semibold text-slate-800">预览</p>
                <div
                    class="relative mt-4 overflow-hidden border border-white bg-white shadow-sm"
                    :class="cropSurfaceClass"
                    :style="{ width: '88px', height: `${previewHeight}px` }"
                >
                    <img
                        v-if="context.sourceUrl"
                        :src="context.sourceUrl"
                        alt="裁剪预览"
                        class="absolute left-1/2 top-1/2 max-w-none select-none"
                        :style="previewImageStyle"
                        draggable="false"
                    />
                </div>
                <dl class="mt-4 space-y-2 text-xs text-slate-500">
                    <div class="flex items-center justify-between gap-3">
                        <dt>输出尺寸</dt>
                        <dd class="font-medium text-slate-700">
                            {{ context.outputWidth }} x {{ context.outputHeight }}
                        </dd>
                    </div>
                    <div class="flex items-center justify-between gap-3">
                        <dt>输出格式</dt>
                        <dd class="font-medium uppercase text-slate-700">
                            {{
                                String(context.preferredMimeType || '').replace('image/', '') ||
                                'jpeg'
                            }}
                        </dd>
                    </div>
                </dl>
            </div>

            <div class="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600">
                <p class="font-semibold text-slate-800">操作说明</p>
                <p class="mt-2 leading-6">
                    {{ context.helperText || '调整图片显示区域后应用。' }}
                </p>
            </div>

            <div
                v-if="context.submitError"
                class="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
            >
                {{ context.submitError }}
            </div>
        </aside>
    </div>
</template>

<style scoped>
.image-editor-slider {
    background: linear-gradient(90deg, rgba(37, 99, 235, 0.16), rgba(59, 130, 246, 0.42)), #e2e8f0;
}

.image-editor-slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 9999px;
    border: none;
    background:
        radial-gradient(circle at 32% 32%, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.28)),
        linear-gradient(135deg, #2563eb, #0f172a);
    box-shadow:
        0 10px 22px -10px rgba(37, 99, 235, 0.6),
        0 0 0 3px rgba(255, 255, 255, 0.95);
}

.image-editor-slider::-moz-range-thumb {
    width: 18px;
    height: 18px;
    border-radius: 9999px;
    border: none;
    background:
        radial-gradient(circle at 32% 32%, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.28)),
        linear-gradient(135deg, #2563eb, #0f172a);
    box-shadow:
        0 10px 22px -10px rgba(37, 99, 235, 0.6),
        0 0 0 3px rgba(255, 255, 255, 0.95);
}
</style>
