export const DEFAULT_IMAGE_EDITOR_EXTENSIONS = ['.jpg', '.jpeg', '.png'];
export const DEFAULT_IMAGE_EDITOR_MIME_TYPES = ['image/jpeg', 'image/png'];

export function clampNumber(value, min, max) {
    if (!Number.isFinite(value)) {
        return min;
    }
    return Math.min(Math.max(value, min), max);
}

export function validateImageFile(
    file,
    {
        maxBytes = Number.POSITIVE_INFINITY,
        allowedExtensions = DEFAULT_IMAGE_EDITOR_EXTENSIONS,
        allowedMimeTypes = DEFAULT_IMAGE_EDITOR_MIME_TYPES,
    } = {}
) {
    if (!file) {
        return '请先选择图片文件';
    }
    if (Number.isFinite(maxBytes) && file.size > maxBytes) {
        return `图片文件不能超过 ${formatFileSize(maxBytes)}`;
    }
    const extension = extractFileExtension(file.name || '');
    if (allowedExtensions.length > 0 && !allowedExtensions.includes(extension)) {
        return '图片仅支持 JPG 或 PNG 格式';
    }
    if (file.type && allowedMimeTypes.length > 0 && !allowedMimeTypes.includes(file.type)) {
        return '图片仅支持 JPG 或 PNG 格式';
    }
    return '';
}

export function resolveOutputDimensions({
    aspectRatio = 1,
    outputSize = 512,
    outputWidth = null,
    outputHeight = null,
} = {}) {
    const safeAspectRatio = Number.isFinite(aspectRatio) && aspectRatio > 0 ? aspectRatio : 1;
    if (
        Number.isFinite(outputWidth) &&
        Number.isFinite(outputHeight) &&
        outputWidth > 0 &&
        outputHeight > 0
    ) {
        return {
            width: Math.round(outputWidth),
            height: Math.round(outputHeight),
        };
    }
    const safeOutputSize =
        Number.isFinite(outputSize) && outputSize > 0 ? Math.round(outputSize) : 512;
    if (safeAspectRatio >= 1) {
        return {
            width: safeOutputSize,
            height: Math.max(1, Math.round(safeOutputSize / safeAspectRatio)),
        };
    }
    return {
        width: Math.max(1, Math.round(safeOutputSize * safeAspectRatio)),
        height: safeOutputSize,
    };
}

export function buildEditedImageFileName(
    originalName,
    preferredExtension = '.jpg',
    fallbackStem = 'image'
) {
    const trimmedName = String(originalName || '').trim();
    const extension = normalizeImageExtension(preferredExtension, '.jpg');
    if (!trimmedName) {
        return `${fallbackStem}${extension}`;
    }
    const baseName = trimmedName.replace(/\.[^.]+$/, '').trim() || fallbackStem;
    return `${baseName}${extension}`;
}

export function normalizeImageExtension(extension, fallback = '.jpg') {
    const normalized = String(extension || '')
        .trim()
        .toLowerCase();
    if (!normalized) {
        return fallback;
    }
    return normalized.startsWith('.') ? normalized : `.${normalized}`;
}

export function resolveExtensionByMimeType(mimeType = '') {
    const normalizedType = String(mimeType || '')
        .trim()
        .toLowerCase();
    if (normalizedType === 'image/png') {
        return '.png';
    }
    return '.jpg';
}

export function formatFileSize(bytes) {
    const safeBytes = Number(bytes) || 0;
    if (safeBytes >= 1024 * 1024) {
        return `${(safeBytes / (1024 * 1024)).toFixed(1)}MB`;
    }
    if (safeBytes >= 1024) {
        return `${Math.round(safeBytes / 1024)}KB`;
    }
    return `${safeBytes}B`;
}

