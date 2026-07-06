<script setup>
import {
    buildEditedImageFileName,
    renderEditedImage,
    resolveExtensionByMimeType,
} from '@/utils/imageEditor';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '应用',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);

async function handleConfirm() {
    if (props.context.rendering) {
        return;
    }
    if (!props.context.imageReady || !props.context.sourceUrl) {
        props.context.submitError = '图片尚未准备完成，请稍后重试';
        return;
    }

    props.context.rendering = true;
    props.context.submitError = '';
    try {
        const output = await renderEditedImage({
            sourceUrl: props.context.sourceUrl,
            viewportWidth: props.context.viewportWidth,
            viewportHeight: props.context.viewportHeight,
            baseScale: props.context.baseScale,
            zoom: props.context.zoom,
            offsetX: props.context.offsetX,
            offsetY: props.context.offsetY,
            outputWidth: props.context.outputWidth,
            outputHeight: props.context.outputHeight,
            mimeType: props.context.preferredMimeType,
            maxBytes: props.context.maxOutputBytes,
            initialQuality: props.context.initialQuality,
            minQuality: props.context.minQuality,
            backgroundColor: props.context.backgroundColor,
        });
        const preferredExtension =
            props.context.preferredExtension || resolveExtensionByMimeType(output.mimeType);
        const fileName = buildEditedImageFileName(
            props.context.originalFileName,
            preferredExtension,
            props.context.fileNameStem || 'image'
        );
        const file = new File([output.blob], fileName, {
            type: output.mimeType,
            lastModified: Date.now(),
        });
        emit('confirm', {
            ...output,
            file,
        });
    } catch (error) {
        props.context.submitError = error?.message || '图片处理失败，请稍后重试';
    } finally {
        props.context.rendering = false;
    }
}
</script>

<template>
    <div
        class="flex items-center justify-end gap-3 border-t border-slate-100 bg-[#f8fafc]/50 px-6 py-5"
    >
        <button
            type="button"
            class="rounded-xl px-5 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="context.rendering"
            @click="emit('cancel')"
        >
            {{ cancelText }}
        </button>
        <button
            type="button"
            class="rounded-xl bg-primary px-6 py-2.5 text-sm font-medium text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 active:scale-95 disabled:cursor-not-allowed disabled:opacity-75"
            :disabled="context.rendering"
            @click="handleConfirm"
        >
            {{ context.rendering ? '处理中...' : confirmText }}
        </button>
    </div>
</template>
