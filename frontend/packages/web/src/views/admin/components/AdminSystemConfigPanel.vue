<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import { alert } from '@/composables/useModal';
import { setBranding } from '@/composables/useBranding';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    getBrandingSettings,
    getPlatformSettings,
    getTokenQuotaSettings,
    saveBrandingSettings,
    savePlatformSettings,
    saveTokenQuotaSettings,
    uploadBrandingLogo,
} from '@/api/system-configs';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import { ADMIN_SELECT_BUTTON_CLASS } from '@/views/admin/components/mcp-management/mcpManagementShared';

const router = useRouter();
const props = defineProps({
    mode: {
        type: String,
        default: 'all',
    },
});

const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const lastUpdatedAt = ref('');
const tokenQuotaUpdatedAt = ref('');
const brandingUpdatedAt = ref('');
const brandingUploading = ref(false);
const brandingFileInputRef = ref(null);

const form = reactive({
    status: 1,
    platforms: [],
});

const tokenQuotaForm = reactive({
    status: 0,
    initialGrantTokens: 1000000,
});

const brandingForm = reactive({
    systemName: '灵洲AI平台',
    logoObjectName: '',
    logoUrl: '/logo.png',
    faviconUrl: '/logo.png',
});

const statusOptions = [
    { value: 1, label: '启用' },
    { value: 0, label: '停用' },
];
const platformCount = computed(
    () =>
        form.platforms.filter(item => item.key.trim() || item.name.trim() || item.apiUrl.trim())
            .length
);
const showPlatformSection = computed(() => props.mode !== 'tokenQuota');
const pageTitle = computed(() => (props.mode === 'tokenQuota' ? '用户额度' : '配置管理'));
const pageDescription = computed(() =>
    props.mode === 'tokenQuota'
        ? '集中管理站内登录用户的 Token 限额开关与新用户初始额度。关闭时只保留 usage 统计，不做限制，也不向用户端展示。'
        : '固定页面维护系统配置。当前先提供“低代码平台配置”和“用户 Token 限额”，后续其它配置会继续在这里追加固定区块。'
);

function createEmptyPlatform() {
    return {
        key: '',
        name: '',
        apiUrl: '',
        appKey: '',
        appSecret: '',
        appSecretDirty: false,
        signatureConfigured: false,
    };
}

