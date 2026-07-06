import { computed, reactive } from 'vue';

const THEME_STORAGE_KEY = 'lingzhou-web-theme';
const THEME_MODES = Object.freeze(['light', 'dark', 'system']);

// 深色模式开关 - 设为 false 可禁用深色模式，强制使用亮色
const DARK_MODE_ENABLED = false;

export const themeState = reactive({
    mode: 'light', // 强制使用 light
    resolvedTheme: 'light',
    initialized: false,
});

let themeMediaQuery = null;
let isListening = false;

function isValidThemeMode(value) {
    return THEME_MODES.includes(value);
}

function resolveStoredThemeMode() {
    // 深色模式禁用时，始终返回 light
    if (!DARK_MODE_ENABLED) {
        return 'light';
    }
    if (typeof window === 'undefined') {
        return 'light';
    }
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isValidThemeMode(stored) ? stored : 'light';
}

function resolveSystemTheme() {
    // 深色模式禁用时，始终返回 light
    if (!DARK_MODE_ENABLED) {
        return 'light';
    }
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
        return 'light';
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function resolveThemeMode(mode) {
    return mode === 'system' ? resolveSystemTheme() : mode;
}

function applyThemeToDocument(mode) {
    const resolvedTheme = resolveThemeMode(mode);
    themeState.resolvedTheme = resolvedTheme;
    themeState.initialized = true;

    if (typeof document === 'undefined') {
        return resolvedTheme;
    }

    document.documentElement.dataset.theme = resolvedTheme;
    document.documentElement.style.colorScheme = resolvedTheme;
    return resolvedTheme;
}

function handleSystemThemeChange() {
    if (themeState.mode !== 'system') {
        return;
    }
    applyThemeToDocument(themeState.mode);
}

function ensureThemeListener() {
    if (isListening || typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
        return;
    }

    themeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    if (typeof themeMediaQuery.addEventListener === 'function') {
        themeMediaQuery.addEventListener('change', handleSystemThemeChange);
    } else if (typeof themeMediaQuery.addListener === 'function') {
        themeMediaQuery.addListener(handleSystemThemeChange);
    }
    isListening = true;
}

export function initTheme() {
    themeState.mode = resolveStoredThemeMode();
    ensureThemeListener();
    return applyThemeToDocument(themeState.mode);
}

export function setTheme(mode) {
    const nextMode = isValidThemeMode(mode) ? mode : 'system';
    themeState.mode = nextMode;

    if (typeof window !== 'undefined') {
        window.localStorage.setItem(THEME_STORAGE_KEY, nextMode);
    }

    return applyThemeToDocument(nextMode);
}

export function useTheme() {
    const resolvedTheme = computed(() => themeState.resolvedTheme);
    const isDark = computed(() => themeState.resolvedTheme === 'dark');

    return {
        themeState,
        themeModes: THEME_MODES,
        resolvedTheme,
        isDark,
        setTheme,
    };
}
