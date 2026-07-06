import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function fetchLoginCaptcha() {
    const { data } = await doRequestJson('/api/user/loginCaptcha', {
        method: 'GET',
        auth: false,
    });
    return data;
}

export async function loginWithPassword(payload) {
    const { data } = await doRequestJson('/api/user/getUseStateForLogin', {
        method: 'POST',
        auth: false,
        body: payload,
    });
    return data;
}
