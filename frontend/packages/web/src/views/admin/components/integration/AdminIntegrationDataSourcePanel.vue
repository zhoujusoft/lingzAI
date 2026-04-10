<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import BaseModal from '@/components/feedback/BaseModal.vue';
import { clearUserSession } from '@/composables/useCurrentUser';
import { confirm } from '@/composables/useModal';
import {
    createIntegrationDataSource,
    deleteIntegrationDataSource,
    listIntegrationDataSources,
    testIntegrationDataSource,
    updateIntegrationDataSource,
} from '@/api/integration';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();

const loading = ref(false);
const testing = ref(false);
const modalOpen = ref(false);
const loadError = ref('');
const saveError = ref('');
const testMessage = ref('');
const testStatus = ref('');
const editingId = ref(null);
const connectionParamsOpen = ref(false);

const filters = reactive({
    keyword: '',
    dbType: '',
    status: '',
});

const form = reactive({
    name: '',
    alias: '',
    dbType: 'MYSQL',
    host: '',
    port: '',
    databaseName: '',
    connectionParams: '',
    authType: 'USERNAME_PASSWORD',
    username: '',
    password: '',
    passwordDirty: false,
    passwordConfigured: false,
    status: 'ACTIVE',
});

const dataSources = ref([]);

const dbTypeOptions = [
    { value: '', label: '全部类型' },
    { value: 'MYSQL', label: 'MySQL' },
    { value: 'POSTGRESQL', label: 'PostgreSQL' },
    { value: 'SQLSERVER', label: 'SQL Server' },
    { value: 'ORACLE', label: 'Oracle' },
];

const statusOptions = [
    { value: '', label: '全部状态' },
    { value: 'ACTIVE', label: '运行中' },
    { value: 'DRAFT', label: '草稿' },
];

const pageTitle = computed(() => (editingId.value ? '编辑数据库' : '创建数据库'));

function connectionDefaults(dbType) {
    const normalized = String(dbType || 'MYSQL').trim().toUpperCase();
    if (normalized === 'POSTGRESQL') {
        return {
            port: '5432',
            connectionParams: 'sslmode=disable',
        };
    }
    if (normalized === 'SQLSERVER') {
        return {
            port: '1433',
            connectionParams: 'encrypt=false;trustServerCertificate=true',
        };
    }
    if (normalized === 'ORACLE') {
        return {
            port: '1521',
            connectionParams: '',
        };
    }
    return {
        port: '3306',
        connectionParams:
            'useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false',
    };
}

function buildConnectionUri({ dbType, host, port, databaseName, connectionParams }) {
    const normalizedDbType = String(dbType || 'MYSQL').trim().toUpperCase();
    const normalizedHost = String(host || '').trim();
    const normalizedPort = String(port || '').trim();
    const normalizedDatabaseName = String(databaseName || '').trim();
    const normalizedParams = String(connectionParams || '').trim();
    if (!normalizedHost || !normalizedPort || !normalizedDatabaseName) {
        return '';
    }
    if (normalizedDbType === 'POSTGRESQL') {
        return `jdbc:postgresql://${normalizedHost}:${normalizedPort}/${normalizedDatabaseName}${normalizedParams ? `?${normalizedParams}` : ''}`;
    }
    if (normalizedDbType === 'SQLSERVER') {
        return `jdbc:sqlserver://${normalizedHost}:${normalizedPort};databaseName=${normalizedDatabaseName}${normalizedParams ? `;${normalizedParams}` : ''}`;
    }
    if (normalizedDbType === 'ORACLE') {
        return `jdbc:oracle:thin:@${normalizedHost}:${normalizedPort}/${normalizedDatabaseName}${normalizedParams ? `?${normalizedParams}` : ''}`;
    }
    return `jdbc:mysql://${normalizedHost}:${normalizedPort}/${normalizedDatabaseName}${normalizedParams ? `?${normalizedParams}` : ''}`;
}

