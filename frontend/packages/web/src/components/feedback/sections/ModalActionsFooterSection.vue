<script setup>
import { MODAL_FOOTER_LAYOUT } from '@/components/feedback/constants/modalEnums';

const props = defineProps({
    confirmText: {
        type: String,
        default: '确认',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
    destructive: {
        type: Boolean,
        default: false,
    },
    showCancel: {
        type: Boolean,
        default: true,
    },
    layout: {
        type: String,
        default: MODAL_FOOTER_LAYOUT.COMPACT,
        validator: value => Object.values(MODAL_FOOTER_LAYOUT).includes(value),
    },
    context: {
        type: Object,
        default: () => ({}),
    },
    resolveWith: {
        type: Function,
        default: null,
    },
});

const emit = defineEmits(['confirm', 'cancel']);

function onConfirm() {
    if (props.resolveWith) {
        emit('confirm', props.resolveWith(props.context));
        return;
    }

    emit('confirm', true);
}
</script>

<template>
    <div
        :class="
            layout === MODAL_FOOTER_LAYOUT.PANEL
                ? 'flex items-center justify-end gap-3 border-t border-slate-100 bg-[#f8fafc]/50 px-8 py-5'
                : 'mt-6 flex items-center justify-end gap-3 px-6 pb-6'
        "
    >
        <button
            v-if="showCancel"
            type="button"
            :class="
                layout === MODAL_FOOTER_LAYOUT.PANEL
                    ? 'rounded-xl px-6 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100'
                    : 'rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50'
            "
            @click="emit('cancel')"
        >
            {{ cancelText }}
        </button>
        <button
            type="button"
            :class="
                layout === MODAL_FOOTER_LAYOUT.PANEL
                    ? [
                          'rounded-xl px-8 py-2.5 text-sm font-medium text-white transition-all active:scale-95',
                          destructive ? 'bg-red-600 hover:bg-red-700' : 'bg-primary shadow-lg shadow-blue-500/30 hover:bg-blue-700',
                      ]
                    : [
                          'rounded-lg px-4 py-2 text-sm font-medium text-white transition',
                          destructive ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700',
                      ]
            "
            @click="onConfirm"
        >
            {{ confirmText }}
        </button>
    </div>
</template>
