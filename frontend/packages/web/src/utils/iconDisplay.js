export function isImageSource(value) {
    if (typeof value !== 'string') {
        return false;
    }
    const normalized = value.trim().toLowerCase();
    return (
        normalized.startsWith('/') ||
        normalized.startsWith('http://') ||
        normalized.startsWith('https://') ||
        normalized.startsWith('data:') ||
        normalized.startsWith('blob:')
    );
}

export function isMaterialSymbolName(value) {
    if (typeof value !== 'string') {
        return false;
    }
    const normalized = value.trim();
    if (!normalized || isImageSource(normalized)) {
        return false;
    }
    return /^[a-z0-9_]+$/i.test(normalized);
}