function normalizePlatformList(platforms) {
    const normalized = Array.isArray(platforms)
        ? platforms.map(item => ({
              key: item?.key || '',
              name: item?.name || '',
              apiUrl: item?.apiUrl || '',
              appKey: item?.authConfig?.appKey || '',
              appSecret: item?.authConfig?.signatureConfigured ? '••••••••••••••••' : '',
              appSecretDirty: false,
              signatureConfigured: Boolean(item?.authConfig?.signatureConfigured),
          }))
        : [];
    return normalized.length ? normalized : [createEmptyPlatform()];
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function fillForm(data) {
    form.status = Number(data?.status) === 0 ? 0 : 1;
    form.platforms = normalizePlatformList(data?.platforms);
    lastUpdatedAt.value = data?.updatedAt || '';
}

function normalizeTokenQuotaGrant(value) {
    const number = Number(value);
    if (!Number.isFinite(number) || number < 0) {
        return 1000000;
    }
    return Math.trunc(number);
}
function fillTokenQuotaForm(data) {
    tokenQuotaForm.status = Number(data?.status) === 1 ? 1 : 0;
    tokenQuotaForm.initialGrantTokens = normalizeTokenQuotaGrant(data?.initialGrantTokens);
    tokenQuotaUpdatedAt.value = data?.updatedAt || '';
}

function fillBrandingForm(data) {
    brandingForm.systemName = data?.systemName || '灵洲AI平台';
    brandingForm.logoObjectName = data?.logoObjectName || '';
    brandingForm.logoUrl = data?.logoUrl || '/logo.png';
    brandingForm.faviconUrl = data?.faviconUrl || brandingForm.logoUrl || '/logo.png';
    brandingUpdatedAt.value = data?.updatedAt || '';
}

async function loadConfig() {
    loading.value = true;
    loadError.value = '';
    try {
        const [platformData, tokenQuotaData, brandingData] = await Promise.all([
            getPlatformSettings(handleUnauthorized),
            getTokenQuotaSettings(handleUnauthorized),
            getBrandingSettings(),
        ]);
        fillForm(platformData);
        fillTokenQuotaForm(tokenQuotaData);
        fillBrandingForm(brandingData);
    } catch (error) {
        loadError.value = error?.message || '配置加载失败';
        fillForm({
            status: 1,
            platforms: [],
        });
        fillTokenQuotaForm({
            status: 0,
            initialGrantTokens: 1000000,
        });
        fillBrandingForm({
            systemName: '灵洲AI平台',
            logoObjectName: '',
            logoUrl: '/logo.png',
            faviconUrl: '/logo.png',
        });
    } finally {
        loading.value = false;
    }
}

function addPlatform() {
    form.platforms.push(createEmptyPlatform());
}

function removePlatform(index) {
    if (form.platforms.length <= 1) {
        form.platforms = [createEmptyPlatform()];
        return;
    }
    form.platforms.splice(index, 1);
}

function handlePlatformSecretFocus(platform) {
    if (!platform) {
        return;
    }
    if (platform.signatureConfigured && !platform.appSecretDirty) {
        platform.appSecret = '';
    }
}

function handlePlatformSecretInput(platform, event) {
    if (!platform) {
        return;
    }
    platform.appSecretDirty = true;
    platform.appSecret = event?.target?.value || '';
}

function openBrandingFileDialog() {
    if (brandingUploading.value || saving.value) {
        return;
    }
    brandingFileInputRef.value?.click();
}

async function handleBrandingFileChange(event) {
    const file = event?.target?.files?.[0];
    event.target.value = '';
    if (!file) {
        return;
    }
    brandingUploading.value = true;
    try {
        const result = await uploadBrandingLogo(file, handleUnauthorized);
        brandingForm.logoObjectName = result?.logoObjectName || '';
        brandingForm.logoUrl = result?.logoUrl || '/logo.png';
        brandingForm.faviconUrl = result?.faviconUrl || brandingForm.logoUrl;
    } catch (error) {
        await alert({
            title: '上传失败',
            message: error?.message || '图标上传失败，请重试',
        });
    } finally {
        brandingUploading.value = false;
    }
}

async function handleSave() {
    if (saving.value) {
        return;
    }
    saving.value = true;
    try {
        const [platformResult, tokenQuotaResult, brandingResult] = await Promise.all([
            savePlatformSettings(
                {
                    status: form.status,
                    platforms: form.platforms.map(item => ({
                        key: item.key,
                        name: item.name,
                        apiUrl: item.apiUrl,
                        authConfig: {
                            appKey: item.appKey,
                            appSecret: item.appSecretDirty ? item.appSecret : '',
                        },
                    })),
                },
                handleUnauthorized
            ),
            saveTokenQuotaSettings(
                {
                    status: tokenQuotaForm.status,
                    initialGrantTokens: normalizeTokenQuotaGrant(tokenQuotaForm.initialGrantTokens),
                },
                handleUnauthorized
            ),
            saveBrandingSettings(
                {
                    systemName: brandingForm.systemName,
                    logoObjectName: brandingForm.logoObjectName,
                },
                handleUnauthorized
            ),
        ]);
        fillForm(platformResult);
        fillTokenQuotaForm(tokenQuotaResult);
        fillBrandingForm(brandingResult);
        setBranding(brandingResult);
        await alert({
            title: '保存成功',
            message: '系统配置已更新。',
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '配置保存失败',
        });
    } finally {
        saving.value = false;
    }
}

onMounted(() => {
    fillForm({
        status: 1,
        platforms: [],
    });
    fillTokenQuotaForm({
        status: 0,
        initialGrantTokens: 1000000,
    });
    fillBrandingForm({
        systemName: '灵洲AI平台',
        logoObjectName: '',
        logoUrl: '/logo.png',
        faviconUrl: '/logo.png',
    });
    loadConfig();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-6 py-4">
            <div class="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <h2 class="text-[28px] font-bold tracking-tight text-slate-900">
                        {{ pageTitle }}
                    </h2>
                    <p class="mt-1 max-w-3xl text-sm leading-6 text-slate-500">
                        {{ pageDescription }}
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="loadConfig"
                    >
                        重新加载
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-primary px-5 py-2 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="loading || saving"
                        @click="handleSave"
                    >
                        {{ saving ? '保存中...' : '保存配置' }}
                    </button>
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-6 pb-8 pt-5">
            <p
                v-if="loadError"
                class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <section class="mb-6 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                <div
                    class="flex flex-col gap-3 border-b border-slate-100 pb-4 lg:flex-row lg:items-start lg:justify-between"
                >
                    <div>
                        <h3 class="text-xl font-bold text-slate-900">系统品牌配置</h3>
                    </div>

                    <div class="text-sm text-slate-500 lg:text-right">
                        <p>最近更新时间：{{ brandingUpdatedAt || '尚未保存' }}</p>
                    </div>
                </div>

                <div v-if="loading" class="mt-5 text-sm text-slate-400">配置加载中...</div>

                <div v-else class="mt-5 grid gap-6 xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
                    <div>
                        <p class="mb-2 text-sm font-semibold text-slate-700">系统图标</p>
                        <input
                            ref="brandingFileInputRef"
                            type="file"
                            class="hidden"
                            accept=".png,.jpg,.jpeg,.webp,.svg,.ico"
                            @change="handleBrandingFileChange"
                        />
                        <button
                            type="button"
                            class="group w-full rounded-2xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white p-4 text-left shadow-sm transition hover:border-primary/40 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="brandingUploading || saving"
                            @click="openBrandingFileDialog"
                        >
                            <div
                                class="mx-auto aspect-square w-28 overflow-hidden rounded-2xl border border-slate-200 bg-white p-3"
                            >
                                <img
                                    :src="brandingForm.logoUrl"
                                    alt="系统图标"
                                    class="h-full w-full rounded-xl object-contain"
                                />
                            </div>
                            <div class="mt-3 text-xs text-slate-500">
                                <p
                                    class="text-sm font-medium text-slate-700 group-hover:text-primary"
                                >
                                    {{
                                        brandingUploading
                                            ? '正在上传，请稍候…'
                                            : '点击图片更换系统图标'
                                    }}
                                </p>
                                <p class="mt-1">
                                    推荐上传 64×64 的正方形图片（PNG/JPG/JPEG/WEBP/SVG/ICO）。
                                </p>
                            </div>
                        </button>
                    </div>

                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-700"
                            >系统名称</span
                        >
                        <input
                            v-model.trim="brandingForm.systemName"
                            type="text"
                            maxlength="32"
                            placeholder="请输入系统名称"
                            class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                        />
                    </label>
                </div>
            </section>

            <section class="mb-6 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                <div
                    class="flex flex-col gap-3 border-b border-slate-100 pb-4 lg:flex-row lg:items-start lg:justify-between"
                >
                    <div>
                        <h3 class="text-xl font-bold text-slate-900">用户 Token 限额</h3>
                        <p class="mt-1 max-w-3xl text-sm leading-6 text-slate-500">
                            关闭时仅保留底层 usage
                            统计，不做额度限制，也不向用户端展示；开启后站内登录用户会执行额度校验，并展示已用量与剩余量。
                        </p>
                    </div>

                    <div class="text-sm text-slate-500 lg:text-right">
                        <p>当前状态：{{ tokenQuotaForm.status === 1 ? '启用' : '关闭' }}</p>
                        <p class="mt-1">最近更新时间：{{ tokenQuotaUpdatedAt || '尚未保存' }}</p>
                    </div>
                </div>

                <div v-if="loading" class="mt-5 text-sm text-slate-400">配置加载中...</div>

                <div v-else class="mt-5 grid gap-5 xl:grid-cols-[minmax(0,280px)_minmax(0,320px)]">
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-700"
                            >开关状态</span
                        >
                        <AppSelect
                            v-model="tokenQuotaForm.status"
                            :options="statusOptions"
                            placeholder="请选择开关状态"
                            :button-class="ADMIN_SELECT_BUTTON_CLASS"
                        />
                    </label>

                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-700">
                            新用户初始额度
                        </span>
                        <input
                            v-model.number="tokenQuotaForm.initialGrantTokens"
                            type="number"
                            min="0"
                            step="1"
                            class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                            placeholder="1000000"
                        />
                        <p class="mt-2 text-xs text-slate-500">
                            仅对后续通过后台新增的站内用户生效，现有用户额度不会因这里修改而自动重算。
                        </p>
                    </label>
                </div>
            </section>

            <section
                v-if="showPlatformSection"
                class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm"
            >
                <div
                    class="flex flex-col gap-3 border-b border-slate-100 pb-4 lg:flex-row lg:items-start lg:justify-between"
                >
                    <div>
                        <h3 class="text-xl font-bold text-slate-900">低代码平台配置</h3>
                        <p class="mt-1 max-w-3xl text-sm leading-6 text-slate-500">
                            平台配置整体存成一个配置项，值是平台数组 JSON。当前维护平台
                            key、平台名称、API URL，以及低代码签名鉴权所需的 AppKey 与密钥。
                        </p>
                    </div>

                    <div class="text-sm text-slate-500 lg:text-right">
                        <p>当前已填写平台数：{{ platformCount }}</p>
                        <p class="mt-1">最近更新时间：{{ lastUpdatedAt || '尚未保存' }}</p>
                    </div>
                </div>

                <div
                    v-if="loading"
                    class="mt-5 rounded-[24px] border border-slate-200 bg-slate-50 px-5 py-8 text-sm text-slate-400"
                >
                    配置加载中...
                </div>

                <div v-else class="mt-5 space-y-6">
                    <div class="block max-w-xs">
                        <span class="mb-2 block text-sm font-semibold text-slate-700">
                            配置状态
                        </span>
                        <AppSelect
                            v-model="form.status"
                            :options="statusOptions"
                            placeholder="请选择配置状态"
                            :button-class="ADMIN_SELECT_BUTTON_CLASS"
                        />
                    </div>

                    <div class="space-y-3">
                        <div class="flex items-center justify-between gap-3">
                            <div>
                                <h4 class="text-base font-semibold text-slate-900">
                                    平台列表配置项
                                </h4>
                                <p class="mt-1 text-sm text-slate-500">
                                    每项包含平台 key、平台名称、API URL、AppKey 和密钥。
                                </p>
                            </div>

                            <button
                                type="button"
                                class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                                @click="addPlatform"
                            >
                                新增平台
                            </button>
                        </div>

                        <article
                            v-for="(platform, index) in form.platforms"
                            :key="index"
                            class="rounded-[24px] border border-slate-200 bg-slate-50 p-4"
                        >
                            <div class="flex flex-col gap-4 lg:flex-row lg:items-start">
                                <label class="block min-w-0 flex-1">
                                    <span class="mb-2 block text-sm font-semibold text-slate-700">
                                        平台 Key
                                    </span>
                                    <input
                                        v-model.trim="platform.key"
                                        type="text"
                                        placeholder="例如：rd-platform"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                                    />
                                </label>

                                <label class="block min-w-0 flex-1">
                                    <span class="mb-2 block text-sm font-semibold text-slate-700">
                                        平台名称
                                    </span>
                                    <input
                                        v-model.trim="platform.name"
                                        type="text"
                                        placeholder="例如：研发工具平台"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                                    />
                                </label>

                                <label class="block min-w-0 flex-[1.4]">
                                    <span class="mb-2 block text-sm font-semibold text-slate-700">
                                        API URL
                                    </span>
                                    <input
                                        v-model.trim="platform.apiUrl"
                                        type="url"
                                        placeholder="https://platform.example.com/api"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                                    />
                                </label>
                            </div>

                            <div
                                class="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]"
                            >
                                <label class="block min-w-0">
                                    <span class="mb-2 block text-sm font-semibold text-slate-700">
                                        AppKey
                                    </span>
                                    <input
                                        v-model.trim="platform.appKey"
                                        type="text"
                                        placeholder="请输入低代码平台 AppKey"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                                    />
                                </label>

                                <label class="block min-w-0">
                                    <span class="mb-2 block text-sm font-semibold text-slate-700">
                                        密钥
                                    </span>
                                    <input
                                        type="password"
                                        :placeholder="
                                            platform.signatureConfigured
                                                ? '已配置，留空则保持原值'
                                                : '请输入低代码平台密钥'
                                        "
                                        :value="platform.appSecret"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-700 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                                        @focus="handlePlatformSecretFocus(platform)"
                                        @input="handlePlatformSecretInput(platform, $event)"
                                    />
                                    <p
                                        v-if="platform.signatureConfigured && !platform.appSecret"
                                        class="mt-2 text-xs text-slate-500"
                                    >
                                        已保存密钥；留空表示继续使用原值。
                                    </p>
                                </label>

                                <div class="flex items-end lg:pt-7">
                                    <button
                                        type="button"
                                        class="rounded-2xl border border-rose-200 bg-white px-4 py-2.5 text-sm font-semibold text-rose-600 transition hover:bg-rose-50"
                                        @click="removePlatform(index)"
                                    >
                                        删除
                                    </button>
                                </div>
                            </div>
                        </article>
                    </div>
                </div>
            </section>
        </div>
    </section>
</template>