function parseConnectionUri(connectionUri, dbType) {
    const text = String(connectionUri || '').trim();
    const defaults = connectionDefaults(dbType);
    if (!text) {
        return {
            host: '',
            port: defaults.port,
            databaseName: '',
            connectionParams: defaults.connectionParams,
        };
    }

    let match = text.match(/^jdbc:mysql:\/\/([^/:?#;]+)(?::(\d+))?\/([^?;]+)(?:\?(.*))?$/i);
    if (match) {
        return {
            host: match[1] || '',
            port: match[2] || '3306',
            databaseName: match[3] || '',
            connectionParams: match[4] || defaults.connectionParams,
        };
    }

    match = text.match(/^jdbc:postgresql:\/\/([^/:?#;]+)(?::(\d+))?\/([^?;]+)(?:\?(.*))?$/i);
    if (match) {
        return {
            host: match[1] || '',
            port: match[2] || '5432',
            databaseName: match[3] || '',
            connectionParams: match[4] || defaults.connectionParams,
        };
    }

    match = text.match(/^jdbc:sqlserver:\/\/([^/:?#;]+)(?::(\d+))?;databaseName=([^;?]+)(?:;(.*))?$/i);
    if (match) {
        return {
            host: match[1] || '',
            port: match[2] || '1433',
            databaseName: match[3] || '',
            connectionParams: match[4] || defaults.connectionParams,
        };
    }

    match = text.match(/^jdbc:oracle:thin:@([^/:?#;]+)(?::(\d+))?\/([^?;]+)(?:\?(.*))?$/i);
    if (match) {
        return {
            host: match[1] || '',
            port: match[2] || '1521',
            databaseName: match[3] || '',
            connectionParams: match[4] || defaults.connectionParams,
        };
    }

    return {
        host: '',
        port: defaults.port,
        databaseName: '',
        connectionParams: defaults.connectionParams,
    };
}

function applyConnectionDefaults(dbType, force = false) {
    const defaults = connectionDefaults(dbType);
    if (force || !String(form.port || '').trim()) {
        form.port = defaults.port;
    }
    if (force || !String(form.connectionParams || '').trim()) {
        form.connectionParams = defaults.connectionParams;
    }
}

function buildPayload() {
    return {
        id: editingId.value || null,
        name: form.name,
        alias: form.alias,
        dbType: form.dbType,
        connectionUri: buildConnectionUri({
            dbType: form.dbType,
            host: form.host,
            port: form.port,
            databaseName: form.databaseName,
            connectionParams: form.connectionParams,
        }),
        authType: form.authType,
        username: form.username,
        password: form.passwordDirty ? form.password : '',
        status: form.status,
    };
}

function handlePasswordFocus() {
    if (form.passwordConfigured && !form.passwordDirty) {
        form.password = '';
    }
}

function handlePasswordInput(event) {
    form.passwordDirty = true;
    form.password = event?.target?.value || '';
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadDataSources() {
    loading.value = true;
    loadError.value = '';
    try {
        const result = await listIntegrationDataSources(filters, handleUnauthorized);
        dataSources.value = Array.isArray(result) ? result : [];
    } catch (error) {
        loadError.value = error?.message || '数据库加载失败';
        dataSources.value = [];
    } finally {
        loading.value = false;
    }
}

function openCreateModal() {
    editingId.value = null;
    saveError.value = '';
    testMessage.value = '';
    testStatus.value = '';
    connectionParamsOpen.value = false;
    Object.assign(form, {
        name: '',
        alias: '',
        dbType: 'MYSQL',
        host: '',
        port: '',
        databaseName: '',
        connectionParams: '',
        authType: 'USERNAME_PASSWORD',
        username: '',
        password: '',
        passwordDirty: false,
        passwordConfigured: false,
        status: 'ACTIVE',
    });
    applyConnectionDefaults('MYSQL', true);
    modalOpen.value = true;
}

function openEditModal(item) {
    editingId.value = item?.id || null;
    saveError.value = '';
    testMessage.value = '';
    testStatus.value = '';
    connectionParamsOpen.value = false;
    const parsedConnection = parseConnectionUri(item?.connectionUri || '', item?.dbType || 'MYSQL');
    Object.assign(form, {
        name: item?.name || '',
        alias: item?.alias || '',
        dbType: item?.dbType || 'MYSQL',
        host: parsedConnection.host,
        port: parsedConnection.port,
        databaseName: parsedConnection.databaseName,
        connectionParams: parsedConnection.connectionParams,
        authType: item?.authType || 'USERNAME_PASSWORD',
        username: item?.username || '',
        password: item?.passwordConfigured ? '••••••••••••' : '',
        passwordDirty: false,
        passwordConfigured: Boolean(item?.passwordConfigured),
        status: item?.status || 'ACTIVE',
    });
    modalOpen.value = true;
}

async function handleDelete(item) {
    const ok = await confirm({
        title: '删除数据库',
        message: `确认删除“${item?.name || '当前数据库'}”吗？`,
        confirmText: '删除',
        cancelText: '取消',
    });
    if (!ok) {
        return;
    }
    await deleteIntegrationDataSource(item.id, handleUnauthorized);
    await loadDataSources();
}

async function handleTestConnection() {
    testing.value = true;
    testMessage.value = '';
    testStatus.value = '';
    try {
        const result = await testIntegrationDataSource(buildPayload(), handleUnauthorized);
        testMessage.value = result?.message || '连接成功';
        testStatus.value = 'success';
    } catch (error) {
        testMessage.value = error?.message || '连接失败';
        testStatus.value = 'error';
    } finally {
        testing.value = false;
    }
}

async function handleSave() {
    saveError.value = '';
    if (
        !buildConnectionUri({
            dbType: form.dbType,
            host: form.host,
            port: form.port,
            databaseName: form.databaseName,
            connectionParams: form.connectionParams,
        })
    ) {
        saveError.value = '请填写 IP、端口和数据库名';
        return;
    }
    try {
        if (editingId.value) {
            await updateIntegrationDataSource(editingId.value, buildPayload(), handleUnauthorized);
        } else {
            await createIntegrationDataSource(buildPayload(), handleUnauthorized);
        }
        modalOpen.value = false;
        await loadDataSources();
    } catch (error) {
        saveError.value = error?.message || '保存失败';
    }
}

function statusLabel(status) {
    if (status === 'ACTIVE') {
        return '运行中';
    }
    if (status === 'DRAFT') {
        return '草稿';
    }
    return status || '未知';
}

function statusDot(status) {
    return status === 'ACTIVE' ? 'bg-emerald-500' : 'bg-slate-300';
}

watch(
    () => form.dbType,
    (nextValue, previousValue) => {
        if (!nextValue || nextValue === previousValue) {
            return;
        }
        applyConnectionDefaults(nextValue, false);
    }
);

onMounted(loadDataSources);
</script>

<template>
    <div class="flex h-full flex-col overflow-hidden bg-[#f6f7f8]">
        <header class="border-b border-slate-200 bg-white px-8 pt-6">
            <div class="flex items-center gap-8">
                <button class="border-b-2 border-primary pb-4 text-sm font-semibold text-primary">
                    数据库
                </button>
                <button
                    class="border-b-2 border-transparent pb-4 text-sm font-medium text-slate-400"
                    type="button"
                    @click="router.push(ROUTE_PATHS.adminIntegrationDatasets)"
                >
                    数据集
                </button>
            </div>
        </header>

        <section
            class="flex flex-col gap-4 border-b border-slate-100 bg-slate-50/80 px-8 py-6 md:flex-row md:items-center md:justify-between"
        >
            <div class="flex flex-1 flex-col gap-4 md:max-w-3xl md:flex-row">
                <div class="md:w-52">
                    <AppSelect
                        :model-value="filters.dbType"
                        :options="dbTypeOptions"
                        size="sm"
                        button-class="border-slate-200 bg-white shadow-none"
                        menu-class="w-full"
                        @update:modelValue="value => { filters.dbType = value; loadDataSources(); }"
                    />
                </div>
                <div class="md:w-52">
                    <AppSelect
                        :model-value="filters.status"
                        :options="statusOptions"
                        size="sm"
                        button-class="border-slate-200 bg-white shadow-none"
                        menu-class="w-full"
                        @update:modelValue="value => { filters.status = value; loadDataSources(); }"
                    />
                </div>
                <div class="relative flex-1">
                    <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">search</span>
                    <input
                        v-model="filters.keyword"
                        class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/10"
                        placeholder="搜索数据库名称..."
                        type="text"
                        @keyup.enter="loadDataSources"
                    />
                </div>
            </div>
            <button
                class="flex items-center justify-center gap-2 rounded-xl bg-primary px-6 py-2.5 font-medium text-white shadow-md transition-all active:scale-95"
                type="button"
                @click="openCreateModal"
            >
                <span class="material-symbols-outlined text-xl">add</span>
                创建数据库
            </button>
        </section>

        <section class="flex-1 overflow-y-auto px-8 pb-8 pt-6">
            <div v-if="loadError" class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                {{ loadError }}
            </div>

            <div v-if="loading" class="py-16 text-center text-sm text-slate-400">数据库加载中...</div>

            <div v-else class="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
                <article
                    v-for="item in dataSources"
                    :key="item.id"
                    class="group flex min-h-[220px] flex-col rounded-[1.25rem] border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary hover:shadow-xl hover:shadow-slate-200/70"
                >
                    <div class="mb-6 flex items-start justify-between">
                        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-50 text-primary">
                            <span class="material-symbols-outlined">database</span>
                        </div>
                        <div class="flex gap-2">
                            <button class="text-slate-300 transition-colors hover:text-primary" type="button" @click.stop="openEditModal(item)">
                                <span class="material-symbols-outlined text-[20px]">edit</span>
                            </button>
                            <button class="text-slate-300 transition-colors hover:text-rose-500" type="button" @click.stop="handleDelete(item)">
                                <span class="material-symbols-outlined text-[20px]">delete</span>
                            </button>
                        </div>
                    </div>
                    <div class="space-y-3">
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">{{ item.alias || item.name }}</h3>
                            <p class="mt-1 text-xs text-slate-500">{{ item.name }}</p>
                        </div>
                        <p class="flex items-center gap-1 text-xs text-slate-500">
                            <span :class="['inline-block h-2 w-2 rounded-full', statusDot(item.status)]" />
                            {{ statusLabel(item.status) }}
                        </p>
                        <div class="flex flex-wrap gap-2">
                            <span class="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-slate-600">
                                {{ item.dbType }}
                            </span>
                            <span class="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600">
                                {{ item.authType || 'USERNAME_PASSWORD' }}
                            </span>
                        </div>
                        <p class="line-clamp-3 text-xs leading-5 text-slate-400">
                            {{ item.connectionUri || '未配置连接串' }}
                        </p>
                    </div>
                </article>

                <button
                    class="flex min-h-[220px] flex-col items-center justify-center rounded-[1.25rem] border-2 border-dashed border-slate-200 bg-white text-center transition-all hover:border-primary"
                    type="button"
                    @click="openCreateModal"
                >
                    <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-50 text-slate-400">
                        <span class="material-symbols-outlined">add</span>
                    </div>
                <span class="text-sm font-semibold text-slate-400">添加新数据库</span>
                </button>
            </div>
        </section>

        <BaseModal :open="modalOpen" panel-class="max-w-5xl" @close="modalOpen = false">
            <template #header>
                <div class="border-b border-slate-200 px-6 py-5">
                    <div class="flex items-center justify-between">
                        <div>
                            <h2 class="text-lg font-bold text-slate-900">{{ pageTitle }}</h2>
<!--                            <p class="mt-1 text-sm text-slate-400">参考 stitch_ai 原型的数据库弹窗布局</p>-->
                        </div>
                        <button class="text-slate-400 transition-colors hover:text-slate-700" type="button" @click="modalOpen = false">
                            <span class="material-symbols-outlined">close</span>
                        </button>
                    </div>
                </div>
            </template>

            <template #content>
                <div class="space-y-5 px-6 py-6">
                    <div v-if="saveError" class="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                        {{ saveError }}
                    </div>
                    <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">名称</span>
                            <input v-model="form.name" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10" />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">别名</span>
                            <input v-model="form.alias" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10" />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">数据库类型</span>
                            <AppSelect
                                v-model="form.dbType"
                                :options="dbTypeOptions.slice(1)"
                                placeholder="请选择数据库类型"
                                button-class="border-slate-200 bg-slate-50 shadow-none"
                                menu-class="w-full"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">状态</span>
                            <AppSelect
                                v-model="form.status"
                                :options="statusOptions.slice(1)"
                                placeholder="请选择状态"
                                button-class="border-slate-200 bg-slate-50 shadow-none"
                                menu-class="w-full"
                            />
                        </label>
                    </div>
                    <div class="grid grid-cols-1 gap-5 md:grid-cols-3">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">服务器 IP</span>
                            <input
                                v-model.trim="form.host"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：127.0.0.1"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">端口</span>
                            <input
                                v-model.trim="form.port"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：3306"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">数据库名</span>
                            <input
                                v-model.trim="form.databaseName"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：lingzhou"
                            />
                        </label>
                    </div>
                    <section class="rounded-2xl border border-slate-200 bg-slate-50/80">
                        <button
                            type="button"
                            class="flex w-full items-center justify-between px-4 py-3 text-left"
                            @click="connectionParamsOpen = !connectionParamsOpen"
                        >
                            <div>
                                <p class="text-sm font-semibold text-slate-900">连接参数</p>
                                <p class="mt-1 text-xs text-slate-500">
                                    系统已自动填好常用参数，通常无需修改。
                                </p>
                            </div>
                            <span class="material-symbols-outlined text-slate-400">
                                {{ connectionParamsOpen ? 'expand_less' : 'expand_more' }}
                            </span>
                        </button>

                        <div v-if="connectionParamsOpen" class="border-t border-slate-200 px-4 py-4">
                            <label class="space-y-2">
                                <span class="text-sm font-semibold text-slate-500">附加连接参数</span>
                                <input
                                    v-model.trim="form.connectionParams"
                                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    placeholder="可选；系统已自动填好常用参数"
                                />
                                <p class="text-xs leading-5 text-slate-400">
                                    可按实际驱动要求继续补充或调整，不填写时使用当前默认参数。
                                </p>
                            </label>
                        </div>
                    </section>
                    <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">用户名</span>
                            <input v-model="form.username" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10" />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-500">密码</span>
                            <input
                                type="password"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                :placeholder="form.passwordConfigured ? '已配置，重新输入后将覆盖当前值' : '请输入数据库密码'"
                                :value="form.password"
                                @focus="handlePasswordFocus"
                                @input="handlePasswordInput"
                            />
                        </label>
                    </div>
                    <div class="min-h-[96px] rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-3.5 transition-all">
                        <div
                            v-if="testMessage"
                            class="flex items-start gap-3 rounded-2xl border px-4 py-3.5"
                            :class="
                                testStatus === 'success'
                                    ? 'border-emerald-200 bg-emerald-50/80 text-emerald-700'
                                    : 'border-rose-200 bg-rose-50 text-rose-700'
                            "
                        >
                            <div
                                class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full"
                                :class="testStatus === 'success' ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'"
                            >
                                <span class="material-symbols-outlined text-[18px]">
                                    {{ testStatus === 'success' ? 'check_circle' : 'error' }}
                                </span>
                            </div>
                            <div class="min-w-0">
                                <div class="text-sm font-semibold">
                                    {{ testStatus === 'success' ? '连接验证通过' : '连接验证失败' }}
                                </div>
                                <div class="mt-1 text-sm leading-6">
                                    {{ testMessage }}
                                </div>
                            </div>
                        </div>
                        <div v-else class="flex h-full min-h-[66px] items-center rounded-2xl px-1 text-sm text-slate-400">
                            连接验证结果将在这里显示
                        </div>
                    </div>
                </div>
            </template>

            <template #footer>
                <div class="flex items-center justify-between border-t border-slate-200 px-6 py-4">
                    <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50" type="button" :disabled="testing" @click="handleTestConnection">
                        {{ testing ? '测试中...' : '测试连接' }}
                    </button>
                    <div class="flex gap-3">
                        <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50" type="button" @click="modalOpen = false">
                            关闭
                        </button>
                        <button class="rounded-xl bg-primary px-5 py-2 text-sm font-semibold text-white transition-all active:scale-95" type="button" @click="handleSave">
                            保存
                        </button>
                    </div>
                </div>
            </template>
        </BaseModal>
    </div>
</template>
