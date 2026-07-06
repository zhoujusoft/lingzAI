<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { hasAnyAdminPermission } from '@/model/admin-menu-permissions';
import { alert } from '@/composables/useModal';
import { brandingState, ensureBrandingLoaded } from '@/composables/useBranding';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { getLicenseRequest, getLicenseStatus, importLicenseFile } from '@/api/license';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const importing = ref(false);
const loadError = ref('');
const downloadFeedback = ref('');
const statusView = ref(null);
const requestView = ref(null);
const importResult = ref(null);
const fileInputRef = ref(null);

ensureBrandingLoaded();

const redirectTarget = computed(() => {
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect.trim() : '';
    return redirect || ROUTE_PATHS.frontChat;
});

const routeReason = computed(() => {
    const reason = typeof route.query.reason === 'string' ? route.query.reason.trim() : '';
    return reason;
});

const currentProfile = computed(() => currentUserState.profile);
const canImport = computed(() => hasAnyAdminPermission(currentProfile.value));
const statusCode = computed(() =>
    String(statusView.value?.status || 'NOT_IMPORTED')
        .trim()
        .toUpperCase()
);

const statusMeta = computed(() => {
    const mapping = {
        VALID: {
            label: '有效',
            badgeClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
            panelClass: 'border-emerald-200 bg-emerald-50/75',
            hint: '当前授权有效，系统功能可以正常访问。',
        },
        EXPIRING_SOON: {
            label: '即将到期',
            badgeClass: 'border-amber-200 bg-amber-50 text-amber-700',
            panelClass: 'border-amber-200 bg-amber-50/85',
            hint: '当前授权已接近到期时间，建议尽快申请并导入新授权。',
        },
        EXPIRED: {
            label: '已过期',
            badgeClass: 'border-rose-200 bg-rose-50 text-rose-700',
            panelClass: 'border-rose-200 bg-rose-50/85',
            hint: '当前授权已过期，系统功能已被拦截，请由管理员导入新授权。',
        },
        NOT_EFFECTIVE: {
            label: '未生效',
            badgeClass: 'border-blue-200 bg-blue-50 text-blue-700',
            panelClass: 'border-blue-200 bg-blue-50/85',
            hint: '导入的授权尚未到生效时间，请核对授权起始日期。',
        },
        INSTANCE_MISMATCH: {
            label: '实例不匹配',
            badgeClass: 'border-orange-200 bg-orange-50 text-orange-700',
            panelClass: 'border-orange-200 bg-orange-50/85',
            hint: '当前实例码与授权文件不一致，请重新申请对应实例的新授权。',
        },
        PRODUCT_MISMATCH: {
            label: '产品不匹配',
            badgeClass: 'border-orange-200 bg-orange-50 text-orange-700',
            panelClass: 'border-orange-200 bg-orange-50/85',
            hint: '授权文件的产品编码与当前系统不一致，请确认授权文件来源。',
        },
        INVALID_SIGNATURE: {
            label: '签名无效',
            badgeClass: 'border-rose-200 bg-rose-50 text-rose-700',
            panelClass: 'border-rose-200 bg-rose-50/85',
            hint: '授权文件签名校验失败，可能已损坏或被篡改。',
        },
        NOT_IMPORTED: {
            label: '未导入',
            badgeClass: 'border-slate-200 bg-slate-100 text-slate-700',
            panelClass: 'border-slate-200 bg-slate-100/85',
            hint: '系统尚未导入授权文件，请先申请授权并由管理员导入。',
        },
    };
    return mapping[statusCode.value] || mapping.NOT_IMPORTED;
});

const summaryReason = computed(() => routeReason.value || statusMeta.value.hint);

const requestPayloadText = computed(() =>
    JSON.stringify(
        {
            productCode: requestView.value?.productCode || '',
            instanceCode: requestView.value?.instanceCode || '',
            customerName: requestView.value?.customerName || '',
            currentLicenseId: requestView.value?.currentLicenseId || '',
            currentLicType: requestView.value?.currentLicType || '',
            currentExpiresAt: requestView.value?.currentExpiresAt || '',
        },
        null,
        2
    )
);

const featureFlags = computed(() =>
    Array.isArray(statusView.value?.featureFlags)
        ? statusView.value.featureFlags.filter(Boolean)
        : []
);

const currentLicTypeLabel = computed(() => formatLicType(statusView.value?.licType));

