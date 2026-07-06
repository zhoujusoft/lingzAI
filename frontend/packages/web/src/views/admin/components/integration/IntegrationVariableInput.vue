<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
    modelValue: {
        type: String,
        default: '',
    },
    groups: {
        type: Array,
        default: () => [],
    },
    placeholder: {
        type: String,
        default: '',
    },
    multiline: {
        type: Boolean,
        default: false,
    },
    rows: {
        type: Number,
        default: 4,
    },
    disabled: {
        type: Boolean,
        default: false,
    },
    invalid: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['update:modelValue', 'blur']);

const inputRef = ref(null);
const textareaRef = ref(null);
const dropdownOpen = ref(false);
const selectionStart = ref(0);
const selectionEnd = ref(0);

const activeRef = computed(() => (props.multiline ? textareaRef.value : inputRef.value));

function syncSelection() {
    const element = activeRef.value;
    if (!element) {
        return;
    }
    selectionStart.value = element.selectionStart ?? 0;
    selectionEnd.value = element.selectionEnd ?? selectionStart.value;
}

function updateValue(value) {
    emit('update:modelValue', value);
}

function insertVariable(key) {
    const element = activeRef.value;
    const token = `$${key}$`;
    const currentValue = props.modelValue || '';
    const start = selectionStart.value;
    const end = selectionEnd.value;
    const nextValue = `${currentValue.slice(0, start)}${token}${currentValue.slice(end)}`;
    updateValue(nextValue);
    dropdownOpen.value = false;
    nextTick(() => {
        const target = activeRef.value;
        if (!target) {
            return;
        }
        const nextCursor = start + token.length;
        target.focus();
        target.setSelectionRange(nextCursor, nextCursor);
        selectionStart.value = nextCursor;
        selectionEnd.value = nextCursor;
    });
}

function handleDocumentClick(event) {
    if (!dropdownOpen.value) {
        return;
    }
    if (activeRef.value?.contains(event.target)) {
        return;
    }
    if (event.target?.closest?.('[data-variable-panel="true"]')) {
        return;
    }
    if (event.target?.closest?.('[data-variable-trigger="true"]')) {
        return;
    }
    dropdownOpen.value = false;
}

watch(
    () => props.modelValue,
    () => nextTick(syncSelection)
);

onMounted(() => {
    document.addEventListener('click', handleDocumentClick);
});

onBeforeUnmount(() => {
    document.removeEventListener('click', handleDocumentClick);
});
</script>

<template>
    <div class="relative">
        <input
            v-if="!multiline"
            ref="inputRef"
            :value="modelValue"
            :disabled="disabled"
            :placeholder="placeholder"
            :class="[
                'w-full rounded-xl border bg-white px-4 py-2.5 pr-16 text-sm outline-none transition disabled:cursor-not-allowed disabled:bg-slate-100',
                invalid
                    ? 'border-rose-300 focus:border-rose-400 focus:ring-2 focus:ring-rose-100'
                    : 'border-slate-200 focus:border-primary focus:ring-2 focus:ring-primary/10',
            ]"
            type="text"
            @input="event => updateValue(event.target.value)"
            @click="syncSelection"
            @keyup="syncSelection"
            @focus="syncSelection"
            @blur="emit('blur')"
        />
        <textarea
            v-else
            ref="textareaRef"
            :value="modelValue"
            :disabled="disabled"
            :placeholder="placeholder"
            :rows="rows"
            :class="[
                'custom-scrollbar w-full rounded-2xl border bg-white px-4 py-3 pr-16 font-mono text-sm leading-6 outline-none transition disabled:cursor-not-allowed disabled:bg-slate-100',
                invalid
                    ? 'border-rose-300 focus:border-rose-400 focus:ring-2 focus:ring-rose-100'
                    : 'border-slate-200 focus:border-primary focus:ring-2 focus:ring-primary/10',
            ]"
            @input="event => updateValue(event.target.value)"
            @click="syncSelection"
            @keyup="syncSelection"
            @focus="syncSelection"
            @blur="emit('blur')"
        />

        <button
            v-if="!disabled"
            data-variable-trigger="true"
            class="absolute right-3 top-3 rounded-lg border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-600 transition hover:border-primary hover:text-primary"
            type="button"
            @click="dropdownOpen = !dropdownOpen"
        >
            变量
        </button>

        <div
            v-if="dropdownOpen && groups.length"
            data-variable-panel="true"
            class="absolute right-0 top-full z-20 mt-2 w-[320px] rounded-2xl border border-slate-200 bg-white p-3 shadow-2xl"
        >
            <div class="custom-scrollbar max-h-80 overflow-y-auto">
                <div v-for="group in groups" :key="group.label" class="mb-3 last:mb-0">
                    <div
                        class="mb-2 text-xs font-semibold uppercase tracking-[0.12em] text-slate-400"
                    >
                        {{ group.label }}
                    </div>
                    <div class="space-y-1">
                        <button
                            v-for="item in group.items"
                            :key="item.key"
                            class="flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
                            type="button"
                            @click="insertVariable(item.key)"
                        >
                            <span>{{ item.label }}</span>
                            <span class="truncate pl-3 font-mono text-xs text-slate-400">
                                {{ item.key }}
                            </span>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
