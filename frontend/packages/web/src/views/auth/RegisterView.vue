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

            <div class="relative z-10 flex w-[54%] flex-col justify-center px-24">
                <div class="max-w-xl">
                    <div class="mb-8 flex items-center gap-4">
                        <img
                            :src="brandingState.logoUrl"
                            :alt="brandingState.systemName"
                            class="h-12 w-12 object-contain"
                        />
                        <h1 class="text-5xl font-bold tracking-tight text-white">
                            {{ brandingState.systemName }}
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

            <div class="z-20 flex w-[46%] items-center justify-center p-12">
                <div
                    class="flex h-[600px] w-full max-w-[450px] flex-col rounded-2xl bg-white p-10 shadow-xl"
                >
                    <div class="mb-5 flex flex-col items-center">
                        <img
                            :src="brandingState.logoUrl"
                            :alt="brandingState.systemName"
                            class="h-16 w-16 object-contain"
                        />
                    </div>

                    <h2 class="mb-1.5 text-center text-xl font-semibold text-slate-800">
                        注册账号申请
                    </h2>
                    <p class="mb-3 text-center text-sm leading-6 text-slate-500">
                        扫码加入企业微信群，联系管理员分配账号后即可登录。
                    </p>
                    <div
                        class="mb-3 px-1 py-0.5 text-center text-base font-extrabold tracking-wide text-red-600"
                    >
                        新用户注册即享 100 万 Token
                    </div>

                    <div class="mb-4 flex justify-center">
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-2.5">
                            <img
                                src="/join-wecom-qr.png"
                                alt="企业微信群二维码"
                                class="h-52 w-52 rounded-lg object-contain"
                            />
                        </div>
                    </div>

                    <p class="mb-5 text-center text-xs leading-5 text-slate-500">
                        若扫码失败，请联系管理员获取入群方式。
                    </p>

                    <button
                        type="button"
                        class="w-full rounded-lg border border-slate-200 bg-white py-3 text-sm font-medium text-slate-700 transition-all hover:border-primary hover:text-primary"
                        @click="goToLogin"
                    >
                        返回登录
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { brandingState, ensureBrandingLoaded } from '@/composables/useBranding';
import { ROUTE_PATHS } from '../../router/routePaths';

const router = useRouter();

onMounted(() => {
    ensureBrandingLoaded();
});

function goToLogin() {
    router.push(ROUTE_PATHS.login);
}
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
