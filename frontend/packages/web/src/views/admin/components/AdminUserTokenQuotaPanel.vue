<script setup>
import { computed, h, onMounted, ref } from 'vue';
import { createColumnHelper } from '@tanstack/vue-table';
import AdminDataTable, {
    createActionsColumn,
    createTextColumn,
    createUserTypeColumn,
} from '@/components/AdminDataTable/index';
import MiniPagination from '@/components/MiniPagination.vue';
import AdminGrantUserTokenQuotaModalContent from './AdminGrantUserTokenQuotaModalContent.vue';
import AdminGrantUserTokenQuotaModalFooter from './AdminGrantUserTokenQuotaModalFooter.vue';
import AdminUpdateUserTokenQuotaModalContent from './AdminUpdateUserTokenQuotaModalContent.vue';
import AdminUpdateUserTokenQuotaModalFooter from './AdminUpdateUserTokenQuotaModalFooter.vue';
import { alert, openModal } from '@/composables/useModal';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import { clearUserSession } from '@/composables/useCurrentUser';
import { listUsers as fetchUsers } from '@/api/users';
import { UserBean } from '@/model/bean';

const router = useRouter();
const users = ref([]);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const loading = ref(false);
const pageSizeOptions = [10, 20, 50];
const columnHelper = createColumnHelper();

function formatTokenCount(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '0';
    }
    return new Intl.NumberFormat('zh-CN').format(Math.max(0, Math.trunc(number)));
}

const userActions = [
    {
        key: 'grant-quota',
        label: '发放额度',
        class: 'text-sm font-medium text-emerald-600 hover:text-emerald-700',
        onClick: row => {
            openGrantQuotaDialog(row);
        },
    },
    {
        key: 'update-quota',
        label: '修改额度',
        class: 'text-sm font-medium text-blue-600 hover:text-blue-700',
        onClick: row => {
            openUpdateQuotaDialog(row);
        },
    },
];

const columns = [
    createTextColumn(columnHelper, {
        accessorKey: 'name',
        header: '姓名',
        maxWidth: 200,
        minWidth: 180,
        textTone: 'dark',
    }),
    createTextColumn(columnHelper, {
        accessorKey: 'code',
        header: '登录名',
        maxWidth: 180,
        minWidth: 160,
    }),
    createUserTypeColumn(columnHelper, {
        header: '用户类型',
        minWidth: 140,
    }),
    createTextColumn(columnHelper, {
        accessorKey: 'roleName',
        header: '角色',
        maxWidth: 180,
        minWidth: 140,
    }),
    columnHelper.accessor(row => row.limitModeText, {
        id: 'limitMode',
        header: '额度模式',
        cell: info =>
            h(
                'div',
                {
                    class: info.row.original.unlimited
                        ? 'inline-flex rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-600'
                        : 'inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600',
                },
                info.getValue()
            ),
        meta: {
            minWidth: 140,
            width: 140,
            align: 'center',
        },
    }),
    columnHelper.accessor(row => row.grantedTokensText, {
        id: 'grantedTokens',
        header: '已发放',
        cell: info => h('div', { class: 'truncate font-medium text-slate-800' }, info.getValue()),
        meta: {
            minWidth: 160,
            width: 160,
        },
    }),
    columnHelper.accessor(row => row.consumedTokensText, {
        id: 'consumedTokens',
        header: '已用',
        cell: info => h('div', { class: 'truncate text-amber-600' }, info.getValue()),
        meta: {
            minWidth: 140,
            width: 140,
        },
    }),
    columnHelper.accessor(row => row.remainingTokensText, {
        id: 'remainingTokens',
        header: '剩余',
        cell: info =>
            h(
                'div',
                {
                    class: info.row.original.unlimited
                        ? 'truncate font-medium text-blue-600'
                        : 'truncate font-medium text-emerald-600',
                },
                info.getValue()
            ),
        meta: {
            minWidth: 160,
            width: 160,
        },
    }),
    createActionsColumn(columnHelper, {
        id: 'actions',
        header: '操作',
        width: 220,
        minWidth: 220,
        maxWidth: 220,
        align: 'center',
        stickyRight: true,
        containerClass: 'justify-center gap-4',
        actions: userActions,
    }),
];

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadUsers(targetPage = page.value) {
    loading.value = true;
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await fetchUsers(
            {
                page: requestedPage,
                pageSize: pageSize.value,
            },
            handleUnauthorized
        );
        const userList = Array.isArray(data?.items) ? data.items : [];
        users.value = userList.map(item => {
            const user = UserBean.fromApi(item);
            return {
                id: user.id,
                name: user.name || '-',
                code: user.code || '-',
                userType: user.userType,
                roleName: user.roleName || '-',
                tokenQuota: user.tokenQuota || null,
                unlimited: Boolean(user.tokenQuota?.unlimited),
                limitModeText: user.tokenQuota?.unlimited ? '无限制' : '有限额',
                grantedTokensText: formatTokenCount(user.tokenQuota?.grantedTokens),
                consumedTokensText: formatTokenCount(user.tokenQuota?.consumedTokens),
                remainingTokensText: user.tokenQuota?.unlimited
                    ? '无限制'
                    : formatTokenCount(user.tokenQuota?.remainingTokens),
            };
        });
        total.value = Number(data?.total ?? userList.length) || 0;
        page.value = Number(data?.page ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? pageSize.value) || pageSize.value;
        if (users.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadUsers(lastPage);
            }
        }
    } catch (error) {
        users.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }
}

