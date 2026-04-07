<script setup>
import { computed, h, onMounted, ref } from 'vue';
import { createColumnHelper } from '@tanstack/vue-table';
import AdminDataTable, {
    createActionsColumn,
    createTextColumn,
    createUserTypeColumn,
} from '@/components/AdminDataTable/index';
import MiniPagination from '@/components/MiniPagination.vue';
import AdminAddUserModalContent from './AdminAddUserModalContent.vue';
import AdminAddUserModalFooter from './AdminAddUserModalFooter.vue';
import AdminResetUserPasswordModalContent from './AdminResetUserPasswordModalContent.vue';
import AdminResetUserPasswordModalFooter from './AdminResetUserPasswordModalFooter.vue';
import AdminUserStateToggleCell from './AdminUserStateToggleCell.vue';
import { alert, confirm, openModal } from '@/composables/useModal';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { deleteUser, listUsers as fetchUsers, updateUserState } from '@/api/users';
import { UserBean } from '@/model/bean';
import { USER_STATES } from '@/model/enums/user-state';
import { USER_TYPES } from '@/model/enums/user-type';

const router = useRouter();
const users = ref([]);
const isCurrentUserAdmin = computed(() => currentUserState.profile?.userType === USER_TYPES.ADMIN);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];

const defaultAddUserOptions = {
    title: '新增用户',
    confirmText: '确定',
    cancelText: '取消',
    initialForm: {
        name: '',
        account: '',
        password: '',
        mobile: '',
        email: '',
        userType: USER_TYPES.NORMAL,
    },
};

const columnHelper = createColumnHelper();

function isFixedAdminRow(row) {
    return (row?.code || '').toLowerCase() === 'admin';
}

const userActions = [
    {
        key: 'edit',
        label: '编辑资料',
        class: 'text-sm font-medium text-blue-600 hover:text-blue-700',
        onClick: row => {
            openEditUserDialog(row);
        },
    },
    {
        key: 'reset-password',
        label: '重置密码',
        class: 'text-sm font-medium text-violet-600 hover:text-violet-700',
        disabled: () => !isCurrentUserAdmin.value,
        onClick: row => {
            openResetPasswordDialog(row);
        },
    },
    {
        key: 'delete',
        label: '删除用户',
        class: 'text-sm font-medium text-red-500 hover:text-red-600',
        disabled: row => !canManageUser(row),
        onClick: row => {
            openDeleteUserDialog(row);
        },
    },
];

const columns = [
    createTextColumn(columnHelper, {
        accessorKey: 'name',
        header: '姓名',
        maxWidth: 220,
        minWidth: 220,
        textTone: 'dark',
    }),
    createTextColumn(columnHelper, {
        accessorKey: 'code',
        header: '登录名',
        maxWidth: 180,
        minWidth: 180,
    }),
    createTextColumn(columnHelper, {
        accessorKey: 'mobile',
        header: '手机号',
        maxWidth: 200,
        minWidth: 200,
    }),
    createTextColumn(columnHelper, {
        accessorKey: 'email',
        header: '邮箱',
        maxWidth: 280,
        minWidth: 280,
    }),
    createUserTypeColumn(columnHelper, {
        header: '用户类型',
        minWidth: 160,
    }),
    columnHelper.accessor('state', {
        header: '状态',
        cell: info =>
            h(AdminUserStateToggleCell, {
                row: info.row.original,
                disabled: !canManageUser(info.row.original),
                onToggle: openToggleUserStateDialog,
            }),
        meta: {
            width: 180,
            minWidth: 180,
            maxWidth: 180,
            align: 'center',
        },
    }),
    createActionsColumn(columnHelper, {
        id: 'actions',
        header: '操作',
        width: 260,
        minWidth: 260,
        maxWidth: 260,
        align: 'center',
        stickyRight: false,
        containerClass: 'justify-center gap-4',
        actions: userActions,
    }),
];

