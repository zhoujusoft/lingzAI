<script setup>
import { computed } from 'vue';

const props = defineProps({
    currentStep: {
        type: Number,
        required: true,
    },
    labels: {
        type: Array,
        default: () => ['上传数据', '文本分段与清洗', '处理完成'],
    },
});

const steps = computed(() =>
    props.labels.map((label, index) => ({
        index: index + 1,
        label,
    })),
);
</script>

<template>
    <div class="flex items-center gap-4" data-component="KnowledgeStepProgress">
        <template v-for="(step, idx) in steps" :key="step.index">
            <div class="flex items-center gap-2">
                <div
                    v-if="step.index < currentStep"
                    class="flex h-6 w-6 items-center justify-center rounded-full bg-blue-100 text-primary"
                >
                    <span class="material-symbols-outlined text-sm font-bold">check</span>
                </div>
                <div
                    v-else-if="step.index === currentStep"
                    class="flex h-6 w-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-white"
                >
                    {{ step.index }}
                </div>
                <div
                    v-else
                    class="flex h-6 w-6 items-center justify-center rounded-full border border-slate-300 text-xs text-slate-400"
                >
                    {{ step.index }}
                </div>

                <span
                    class="text-sm"
                    :class="
                        step.index === currentStep
                            ? 'font-bold text-primary'
                            : 'font-medium text-slate-400'
                    "
                >
                    {{ step.label }}
                </span>
            </div>

            <div v-if="idx < steps.length - 1" class="h-px w-12 bg-slate-200" />
        </template>
    </div>
</template>