async function openUpdateQuotaDialog(row) {
    const updated = await openModal({
        title: '修改用户额度',
        content: {
            component: AdminUpdateUserTokenQuotaModalContent,
        },
        footer: {
            component: AdminUpdateUserTokenQuotaModalFooter,
            props: {
                confirmText: '确认修改',
                cancelText: '取消',
            },
        },
        confirmText: '确认修改',
        cancelText: '取消',
        showCancel: true,
        showClose: true,
        context: {
            id: row?.id ?? null,
            name: row?.name || '',
            account: row?.code || '',
            remainingTokens: row?.tokenQuota?.remainingTokens ?? 0,
            unlimited: Boolean(row?.tokenQuota?.unlimited),
            currentGrantedText: row?.grantedTokensText || '0',
            currentConsumedText: row?.consumedTokensText || '0',
            currentRemainingText: row?.remainingTokensText || '0',
            formErrors: {},
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });
    if (updated) {
        await loadUsers(page.value);
        await alert({
            title: '修改成功',
            message: `已更新用户“${row?.name || row?.code || '-'}”的额度设置。`,
        });
    }
}

async function openGrantQuotaDialog(row) {
    const granted = await openModal({
        title: '发放用户额度',
        content: {
            component: AdminGrantUserTokenQuotaModalContent,
        },
        footer: {
            component: AdminGrantUserTokenQuotaModalFooter,
            props: {
                confirmText: '确认发放',
                cancelText: '取消',
            },
        },
        confirmText: '确认发放',
        cancelText: '取消',
        showCancel: true,
        showClose: true,
        context: {
            id: row?.id ?? null,
            name: row?.name || '',
            account: row?.code || '',
            grantTokens: 1000000,
            currentGrantedText: row?.grantedTokensText || '0',
            currentConsumedText: row?.consumedTokensText || '0',
            currentRemainingText: row?.remainingTokensText || '0',
            formErrors: {},
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });
    if (granted) {
        await loadUsers(page.value);
        await alert({
            title: '发放成功',
            message: `已为用户“${row?.name || row?.code || '-'}”发放额度。`,
        });
    }
}

function handlePageChange(nextPage) {
    loadUsers(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadUsers(1);
}

const quotaEnabledText = computed(() => {
    const firstUser = users.value[0];
    return firstUser?.tokenQuota?.enabled ? '已启用' : '已关闭';
});

onMounted(() => {
    loadUsers();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header
            class="flex h-16 shrink-0 items-center justify-between border-b border-slate-100 bg-white px-8"
        >
            <div>
                <h1 class="text-xl font-bold text-slate-900">用户额度</h1>
                <p class="mt-1 text-sm text-slate-500">
                    管理 `user_token_account` 的发放额度。当前限额开关：{{ quotaEnabledText }}
                </p>
            </div>
            <button
                type="button"
                class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="loading"
                @click="loadUsers(page)"
            >
                {{ loading ? '刷新中...' : '刷新列表' }}
            </button>
        </header>

        <div class="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#f8fafc] p-8">
            <AdminDataTable :columns="columns" :data="users" min-table-width="1280px" />
            <div class="mt-auto flex justify-end pt-3">
                <MiniPagination
                    :page="page"
                    :page-size="pageSize"
                    :total="total"
                    :page-size-options="pageSizeOptions"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>
        </div>
    </section>
</template>