function addUserDialog(options = {}) {
    const resolved = {
        ...defaultAddUserOptions,
        ...options,
        initialForm: {
            ...defaultAddUserOptions.initialForm,
            ...(options.initialForm || {}),
        },
    };

    return openModal({
        title: resolved.title,
        content: {
            component: AdminAddUserModalContent,
            props: {
                mode: resolved.mode || 'create',
            },
        },
        footer: {
            component: AdminAddUserModalFooter,
            props: {
                confirmText: resolved.confirmText,
                cancelText: resolved.cancelText,
                mode: resolved.mode || 'create',
            },
        },
        confirmText: resolved.confirmText,
        cancelText: resolved.cancelText,
        showCancel: true,
        showClose: true,
        context: {
            ...resolved.initialForm,
            formErrors: {},
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });
}

async function openAddUserDialog() {
    const created = await addUserDialog();
    if (created) {
        await loadUsers(page.value);
    }
}

async function openEditUserDialog(row) {
    const edited = await addUserDialog({
        title: '编辑用户',
        confirmText: '保存',
        mode: 'edit-profile',
        initialForm: {
            id: row?.id ?? null,
            name: row?.name && row.name !== '-' ? row.name : '',
            account: row?.code && row.code !== '-' ? row.code : '',
            mobile: row?.mobile && row.mobile !== '-' ? row.mobile : '',
            email: row?.email && row.email !== '-' ? row.email : '',
            userType: row?.userType ?? USER_TYPES.NORMAL,
        },
    });
    if (edited) {
        await loadUsers(page.value);
    }
}

async function openResetPasswordDialog(row) {
    if (!isCurrentUserAdmin.value) {
        await alert({
            title: '提示',
            message: '仅管理员可重置密码',
        });
        return;
    }

    const updated = await openModal({
        title: '重置密码',
        content: {
            component: AdminResetUserPasswordModalContent,
        },
        footer: {
            component: AdminResetUserPasswordModalFooter,
            props: {
                confirmText: '确认重置',
                cancelText: '取消',
            },
        },
        confirmText: '确认重置',
        cancelText: '取消',
        showCancel: true,
        showClose: true,
        context: {
            id: row?.id ?? null,
            name: row?.name && row.name !== '-' ? row.name : '',
            account: row?.code && row.code !== '-' ? row.code : '',
            password: '',
            confirmPassword: '',
            formErrors: {},
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });
    if (updated) {
        await alert({
            title: '重置成功',
            message: `用户“${row?.name || row?.code || '-'}”的密码已更新。`,
        });
    }
}

function canManageUser(row) {
    if (!isCurrentUserAdmin.value) {
        return false;
    }
    return (row?.code || '').toLowerCase() !== 'admin';
}

async function openToggleUserStateDialog(row) {
    if (!canManageUser(row)) {
        await alert({
            title: '提示',
            message: isCurrentUserAdmin.value ? 'admin用户不可禁用' : '普通用户不可禁用或删除用户',
        });
        return;
    }

    const targetState = row.state === USER_STATES.ACTIVE ? USER_STATES.INACTIVE : USER_STATES.ACTIVE;
    const actionText = targetState === USER_STATES.INACTIVE ? '禁用' : '启用';
    const confirmed = await confirm({
        title: `${actionText}用户`,
        message: `确认${actionText}用户“${row.name || row.code}”吗？`,
        confirmText: actionText,
        cancelText: '取消',
    });
    if (!confirmed) {
        return;
    }

    try {
        await updateUserState(
            {
                id: row.id,
                state: targetState,
            },
            handleUnauthorized,
        );
        await loadUsers(page.value);
    } catch (error) {
        await alert({
            title: '操作失败',
            message: error?.message || `${actionText}失败`,
        });
    }
}

async function openDeleteUserDialog(row) {
    if (!canManageUser(row)) {
        await alert({
            title: '提示',
            message: isCurrentUserAdmin.value ? 'admin用户不可删除' : '普通用户不可禁用或删除用户',
        });
        return;
    }

    const confirmed = await confirm({
        title: '删除用户',
        message: `确认删除用户“${row.name || row.code}”吗？删除后不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }

    try {
        await deleteUser(
            {
                id: row.id,
            },
            handleUnauthorized,
        );
        await loadUsers(page.value);
    } catch (error) {
        await alert({
            title: '操作失败',
            message: error?.message || '删除失败',
        });
    }
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadUsers(targetPage = page.value) {
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await fetchUsers(
            {
                page: requestedPage,
                pageSize: pageSize.value,
            },
            handleUnauthorized,
        );
        const userList = Array.isArray(data?.items) ? data.items : [];
        users.value = userList.map(item => {
            const user = UserBean.fromApi(item);
            return {
                id: user.id,
                name: user.name || '-',
                code: user.code || '-',
                mobile: user.mobile || '-',
                email: user.email || '-',
                userType: user.userType,
                state: user.state,
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

onMounted(() => {
    loadUsers();
});

</script>

<template>
    <section
        class="admin-page admin-page--system-panel flex h-full min-h-0 flex-col bg-slate-50"
        data-component="AdminSystemPanel"
    >
        <header class="flex h-16 shrink-0 items-center justify-between border-b border-slate-100 bg-white px-8">
            <h1 class="text-xl font-bold text-slate-900">用户管理</h1>
            <button
                type="button"
                class="flex items-center justify-center gap-2 rounded-lg bg-primary px-6 py-2 font-medium text-white shadow-md transition-all hover:bg-blue-700"
                @click="openAddUserDialog"
            >
                <span class="material-symbols-outlined fill-0 text-lg font-bold">add</span>
                <span>新增用户</span>
            </button>
        </header>

        <div class="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#f8fafc] p-8">
            <AdminDataTable :columns="columns" :data="users" min-table-width="1480px" />
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
