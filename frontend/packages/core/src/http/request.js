import axios from 'axios';
import {
    clearStoredToken,
    getStoredAccessToken,
    getStoredRefreshToken,
    updateStoredAccessToken,
} from '../service/auth';
import { AUTH_ENDPOINTS, AUTH_HEADERS } from '../model/constants';
import {
    HTTP_STATUS_OK_MAX,
    HTTP_STATUS_OK_MIN,
    HTTP_STATUS_UNAUTHORIZED,
} from '../model/http-status';

const API_BASE_URL = (import.meta.env?.VITE_BASE_URL || '').trim().replace(/\/+$/, '');

export class RequestError extends Error {
    constructor(message, options = {}) {
        super(message || '请求失败');
        this.name = 'RequestError';
        this.status = options.status || 0;
        this.code = options.code ?? null;
        this.payload = options.payload;
    }
}

function parseApiCode(payload) {
    if (!payload || typeof payload !== 'object') {
        return 0;
    }
    const value = payload.code;
    if (typeof value === 'number') {
        return value;
    }
    if (typeof value === 'string' && value.trim()) {
        const parsed = Number(value);
        return Number.isNaN(parsed) ? 0 : parsed;
    }
    return 0;
}

function parseApiMessage(payload, fallback = '请求失败') {
    if (!payload || typeof payload !== 'object') {
        return fallback;
    }
    const msg = payload.message || payload.Message;
    if (typeof msg === 'string' && msg.trim()) {
        return msg.trim();
    }
    if (payload.data && typeof payload.data === 'object') {
        const nested = payload.data.error || payload.data.message;
        if (typeof nested === 'string' && nested.trim()) {
            return nested.trim();
        }
    }
    if (typeof payload.error === 'string' && payload.error.trim()) {
        return payload.error.trim();
    }
    return fallback;
}

function fallbackMessageByStatus(status) {
    if (status === 400) {
        return '请求参数不正确';
    }
    if (status === 401) {
        return '登录已过期或无权限，请重新登录';
    }
    if (status === 403) {
        return '当前无权限执行该操作';
    }
    if (status === 404) {
        return '请求的资源不存在';
    }
    if (status === 429) {
        return '请求过于频繁，请稍后重试';
    }
    if (status >= 500) {
        return '服务暂时不可用，请稍后重试';
    }
    return '请求失败';
}

function normalizeBody(body, headers) {
    if (body == null) {
        return body;
    }
    if (
        body instanceof FormData ||
        body instanceof URLSearchParams ||
        body instanceof Blob ||
        body instanceof ArrayBuffer
    ) {
        return body;
    }
    if (typeof body === 'string') {
        return body;
    }
    if (!headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }
    return JSON.stringify(body);
}

async function parseJsonSafe(response) {
    const payload = response.data;
    if (payload == null || payload === '') {
        return {};
    }
    if (typeof payload === 'object') {
        return payload;
    }
    if (typeof payload !== 'string') {
        return {};
    }
    try {
        return JSON.parse(payload);
    } catch (error) {
        return {};
    }
}

function headersToObject(headers) {
    return Object.fromEntries(headers.entries());
}

function extractTokenPair(payload) {
    if (!payload || typeof payload !== 'object') {
        return { accessToken: '', refreshToken: '' };
    }
    const accessToken = (payload.AccessToken || payload.accessToken || '').trim();
    const refreshToken = (payload.RefreshToken || payload.refreshToken || '').trim();
    return {
        accessToken,
        refreshToken: refreshToken || accessToken,
    };
}

let refreshPromise = null;

async function refreshAccessToken() {
    if (refreshPromise) {
        return refreshPromise;
    }

    refreshPromise = (async () => {
        const refreshToken = getStoredRefreshToken();
        if (!refreshToken) {
            throw new RequestError('登录已过期', {
                status: HTTP_STATUS_UNAUTHORIZED,
                code: HTTP_STATUS_UNAUTHORIZED,
            });
        }

        const response = await request(AUTH_ENDPOINTS.refreshToken, {
            method: 'POST',
            auth: false,
            skipRefresh: true,
            headers: {
                [AUTH_HEADERS.refreshToken]: `Bearer ${refreshToken}`,
            },
        });

        const payload = await parseJsonSafe(response);
        const code = parseApiCode(payload);
        const ok = response.status >= HTTP_STATUS_OK_MIN && response.status < HTTP_STATUS_OK_MAX;
        if (!ok || code !== 0) {
            throw new RequestError(parseApiMessage(payload, '登录已过期'), {
                status: response.status,
                code,
                payload,
            });
        }

        const tokenPayload = payload && typeof payload === 'object' ? payload.data : payload;
        const { accessToken, refreshToken: nextRefreshToken } = extractTokenPair(tokenPayload);
        if (!accessToken) {
            throw new RequestError('刷新 token 失败', {
                status: HTTP_STATUS_UNAUTHORIZED,
                code: HTTP_STATUS_UNAUTHORIZED,
                payload,
            });
        }

        updateStoredAccessToken(accessToken, nextRefreshToken);
        return accessToken;
    })();

    try {
        await refreshPromise;
    } finally {
        refreshPromise = null;
    }
}

async function request(url, options = {}) {
    const {
        method = 'GET',
        headers = {},
        body,
        auth = true,
        onUnauthorized,
        responseType,
        skipRefresh = false,
        retry = false,
    } = options;

    const mergedHeaders = new Headers(headers);
    if (auth) {
        const accessToken = getStoredAccessToken();
        const refreshToken = getStoredRefreshToken();
        if (accessToken && !mergedHeaders.has(AUTH_HEADERS.authorization)) {
            mergedHeaders.set(AUTH_HEADERS.authorization, `Bearer ${accessToken}`);
        }
        if (refreshToken && !mergedHeaders.has(AUTH_HEADERS.refreshToken)) {
            mergedHeaders.set(AUTH_HEADERS.refreshToken, `Bearer ${refreshToken}`);
        }
    }

    const response = await axios({
        baseURL: API_BASE_URL || undefined,
        url,
        method,
        data: normalizeBody(body, mergedHeaders),
        headers: headersToObject(mergedHeaders),
        responseType,
        adapter: 'fetch',
        validateStatus: () => true,
    });

    if (response.status === HTTP_STATUS_UNAUTHORIZED && auth && !skipRefresh && !retry) {
        try {
            await refreshAccessToken();
            return request(url, {
                ...options,
                retry: true,
            });
        } catch (error) {
            clearStoredToken();
            if (typeof onUnauthorized === 'function') {
                onUnauthorized();
            }
        }
    }

    if (response.status === HTTP_STATUS_UNAUTHORIZED && typeof onUnauthorized === 'function') {
        onUnauthorized();
    }
    return response;
}

export async function requestRaw(url, options = {}) {
    return request(url, options);
}

export async function requestJson(url, options = {}) {
    const response = await request(url, options);
    const payload = await parseJsonSafe(response);
    const code = parseApiCode(payload);
    const data =
        payload && typeof payload === 'object' && payload.data !== undefined
            ? payload.data
            : payload;
    const ok = response.status >= HTTP_STATUS_OK_MIN && response.status < HTTP_STATUS_OK_MAX;

    if (!ok || code !== 0) {
        throw new RequestError(parseApiMessage(payload, fallbackMessageByStatus(response.status)), {
            status: response.status,
            code,
            payload,
        });
    }

    return {
        response,
        payload,
        data,
    };
}
