<template>
    <div class="m-0 flex min-h-screen items-center justify-center bg-slate-900 font-sans">
        <div class="page-container flex overflow-hidden shadow-2xl">
            <div class="tech-bg-illustration grid-pattern"></div>
            <svg
                class="tech-bg-illustration"
                height="100%"
                width="100%"
                xmlns="http://www.w3.org/2000/svg"
            >
                <circle class="node-dot" cx="10%" cy="20%" r="2"></circle>
                <circle class="node-dot" cx="25%" cy="15%" r="3"></circle>
                <circle class="node-dot" cx="15%" cy="40%" r="2"></circle>
                <circle class="node-dot" cx="30%" cy="60%" r="4"></circle>
                <circle class="node-dot" cx="45%" cy="25%" r="2"></circle>
                <circle class="node-dot" cx="5%" cy="80%" r="3"></circle>
                <line class="node-line" x1="10%" x2="25%" y1="20%" y2="15%"></line>
                <line class="node-line" x1="25%" x2="45%" y1="15%" y2="25%"></line>
                <line class="node-line" x1="10%" x2="15%" y1="20%" y2="40%"></line>
                <line class="node-line" x1="15%" x2="30%" y1="40%" y2="60%"></line>
                <line class="node-line" x1="30%" x2="5%" y1="60%" y2="80%"></line>
                <path class="node-line" d="M 60% 0 L 60% 20 L 70% 30 L 100% 30" fill="none"></path>
                <path class="node-line" d="M 80% 100 L 80% 80 L 70% 70 L 0% 70" fill="none"></path>
            </svg>

            <div class="relative z-10 flex w-3/5 flex-col justify-center px-24">
                <div class="max-w-xl">
                    <div class="mb-8 flex items-center gap-4">
                        <div
                            class="flex h-12 w-12 items-center justify-center rounded-lg bg-primary"
                        >
                            <span class="material-symbols-outlined text-3xl text-white">hub</span>
                        </div>
                        <h1 class="text-5xl font-bold tracking-tight text-white">
                            灵洲 AI 平台
                        </h1>
                    </div>
                    <p class="text-xl font-light leading-relaxed text-blue-100/60">
                        高效、稳健的企业级人工智能能力中心
                    </p>

                    <div class="mt-16 flex gap-12">
                        <div class="flex flex-col gap-2">
                            <div class="h-1 w-16 rounded-full bg-primary/40"></div>
                            <div class="h-1 w-10 rounded-full bg-primary/20"></div>
                        </div>
                        <div class="flex flex-col gap-2">
                            <div class="h-1 w-16 rounded-full bg-primary/40"></div>
                            <div class="h-1 w-10 rounded-full bg-primary/20"></div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="z-20 flex w-2/5 items-center justify-center p-12">
                <div class="flex w-full max-w-[380px] flex-col rounded-2xl bg-white p-10 shadow-xl">
                    <div class="mb-10 flex flex-col items-center">
                        <div
                            class="flex h-16 w-16 items-center justify-center rounded-2xl border border-slate-100 bg-slate-50 shadow-sm"
                        >
                            <span class="material-symbols-outlined text-4xl text-primary"
                                >center_focus_strong</span
                            >
                        </div>
                    </div>

                    <form class="space-y-5" @submit.prevent="handleLogin">
                        <div class="space-y-1.5">
                            <label
                                class="ml-1 text-xs font-medium uppercase tracking-wider text-slate-500"
                                >账号</label
                            >
                            <div class="relative">
                                <div
                                    class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-400"
                                >
                                    <span class="material-symbols-outlined text-xl"
                                        >account_circle</span
                                    >
                                </div>
                                <input
                                    v-model.trim="loginForm.code"
                                    type="text"
                                    autocomplete="username"
                                    placeholder="请输入账号"
                                    class="block w-full rounded-lg border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-slate-800 placeholder-transparent transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                                />
                            </div>
                        </div>

                        <div class="space-y-1.5">
                            <label
                                class="ml-1 text-xs font-medium uppercase tracking-wider text-slate-500"
                                >密码</label
                            >
                            <div class="relative">
                                <div
                                    class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-400"
                                >
                                    <span class="material-symbols-outlined text-xl">key</span>
                                </div>
                                <input
                                    v-model="loginForm.password"
                                    type="password"
                                    autocomplete="current-password"
                                    placeholder="请输入密码"
                                    class="block w-full rounded-lg border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-slate-800 placeholder-transparent transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                                />
                            </div>
                        </div>

                        <div class="flex items-center pt-2">
                            <label class="group flex cursor-pointer items-center gap-2">
                                <input
                                    v-model="loginForm.remember"
                                    type="checkbox"
                                    class="h-4 w-4 rounded border-slate-300 bg-white text-primary transition-all focus:ring-primary"
                                />
                                <span
                                    class="text-sm text-slate-500 transition-colors group-hover:text-slate-800"
                                    >记住我</span
                                >
                            </label>
                        </div>

                        <button
                            type="submit"
                            :disabled="loginLoading"
                            class="mt-4 w-full transform rounded-lg bg-primary py-3.5 font-medium text-white shadow-lg shadow-primary/20 transition-all duration-200 hover:bg-primary-hover active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-75"
                        >
                            {{ loginLoading ? '登录中...' : '立即登录' }}
                        </button>

                        <p v-if="loginError" class="text-sm text-red-600">{{ loginError }}</p>
                    </form>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import { setStoredToken } from '@lingzhou/core/auth';
