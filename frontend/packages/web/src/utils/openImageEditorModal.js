import { reactive } from 'vue';
import { openModal } from '@/composables/useModal';
import ImageEditorModalContent from '@/components/media/ImageEditorModalContent.vue';
import ImageEditorModalFooter from '@/components/media/ImageEditorModalFooter.vue';
import {
    normalizeImageExtension,
    resolveExtensionByMimeType,
    resolveOutputDimensions,
} from '@/utils/imageEditor';

export async function openImageEditorModal(options = {}) {
    const aspectRatio =
        Number.isFinite(options.aspectRatio) && options.aspectRatio > 0 ? options.aspectRatio : 1;
    const outputDimensions = resolveOutputDimensions({
        aspectRatio,
        outputSize: options.outputSize,
        outputWidth: options.outputWidth,
        outputHeight: options.outputHeight,
    });
    const preferredMimeType =
        String(options.preferredMimeType || 'image/jpeg').trim() || 'image/jpeg';
    const preferredExtension = normalizeImageExtension(
        options.preferredExtension || resolveExtensionByMimeType(preferredMimeType),
        '.jpg'
    );

    const context = reactive({
        file: options.file || null,
        sourceUrl: '',
        ownsSourceUrl: false,
        originalFileName: options.file?.name || '',
        fileNameStem: options.fileNameStem || 'image',
        aspectRatio,
        cropShape: options.cropShape || (aspectRatio === 1 ? 'circle' : 'rounded'),
        helperText: String(options.helperText || '').trim(),
        viewportWidth: Number.isFinite(options.viewportWidth) ? options.viewportWidth : 320,
        viewportHeight: Number.isFinite(options.viewportHeight)
            ? options.viewportHeight
            : Math.round(
                  (Number.isFinite(options.viewportWidth) ? options.viewportWidth : 320) /
                      aspectRatio
              ),
        naturalWidth: 0,
        naturalHeight: 0,
        imageReady: false,
        baseScale: 1,
        zoom: 1,
        minZoom: Number.isFinite(options.minZoom) ? options.minZoom : 0.6,
        maxZoom: Number.isFinite(options.maxZoom) ? options.maxZoom : 3,
        offsetX: 0,
        offsetY: 0,
        outputWidth: outputDimensions.width,
        outputHeight: outputDimensions.height,
        preferredMimeType,
        preferredExtension,
        backgroundColor: options.backgroundColor || '#ffffff',
        maxOutputBytes: Number.isFinite(options.maxOutputBytes)
            ? options.maxOutputBytes
            : Number.POSITIVE_INFINITY,
        initialQuality: Number.isFinite(options.initialQuality) ? options.initialQuality : 0.9,
        minQuality: Number.isFinite(options.minQuality) ? options.minQuality : 0.72,
        rendering: false,
        submitError: '',
    });

    return openModal({
        title: options.title || '调整图片',
        showClose: true,
        showCancel: true,
        stackOnActive: true,
        panelClass: options.panelClass || '!max-w-[980px]',
        content: {
            component: ImageEditorModalContent,
        },
        footer: {
            component: ImageEditorModalFooter,
            props: {
                confirmText: options.confirmText || '应用',
                cancelText: options.cancelText || '取消',
            },
        },
        context,
    });
}
