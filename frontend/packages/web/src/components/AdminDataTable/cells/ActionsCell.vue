<script setup>
const props = defineProps({
    row: {
        type: Object,
        default: () => ({}),
    },
    actions: {
        type: Array,
        default: () => [],
    },
    containerClass: {
        type: String,
        default: '',
    },
});

function resolveText(action) {
    return typeof action.label === 'function' ? action.label(props.row) : action.label;
}

function resolveClass(action) {
    if (!action.class) {
        return 'text-sm font-medium text-slate-500 hover:text-slate-700';
    }

    return typeof action.class === 'function' ? action.class(props.row) : action.class;
}

function isDisabled(action) {
    return typeof action.disabled === 'function' ? action.disabled(props.row) : !!action.disabled;
}

function isVisible(action) {
    if (typeof action.visible === 'function') {
        return action.visible(props.row);
    }

    return action.visible !== false;
}

function handleClick(action) {
    if (isDisabled(action) || typeof action.onClick !== 'function') {
        return;
    }

    action.onClick(props.row);
}
</script>

<template>
    <div :class="['flex items-center gap-3', containerClass || 'justify-end']">
        <button
            v-for="(action, index) in actions"
            v-show="isVisible(action)"
            :key="action.key || (typeof action.label === 'string' ? action.label : index)"
            type="button"
            :disabled="isDisabled(action)"
            :class="[resolveClass(action), isDisabled(action) ? 'cursor-not-allowed opacity-50' : '']"
            @click="handleClick(action)"
        >
            {{ resolveText(action) }}
        </button>
    </div>
</template>