export async function renderEditedImage({
    sourceUrl,
    viewportWidth,
    viewportHeight,
    baseScale,
    zoom,
    offsetX,
    offsetY,
    outputWidth,
    outputHeight,
    mimeType = 'image/jpeg',
    maxBytes = Number.POSITIVE_INFINITY,
    initialQuality = 0.9,
    minQuality = 0.72,
    backgroundColor = '#ffffff',
} = {}) {
    if (!sourceUrl) {
        throw new Error('缺少图片源');
    }

    const safeViewportWidth = Math.max(1, Math.round(Number(viewportWidth) || 0));
    const safeViewportHeight = Math.max(1, Math.round(Number(viewportHeight) || 0));
    const safeOutputWidth = Math.max(1, Math.round(Number(outputWidth) || safeViewportWidth));
    const safeOutputHeight = Math.max(1, Math.round(Number(outputHeight) || safeViewportHeight));
    const safeBaseScale = Math.max(Number(baseScale) || 0, 0.0001);
    const safeZoom = Math.max(Number(zoom) || 1, 1);
    const safeOffsetX = Number(offsetX) || 0;
    const safeOffsetY = Number(offsetY) || 0;
    const safeMimeType = String(mimeType || 'image/jpeg').trim() || 'image/jpeg';

    const image = await loadImageElement(sourceUrl);
    const canvas = document.createElement('canvas');
    canvas.width = safeOutputWidth;
    canvas.height = safeOutputHeight;
    const context = canvas.getContext('2d');
    if (!context) {
        throw new Error('当前浏览器不支持图片处理');
    }

    context.clearRect(0, 0, canvas.width, canvas.height);
    if (safeMimeType !== 'image/png') {
        context.fillStyle = backgroundColor || '#ffffff';
        context.fillRect(0, 0, canvas.width, canvas.height);
    }
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';

    const scaleFactorX = safeOutputWidth / safeViewportWidth;
    const scaleFactorY = safeOutputHeight / safeViewportHeight;
    const totalScale = safeBaseScale * safeZoom;
    const renderedWidth = image.naturalWidth * totalScale * scaleFactorX;
    const renderedHeight = image.naturalHeight * totalScale * scaleFactorY;
    const renderX = (safeOutputWidth - renderedWidth) / 2 + safeOffsetX * scaleFactorX;
    const renderY = (safeOutputHeight - renderedHeight) / 2 + safeOffsetY * scaleFactorY;

    context.drawImage(image, renderX, renderY, renderedWidth, renderedHeight);

    const blob = await exportCanvasBlob(canvas, {
        mimeType: safeMimeType,
        maxBytes,
        initialQuality,
        minQuality,
    });

    return {
        blob,
        mimeType: blob.type || safeMimeType,
        width: safeOutputWidth,
        height: safeOutputHeight,
    };
}

async function loadImageElement(sourceUrl) {
    return new Promise((resolve, reject) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = () => reject(new Error('图片加载失败，请重新选择'));
        image.src = sourceUrl;
    });
}

async function exportCanvasBlob(
    canvas,
    {
        mimeType = 'image/jpeg',
        maxBytes = Number.POSITIVE_INFINITY,
        initialQuality = 0.9,
        minQuality = 0.72,
    } = {}
) {
    const compressible = ['image/jpeg', 'image/webp'].includes(mimeType);
    if (!compressible || !Number.isFinite(maxBytes)) {
        return canvasToBlob(canvas, mimeType, initialQuality);
    }

    let quality = clampNumber(initialQuality, minQuality, 0.95);
    let blob = await canvasToBlob(canvas, mimeType, quality);
    while (blob.size > maxBytes && quality > minQuality) {
        quality = Math.max(minQuality, Number((quality - 0.06).toFixed(2)));
        blob = await canvasToBlob(canvas, mimeType, quality);
        if (quality === minQuality) {
            break;
        }
    }
    return blob;
}

async function canvasToBlob(canvas, mimeType, quality) {
    return new Promise((resolve, reject) => {
        canvas.toBlob(
            blob => {
                if (!blob) {
                    reject(new Error('图片导出失败，请稍后重试'));
                    return;
                }
                resolve(blob);
            },
            mimeType,
            quality
        );
    });
}

function extractFileExtension(fileName) {
    const normalized = String(fileName || '')
        .trim()
        .toLowerCase();
    const dotIndex = normalized.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex === normalized.length - 1) {
        return '';
    }
    return normalized.substring(dotIndex);
}
