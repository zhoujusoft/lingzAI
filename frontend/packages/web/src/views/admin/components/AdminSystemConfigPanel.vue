<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { getPlatformSettings, savePlatformSettings } from '@/api/system-configs';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import { ADMIN_SELECT_BUTTON_CLASS } from '@/views/admin/components/mcp-management/mcpManagementShared';

const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const lastUpdatedAt = ref('');

const form = reactive({
    status: 1,
    platforms: [],
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

async function loadConfig() {
    loading.value = true;
    loadError.value = '';
    try {
        const data = await getPlatformSettings(handleUnauthorized);
        fillForm(data);
    } catch (error) {
        loadError.value = error?.message || '配置加载失败';
        fillForm({
            status: 1,
            platforms: [],
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

async function handleSave() {
    if (saving.value) {
        return;
    }
    saving.value = true;
    try {
        const result = await savePlatformSettings(
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
        );
        fillForm(result);
        await alert({
            title: '保存成功',
            message: '低代码平台配置已更新。',
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
    loadConfig();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-6 py-5">
            <div class="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.32em] text-slate-400">
                        Config Management
                    </p>
                    <h2 class="mt-2 text-[28px] font-bold tracking-tight text-slate-900">
                        配置管理
                    </h2>
                    <p class="mt-1 max-w-3xl text-sm leading-6 text-slate-500">
                        固定页面维护系统配置。当前先提供“低代码平台配置”，后续其它配置会继续在这里追加固定区块。
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

            <section class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                <div
                    class="flex flex-col gap-3 border-b border-slate-100 pb-4 lg:flex-row lg:items-start lg:justify-between"
                >
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-[0.28em] text-slate-400">
                            Low-code Platforms
                        </p>
                        <h3 class="mt-2 text-xl font-bold text-slate-900">低代码平台配置</h3>
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

                            <div class="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
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