function formatValue(value, fallback = '-') {
    if (value == null) {
        return fallback;
    }
    const normalized = String(value).trim();
    return normalized ? normalized : fallback;
}

function formatNumber(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '-';
    }
    return number.toLocaleString('zh-CN');
}

function formatLicType(value) {
    const normalized = String(value ?? '')
        .trim()
        .toUpperCase();
    if (
        normalized === '1' ||
        normalized === 'STANDARD' ||
        normalized === 'FORMAL' ||
        normalized === 'OFFICIAL' ||
        normalized === '正式版' ||
        normalized === '正式'
    ) {
        return '正式版';
    }
    if (
        normalized === '0' ||
        normalized === 'TRIAL' ||
        normalized === '试用版' ||
        normalized === '试用'
    ) {
        return '试用版';
    }
    return '-';
}

function isFormalLicenseType(value) {
    return formatLicType(value) === '正式版';
}

function formatUserLimit(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '-';
    }
    if (number < 0) {
        return '不限';
    }
    return number.toLocaleString('zh-CN');
}

function handleUnauthorized() {
    clearUserSession();
    router.replace({
        path: ROUTE_PATHS.login,
        query: {
            redirect: ROUTE_PATHS.license,
        },
    });
}

async function loadLicenseData() {
    loading.value = true;
    loadError.value = '';
    try {
        const [statusData, requestData] = await Promise.all([
            getLicenseStatus(handleUnauthorized),
            getLicenseRequest(handleUnauthorized),
        ]);
        statusView.value = statusData || null;
        requestView.value = requestData || null;
    } catch (error) {
        loadError.value = error?.message || '授权信息加载失败';
    } finally {
        loading.value = false;
    }
}

function openImportDialog() {
    if (importing.value) {
        return;
    }
    if (!canImport.value) {
        alert({
            title: '无导入权限',
            message: '当前账号没有导入授权文件的权限，请联系系统管理员处理。',
        });
        return;
    }
    fileInputRef.value?.click();
}

async function handleImportFileChange(event) {
    const file = event?.target?.files?.[0];
    event.target.value = '';
    if (!file) {
        return;
    }

    importing.value = true;
    try {
        const result = await importLicenseFile(file, handleUnauthorized);
        importResult.value = result || null;
        await loadLicenseData();
        await alert({
            title: '导入成功',
            message: `授权 ${formatValue(result?.licenseId)} 已成功导入，现在可以返回系统继续使用。`,
        });
    } catch (error) {
        await alert({
            title: '导入失败',
            message: error?.message || '授权文件导入失败，请检查文件内容后重试。',
        });
    } finally {
        importing.value = false;
    }
}

function buildRequestFileName() {
    const instanceCode = formatValue(requestView.value?.instanceCode, 'request').replace(
        /[^a-zA-Z0-9_-]/g,
        '_'
    );
    return `license-request-${instanceCode}.txt`;
}

async function downloadRequestPayload() {
    try {
        const blob = new Blob([requestPayloadText.value], {
            type: 'text/plain;charset=utf-8',
        });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = buildRequestFileName();
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
        downloadFeedback.value = '申请文件已生成并开始下载';
        window.setTimeout(() => {
            downloadFeedback.value = '';
        }, 2500);
    } catch (error) {
        await alert({
            title: '生成文件失败',
            message: '当前环境无法生成申请文件，请稍后重试。',
        });
    }
}

function backToSystem() {
    router.replace(redirectTarget.value);
}

onMounted(() => {
    loadLicenseData();
});
</script>

