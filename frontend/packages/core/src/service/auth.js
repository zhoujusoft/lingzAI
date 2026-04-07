import { AUTH_STORAGE_KEYS } from '../model/constants';

let inMemoryAccessToken = '';

export function getAuthStorageKeys() {
    return AUTH_STORAGE_KEYS;
}

function safeGet(storage, key) {
    return (storage.getItem(key) || '').trim();
}

function setStoredRefreshToken(refreshToken) {
    const value = (refreshToken || '').trim();
    if (!value) {
        return;
    }
    window.localStorage.setItem(AUTH_STORAGE_KEYS.refreshToken, value);
}

export function getStoredAccessToken() {
    return inMemoryAccessToken;
}

export function getStoredRefreshToken() {
    return safeGet(window.localStorage, AUTH_STORAGE_KEYS.refreshToken);
}

// Keep compatibility for existing callers.
export function getStoredToken() {
    return getStoredAccessToken();
}

export function setStoredTokens(accessToken, refreshToken) {
    const access = (accessToken || '').trim();
    const refresh = (refreshToken || '').trim();
    if (!access) {
        return;
    }

    const refreshValue = refresh || access;
    clearStoredToken();
    inMemoryAccessToken = access;
    setStoredRefreshToken(refreshValue);
}

export function setStoredToken(token, refreshToken = '') {
    setStoredTokens(token, refreshToken || token);
}

export function updateStoredAccessToken(accessToken, refreshToken = '') {
    const access = (accessToken || '').trim();
    if (!access) {
        return;
    }
    inMemoryAccessToken = access;
    const nextRefreshToken = (refreshToken || '').trim();
    if (nextRefreshToken) {
        setStoredRefreshToken(nextRefreshToken);
    }
}

export function clearStoredToken() {
    inMemoryAccessToken = '';
    window.localStorage.removeItem(AUTH_STORAGE_KEYS.refreshToken);
}

export function isAuthenticated() {
    return !!(getStoredAccessToken() || getStoredRefreshToken());
}
