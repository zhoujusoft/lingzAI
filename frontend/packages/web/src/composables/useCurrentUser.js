import { clearStoredToken, isAuthenticated } from '@lingzhou/core/auth';
import { requestJson as doRequestJson } from '@lingzhou/core/http/request';
import { reactive } from 'vue';
import { UserBean } from '@/model/bean';

export const currentUserState = reactive({
    profile: null,
    name: '',
    loading: false,
    initialized: false,
});

let fetchProfilePromise = null;

function normalizeUserName(profile) {
    if (!profile || typeof profile !== 'object') {
        return '';
    }
    return profile.name || profile.code || '';
}

export function hasLoginSession() {
    return isAuthenticated();
}

export function resetCurrentUser() {
    currentUserState.profile = null;
    currentUserState.name = '';
    currentUserState.loading = false;
    currentUserState.initialized = false;
}

export function clearUserSession() {
    clearStoredToken();
    resetCurrentUser();
}

export async function fetchCurrentUser(options = {}) {
    if (fetchProfilePromise) {
        return fetchProfilePromise;
    }

    const { onUnauthorized } = options;
    currentUserState.loading = true;

    fetchProfilePromise = (async () => {
        try {
            const { data: profile } = await doRequestJson('/api/user/info', {
                onUnauthorized,
            });
            const profileBean = profile ? UserBean.fromApi(profile) : null;
            currentUserState.profile = profileBean;
            currentUserState.name = normalizeUserName(profileBean);
            currentUserState.initialized = true;
            return profileBean;
        } finally {
            currentUserState.loading = false;
            fetchProfilePromise = null;
        }
    })();

    return fetchProfilePromise;
}

export async function ensureCurrentUserLoaded(options = {}) {
    const { force = false, onUnauthorized } = options;

    if (!force && currentUserState.initialized) {
        return currentUserState.profile;
    }

    return fetchCurrentUser({ onUnauthorized });
}

export async function logoutCurrentUser(options = {}) {
    const { onUnauthorized } = options;
    try {
        await doRequestJson('/api/user/logout', {
            method: 'POST',
            onUnauthorized,
        });
    } catch (error) {
        // logout request failure should not block local cleanup
    } finally {
        clearUserSession();
    }
}
