<script setup>
import { computed } from 'vue';
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue';

const props = defineProps({
    modelValue: {
        type: [String, Number, Boolean, Object],
        default: '',
    },
    options: {
        type: Array,
        default: () => [],
    },
    placeholder: {
        type: String,
        default: '请选择',
    },
    size: {
        type: String,
        default: 'md',
        validator: value => ['sm', 'md'].includes(value),
    },
    menuPlacement: {
        type: String,
        default: 'bottom',
        validator: value => ['bottom', 'top'].includes(value),
    },
    fullWidth: {
        type: Boolean,
        default: true,
    },
    disabled: {
        type: Boolean,
        default: false,
    },
    buttonClass: {
        type: String,
        default: '',
    },
    menuClass: {
        type: String,
        default: '',
    },
    optionClass: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['update:modelValue']);

function normalizeOption(option, index) {
    if (option && typeof option === 'object') {
        const value = option.value ?? option.id ?? option.key ?? index;
        const label = option.label ?? option.name ?? value;
        return {
            key: option.key ?? `${String(value)}-${index}`,
            value,
            label: String(label ?? '').trim(),
            description: String(option.description ?? '').trim(),
            disabled: Boolean(option.disabled),
        };
    }

    return {
        key: `${String(option ?? '')}-${index}`,
        value: option,
        label: String(option ?? '').trim(),
        description: '',
        disabled: false,
    };
}

const normalizedOptions = computed(() =>
    (Array.isArray(props.options) ? props.options : []).map(normalizeOption)
);

const selectedOption = computed(() =>
    normalizedOptions.value.find(option => option.value === props.modelValue) || null
);

const selectedLabel = computed(() => selectedOption.value?.label || props.placeholder);

const proxyValue = computed({
    get() {
        return props.modelValue;
    },
    set(value) {
        emit('update:modelValue', value);
    },
});

const wrapperClass = computed(() => (props.fullWidth ? 'w-full' : 'w-fit'));

const buttonSizeClass = computed(() =>
    props.size === 'sm'
        ? 'h-9 min-w-[96px] rounded-xl px-3 pr-10 text-xs font-semibold'
        : 'min-h-[48px] rounded-xl px-4 py-3 pr-11 text-sm font-medium'
);

const buttonClasses = computed(() => [
    'relative flex items-center justify-between gap-3 border border-slate-200 bg-white/95 text-left text-slate-700 outline-none shadow-[0_10px_30px_-20px_rgba(15,23,42,0.4)] transition duration-150 hover:border-slate-300 hover:bg-white focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/15 disabled:cursor-not-allowed disabled:opacity-60',
    props.fullWidth ? 'w-full' : '',
    buttonSizeClass.value,
    props.buttonClass,
]);

const menuPlacementClass = computed(() =>
    props.menuPlacement === 'top'
        ? 'bottom-[calc(100%+0.5rem)] left-0 min-w-full'
        : 'top-[calc(100%+0.5rem)] left-0 min-w-full'
);

const menuClasses = computed(() => [
    'absolute z-30 overflow-hidden rounded-xl border border-slate-200 bg-white/95 p-1.5 shadow-[0_20px_48px_-20px_rgba(15,23,42,0.38)] ring-1 ring-slate-100/80 backdrop-blur-sm focus:outline-none',
    menuPlacementClass.value,
    props.menuClass,
]);
</script>

<template>
    <Listbox v-model="proxyValue" :disabled="disabled" v-slot="{ open }">
        <div :class="['relative', wrapperClass]" @click.stop>
            <ListboxButton :class="buttonClasses">
                <span
                    class="min-w-0 truncate"
                    :class="selectedOption ? 'text-slate-700' : 'text-slate-400'"
                >
                    {{ selectedLabel }}
                </span>
                <span
                    class="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-slate-400 transition duration-150"
                    :class="open ? 'rotate-180 text-primary' : ''"
                >
                    expand_more
                </span>
            </ListboxButton>

            <transition
                enter-active-class="transition duration-150 ease-out"
                enter-from-class="translate-y-1 opacity-0"
                enter-to-class="translate-y-0 opacity-100"
                leave-active-class="transition duration-100 ease-in"
                leave-from-class="translate-y-0 opacity-100"
                leave-to-class="translate-y-1 opacity-0"
            >
                <ListboxOptions v-if="open" as="ul" :class="menuClasses">
                    <ListboxOption
                        v-for="option in normalizedOptions"
                        :key="option.key"
                        v-slot="{ active, selected, disabled: optionDisabled }"
                        :value="option.value"
                        :disabled="option.disabled"
                        as="template"
                    >
                        <li
                            :class="[
                                'flex cursor-pointer items-center justify-between gap-3 rounded-lg px-3 py-2.5 transition-colors',
                                props.size === 'sm' ? 'text-xs' : 'text-sm',
                                active ? 'bg-blue-50 text-primary' : 'text-slate-700',
                                selected ? 'font-semibold' : 'font-medium',
                                optionDisabled ? 'cursor-not-allowed opacity-50' : '',
                                props.optionClass,
                            ]"
                        >
                            <div class="min-w-0 flex-1">
                                <div class="truncate">{{ option.label }}</div>
                                <div
                                    v-if="option.description"
                                    class="mt-0.5 truncate text-[11px] font-normal text-slate-400"
                                >
                                    {{ option.description }}
                                </div>
                            </div>
                            <span
                                v-if="selected"
                                class="material-symbols-outlined shrink-0 text-base text-primary"
                            >
                                check
                            </span>
                        </li>
                    </ListboxOption>
                </ListboxOptions>
            </transition>
        </div>
    </Listbox>
</template>
