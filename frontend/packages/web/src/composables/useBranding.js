import { reactive } from 'vue';
import { getBrandingSettings } from '@/api/system-configs';

const DEFAULT_BRANDING = Object.freeze({
    systemName: '灵洲AI平台',
    logoUrl: '/logo.png',
    faviconUrl: '/logo.png',
    logoObjectName: '',
});

export const brandingState = reactive({
    ...DEFAULT_BRANDING,
    loaded: false,
});

let loadingPromise = null;

function normalizeBranding(input) {
    const source = input || {};
    const systemName =
        typeof source.systemName === 'string' && source.systemName.trim()
            ? source.systemName.trim()
            : DEFAULT_BRANDING.systemName;
    const logoUrl =
        typeof source.logoUrl === 'string' && source.logoUrl.trim()
            ? source.logoUrl.trim()
            : DEFAULT_BRANDING.logoUrl;
    const faviconUrl =
        typeof source.faviconUrl === 'string' && source.faviconUrl.trim()
            ? source.faviconUrl.trim()
            : logoUrl || DEFAULT_BRANDING.faviconUrl;
    const logoObjectName =
        typeof source.logoObjectName === 'string' ? source.logoObjectName.trim() : '';
    return {
        systemName,
        logoUrl,
        faviconUrl,
        logoObjectName,
    };
}

function findOrCreateIconLink(relValue) {
    let node = document.head.querySelector(`link[rel="${relValue}"]`);
    if (!node) {
        node = document.createElement('link');
        node.setAttribute('rel', relValue);
        document.head.appendChild(node);
    }
    return node;
}

function resolveImageMimeType(url) {
    if (typeof url !== 'string' || !url.trim()) {
        return '';
    }
    const clean = url.trim().split('#')[0].split('?')[0].toLowerCase();
    if (clean.endsWith('.png')) {
        return 'image/png';
    }
    if (clean.endsWith('.jpg') || clean.endsWith('.jpeg')) {
        return 'image/jpeg';
    }
    if (clean.endsWith('.webp')) {
        return 'image/webp';
    }
    if (clean.endsWith('.svg')) {
        return 'image/svg+xml';
    }
    if (clean.endsWith('.ico')) {
        return 'image/x-icon';
    }
    if (clean.endsWith('.gif')) {
        return 'image/gif';
    }
    return '';
}

export function applyBrandingToDocument(branding = brandingState) {
    if (typeof document === 'undefined') {
        return;
    }
    const normalized = normalizeBranding(branding);
    document.title = normalized.systemName;

    const faviconHref = normalized.faviconUrl || normalized.logoUrl || DEFAULT_BRANDING.faviconUrl;
    const faviconType = resolveImageMimeType(faviconHref);
    const iconNode = findOrCreateIconLink('icon');
    iconNode.setAttribute('href', faviconHref);
    if (faviconType) {
        iconNode.setAttribute('type', faviconType);
    } else {
        iconNode.removeAttribute('type');
    }
    const appleNode = findOrCreateIconLink('apple-touch-icon');
    appleNode.setAttribute('href', faviconHref);
    if (faviconType) {
        appleNode.setAttribute('type', faviconType);
    } else {
        appleNode.removeAttribute('type');
    }
}

export function setBranding(value) {
    const normalized = normalizeBranding(value);
    brandingState.systemName = normalized.systemName;
    brandingState.logoUrl = normalized.logoUrl;
    brandingState.faviconUrl = normalized.faviconUrl;
    brandingState.logoObjectName = normalized.logoObjectName;
    brandingState.loaded = true;
    applyBrandingToDocument(brandingState);
}

export async function loadBranding({ force = false } = {}) {
    if (!force && brandingState.loaded) {
        return brandingState;
    }
    if (!force && loadingPromise) {
        return loadingPromise;
    }
    loadingPromise = getBrandingSettings()
        .then(data => {
            setBranding(data || {});
            return brandingState;
        })
        .catch(() => {
            setBranding(DEFAULT_BRANDING);
            return brandingState;
        })
        .finally(() => {
            loadingPromise = null;
        });
    return loadingPromise;
}

export function ensureBrandingLoaded() {
    return loadBranding();
}

export function resetBrandingToDefault() {
    setBranding(DEFAULT_BRANDING);
}