<template>
    <div
        class="h-screen overflow-y-auto bg-[radial-gradient(circle_at_top,#e0f2fe_0%,#f8fafc_38%,#e2e8f0_100%)] text-slate-900"
    >
        <div class="mx-auto flex min-h-full w-full max-w-7xl flex-col px-4 py-8 sm:px-6 lg:px-8">
            <header
                class="overflow-hidden rounded-[32px] border border-white/70 bg-white/80 shadow-[0_24px_80px_rgba(15,23,42,0.10)] backdrop-blur"
            >
                <div
                    class="grid gap-8 px-6 py-8 lg:grid-cols-[minmax(0,1.3fr)_minmax(320px,0.9fr)] lg:px-10"
                >
                    <div>
                        <div class="flex items-center gap-4">
                            <div
                                class="flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-900 shadow-lg shadow-slate-900/15"
                            >
                                <img
                                    :src="brandingState.logoUrl"
                                    :alt="brandingState.systemName"
                                    class="h-10 w-10 object-contain"
                                />
                            </div>
                            <div>
                                <h1
                                    class="text-3xl font-black tracking-tight text-slate-950 sm:text-4xl"
                                >
                                    {{ brandingState.systemName }} 授权管理
                                </h1>
                            </div>
                        </div>

                        <p class="mt-6 max-w-3xl text-sm leading-7 text-slate-600 sm:text-base">
                            当系统因为授权过期、实例不匹配或授权额度耗尽而拒绝访问时，前端会自动跳转到此页面。
                            你可以在这里查看当前授权状态、下载续期申请文件，并由管理员导入新的授权文件。
                        </p>

                        <div
                            class="mt-6 rounded-3xl border px-5 py-4 text-sm leading-6"
                            :class="statusMeta.panelClass"
                        >
                            <div class="flex flex-wrap items-center gap-3">
                                <span
                                    class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold"
                                    :class="statusMeta.badgeClass"
                                >
                                    {{ statusMeta.label }}
                                </span>
                                <span class="text-slate-500">授权类型：{{ currentLicTypeLabel }}</span>
                            </div>
                            <p class="mt-3 text-slate-700">{{ summaryReason }}</p>
                        </div>

                        <div class="mt-6 flex flex-wrap gap-3">
                            <button
                                type="button"
                                class="rounded-2xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-700"
                                @click="loadLicenseData"
                            >
                                {{ loading ? '刷新中...' : '刷新状态' }}
                            </button>
                            <button
                                type="button"
                                class="rounded-2xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:border-blue-200 hover:bg-blue-50"
                                @click="backToSystem"
                            >
                                返回系统
                            </button>
                        </div>
                    </div>

                    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                        <div
                            class="rounded-3xl border border-slate-200 bg-slate-950 px-5 py-5 text-white"
                        >
                            <p class="text-xs uppercase tracking-[0.28em] text-slate-400">
                                激活用户数
                            </p>
                            <p class="mt-3 text-3xl font-black">
                                {{ formatNumber(statusView?.activeUsers) }}
                                <span class="text-base font-medium text-slate-400">
                                    / {{ formatUserLimit(statusView?.maxActiveUsers) }}
                                </span>
                            </p>
                            <p class="mt-2 text-sm text-slate-400">
                                当前已激活用户数与授权人数上限。
                            </p>
                        </div>

                        <div class="rounded-3xl border border-slate-200 bg-white px-5 py-5">
                            <p class="text-xs uppercase tracking-[0.28em] text-slate-400">
                                授权 Token 余额
                            </p>
                            <p class="mt-3 text-3xl font-black text-slate-900">
                                {{ formatNumber(statusView?.remainingTokens) }}
                            </p>
                            <p class="mt-2 text-sm text-slate-500">
                                已消耗 {{ formatNumber(statusView?.consumedTokens) }}，总额度
                                {{ formatNumber(statusView?.maxTotalTokens) }}。
                            </p>
                        </div>
                    </div>
                </div>
            </header>

            <p
                v-if="loadError"
                class="mt-6 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div class="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.24fr)_minmax(300px,0.72fr)] lg:items-stretch">
                <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-[0.28em] text-slate-400">
                            授权状态
                        </p>
                        <h2 class="mt-2 text-2xl font-black tracking-tight text-slate-950">
                            当前授权详情
                        </h2>
                    </div>

                    <div class="mt-6 grid gap-4 md:grid-cols-2">
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">客户名称</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ formatValue(statusView?.customerName) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">产品编码</p>
                            <p class="mt-2 break-all font-mono text-sm text-slate-900">
                                {{ formatValue(statusView?.productCode) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">实例码</p>
                            <p class="mt-2 break-all font-mono text-sm text-slate-900">
                                {{ formatValue(statusView?.instanceCode) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">授权编号</p>
                            <p class="mt-2 break-all font-mono text-sm text-slate-900">
                                {{ formatValue(statusView?.licenseId) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">修订版本</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ formatValue(statusView?.revision) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">授权类型</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ formatLicType(statusView?.licType) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">版本类型</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ formatValue(statusView?.edition) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">生效时间</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ formatValue(statusView?.effectiveAt) }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">到期时间</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{
                                    isFormalLicenseType(statusView?.licType)
                                        ? '无期限'
                                        : formatValue(statusView?.expiresAt)
                                }}
                            </p>
                        </div>
                        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <p class="text-xs uppercase tracking-[0.24em] text-slate-400">导入时间</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ formatValue(statusView?.importedAt) }}
                            </p>
                        </div>
                    </div>

                    <div
                        class="mt-6 rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-4"
                    >
                        <p class="text-xs uppercase tracking-[0.24em] text-slate-400">功能标记</p>
                        <div v-if="featureFlags.length" class="mt-3 flex flex-wrap gap-2">
                            <span
                                v-for="flag in featureFlags"
                                :key="flag"
                                class="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white"
                            >
                                {{ flag }}
                            </span>
                        </div>
                        <p v-else class="mt-3 text-sm text-slate-500">
                            当前授权没有额外功能标记。
                        </p>
                    </div>
                </section>

                <div class="flex h-full flex-col gap-5">
                    <section class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <p
                                    class="text-xs font-semibold uppercase tracking-[0.28em] text-slate-400"
                                >
                                    续期申请
                                </p>
                                <h2 class="mt-2 text-2xl font-black tracking-tight text-slate-950">
                                    授权申请信息
                                </h2>
                            </div>
                            <button
                                type="button"
                                class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-sky-200 hover:bg-sky-50"
                                @click="downloadRequestPayload"
                            >
                                下载申请文件
                            </button>
                        </div>

                        <p class="mt-3 text-sm leading-6 text-slate-500">
                            点击右上角按钮会下载一个 `.txt` 文件，文件内容就是下方 JSON，可直接发给授权方作为申请信息使用。
                        </p>

                        <p v-if="downloadFeedback" class="mt-3 text-sm font-medium text-emerald-600">
                            {{ downloadFeedback }}
                        </p>

                        <textarea
                            :value="requestPayloadText"
                            readonly
                            class="mt-3 h-[152px] w-full rounded-3xl border border-slate-200 bg-slate-950/95 px-4 py-4 font-mono text-sm leading-6 text-slate-100 outline-none"
                        />
                    </section>

                    <section class="flex min-h-0 flex-1 flex-col rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <p
                                    class="text-xs font-semibold uppercase tracking-[0.28em] text-slate-400"
                                >
                                    导入授权
                                </p>
                                <h2 class="mt-2 text-2xl font-black tracking-tight text-slate-950">
                                    导入新的授权文件
                                </h2>
                            </div>
                            <span
                                v-if="canImport"
                                class="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700"
                            >
                                当前账号可导入
                            </span>
                            <span
                                v-else
                                class="rounded-full border border-slate-200 bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600"
                            >
                                当前账号通常没有导入权限
                            </span>
                        </div>

                        <p class="mt-3 text-sm leading-6 text-slate-500">
                            导入成功后，本页面会自动刷新最新授权状态。建议导入完成后点击“返回系统”继续原有操作。
                        </p>

                        <input
                            ref="fileInputRef"
                            type="file"
                            class="hidden"
                            accept=".json,.lic,.txt"
                            @change="handleImportFileChange"
                        />

                        <button
                            type="button"
                            class="mt-4 flex-1 rounded-3xl border border-dashed border-blue-300 bg-blue-50 px-5 py-7 text-center transition hover:border-blue-400 hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="importing || !canImport"
                            @click="openImportDialog"
                        >
                            <span class="material-symbols-outlined text-4xl text-blue-600">
                                upload_file
                            </span>
                            <p class="mt-3 text-base font-semibold text-blue-900">
                                {{
                                    importing
                                        ? '正在导入，请稍候...'
                                        : canImport
                                          ? '选择授权文件并导入'
                                          : '当前账号无导入权限'
                                }}
                            </p>
                            <p class="mt-1 text-sm text-blue-700">
                                {{
                                    canImport
                                        ? '支持 JSON、LIC、TXT 等授权文件格式，后端会自动完成签名与实例校验。'
                                        : '你仍可下载申请文件并发送给授权方，再由管理员在此页面完成导入。'
                                }}
                            </p>
                        </button>

                        <div
                            v-if="importResult"
                            class="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-4 text-sm text-emerald-700"
                        >
                            最新导入结果：授权编号 {{ formatValue(importResult.licenseId) }}，修订版本
                            {{ formatValue(importResult.revision) }}，授权类型
                            {{ formatLicType(importResult.licType) }}。
                        </div>
                    </section>
                </div>
            </div>
        </div>
    </div>
</template>
