import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    const apiBaseUrl = (env.VITE_BASE_URL || '').trim();
    const proxyTargetOverride = (env.VITE_PROXY_TARGET || '').trim();
    const appBasePath = (env.VITE_BASE_PATH || '/').trim() || '/';
    const isAbsoluteApiBase = /^https?:\/\//i.test(apiBaseUrl);
    const proxyTarget = proxyTargetOverride || (isAbsoluteApiBase ? new URL(apiBaseUrl).origin : 'http://localhost:5050');

    return {
        base: appBasePath,
        plugins: [vue()],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url)),
            },
        },
        build: {
            outDir: '../../dist',
            emptyOutDir: true,
        },
        server: {
            port: 5173,
            proxy: {
                '/api': {
                    target: proxyTarget,
                    changeOrigin: true,
                },
            },
        },
    };
});
