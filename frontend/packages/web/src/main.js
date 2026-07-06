import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import './styles/global.css';
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css';
import { ensureBrandingLoaded } from './composables/useBranding';
import { initTheme } from './composables/useTheme';
import { ROUTE_PATHS } from './router/routePaths';
import { setGlobalRequestErrorHandler } from '@lingzhou/core';

setGlobalRequestErrorHandler(({ error, url, status, isLicenseError }) => {
    if (!isLicenseError || status !== 403) {
        return;
    }

    const currentRoute = router.currentRoute.value;
    if (currentRoute?.path === ROUTE_PATHS.license) {
        return;
    }
    if (typeof url === 'string' && url.startsWith('/api/license/')) {
        return;
    }

    router.replace({
        path: ROUTE_PATHS.license,
        query: {
            reason: typeof error?.message === 'string' ? error.message.trim() : '',
            redirect: currentRoute?.fullPath || ROUTE_PATHS.frontChat,
        },
    });
});
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller';

initTheme();
ensureBrandingLoaded();
const app = createApp(App);
app.component('DynamicScroller', DynamicScroller);
app.component('DynamicScrollerItem', DynamicScrollerItem);
app.use(router);
app.mount('#app');