import { requestJson } from '@lingzhou/core/http/request';
import { encryptPassword } from '@/utils/password-encrypt';
import { ROUTE_PATHS } from '../../router/routePaths';

export default {
    name: 'LoginView',
    data() {
        return {
            loginForm: {
                code: '',
                password: '',
                remember: true,
            },
            loginLoading: false,
            loginError: '',
        };
    },
    methods: {
        async handleLogin() {
            if (this.loginLoading) {
                return;
            }
            if (!this.loginForm.code || !this.loginForm.password) {
                this.loginError = '请输入账号和密码';
                return;
            }

            this.loginLoading = true;
            this.loginError = '';
            try {
                const encryptedPassword = await encryptPassword(
                    this.loginForm.password,
                );
                const { data: bizData } = await requestJson('/api/user/getUseStateForLogin', {
                    method: 'POST',
                    auth: false,
                    body: {
                        code: this.loginForm.code,
                        password: encryptedPassword,
                        rememberMe: this.loginForm.remember,
                    },
                });

                const state = bizData.State ?? bizData.state;
                if (state !== 0) {
                    throw new Error(this.mapLoginState(state));
                }

                const accessToken = (bizData.AccessToken || bizData.accessToken || '').trim();
                const refreshToken = (bizData.RefreshToken || bizData.refreshToken || '').trim();
                if (!accessToken) {
                    throw new Error('登录成功但未返回 token');
                }

                setStoredToken(accessToken, refreshToken || accessToken);
                const redirect =
                    typeof this.$route.query.redirect === 'string'
                        ? this.$route.query.redirect
                        : ROUTE_PATHS.frontChat;
                this.$router.replace(redirect || ROUTE_PATHS.frontChat);
            } catch (error) {
                this.loginError = error.message || '登录失败';
            } finally {
                this.loginLoading = false;
            }
        },
        mapLoginState(state) {
            const mapping = {
                1: '账号或密码错误',
                2: '用户已禁用',
                9: '账号不存在',
                10: '账号待激活',
            };
            return mapping[state] || '登录失败';
        },
    },
};
</script>

<style scoped>
.page-container {
    width: 100vw;
    aspect-ratio: 16 / 9;
    max-height: 100vh;
    position: relative;
    overflow: hidden;
    background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%);
}

.tech-bg-illustration {
    position: absolute;
    inset: 0;
    pointer-events: none;
}

.node-dot {
    fill: #3b82f6;
    opacity: 0.4;
}

.node-line {
    stroke: #3b82f6;
    stroke-width: 0.5;
    opacity: 0.2;
}

.grid-pattern {
    background-image: radial-gradient(rgba(59, 130, 246, 0.1) 1px, transparent 1px);
    background-size: 40px 40px;
}
</style>
