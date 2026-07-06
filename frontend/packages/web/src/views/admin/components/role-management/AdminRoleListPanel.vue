<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MiniPagination from '@/components/MiniPagination.vue';
import AdminRoleBatchBindUsersModalContent from '@/views/admin/components/AdminRoleBatchBindUsersModalContent.vue';
import AdminRoleBatchBindUsersModalFooter from '@/views/admin/components/AdminRoleBatchBindUsersModalFooter.vue';
import { alert, confirm, openModal } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { listRoles as fetchRoles, deleteRole } from '@/api/roles';
import { ROUTE_PATHS } from '@/router/routePaths';

const emit = defineEmits(['open-role-create', 'open-role-detail', 'open-role-resources']);

const router = useRouter();
const roles = ref([]);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const keyword = ref('');
const pageSizeOptions = [10, 20, 50];

const enabledRoleCount = computed(() => roles.value.filter(role => role.enabled === 1).length);
const disabledRoleCount = computed(() => roles.value.filter(role => role.enabled !== 1).length);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function formatDateTime(value) {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '-';
    }
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
}

async function openDeleteRoleDialog(row) {
    const confirmed = await confirm({
        title: '删除角色',
        message: `确认删除角色"${row.roleName}"吗？删除后不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }

    try {
        await deleteRole(row.id, handleUnauthorized);
        await loadRoles(page.value);
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除角色失败',
        });
    }
}

async function openBatchBindUsersDialog(row) {
    const bound = await openModal({
        title: `批量绑定用户 - ${row?.roleName || '-'}`,
        panelClass: 'max-w-[980px]',
        content: {
            component: AdminRoleBatchBindUsersModalContent,
        },
        footer: {
            component: AdminRoleBatchBindUsersModalFooter,
            props: {
                confirmText: '批量绑定',
                cancelText: '取消',
            },
        },
        showCancel: true,
        showClose: true,
        context: {
            roleId: row?.id ?? null,
            roleName: row?.roleName || '',
            selectedUserIds: [],
            page: 1,
            pageSize: 10,
            total: 0,
            keyword: '',
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });

    if (bound) {
        await alert({
            title: '绑定成功',
            message: `已将选中用户绑定到角色"${row?.roleName || '-'}"`,
        });
    }
}

async function loadRoles(targetPage = page.value) {
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await fetchRoles(
            {
                page: requestedPage,
                pageSize: pageSize.value,
                keyword: keyword.value,
            },
            handleUnauthorized
        );
        roles.value = Array.isArray(data?.list)
            ? data.list
            : Array.isArray(data?.items)
              ? data.items
              : [];
        total.value = Number(data?.total ?? 0) || 0;
        page.value = Number(data?.page ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? pageSize.value) || pageSize.value;

        if (roles.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadRoles(lastPage);
            }
        }
    } catch {
        roles.value = [];
        total.value = 0;
    }
}

function handlePageChange(nextPage) {
    loadRoles(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadRoles(1);
}

function handleSearch() {
    loadRoles(1);
}

onMounted(() => {
    loadRoles();
});
</script>

<template>
    <section
        class="admin-page flex h-full min-h-0 flex-col bg-slate-100"
        data-component="AdminRoleListPanel"
    >
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h1 class="text-3xl font-bold tracking-tight text-slate-900">角色管理</h1>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        管理系统角色、后台菜单权限和可进入对话的技能工具资源。
                    </p>
                </div>
                <button
                    type="button"
                    class="inline-flex self-start items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/15 transition hover:bg-blue-700 xl:self-auto"
                    @click="emit('open-role-create')"
                >
                    <span class="material-symbols-outlined text-base">add</span>
                    <span>新增角色</span>
                </button>
            </div>

            <div class="mt-5 grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto] xl:items-center">
                <div class="grid gap-2 sm:grid-cols-3">
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ total }}</p>
                        <p class="text-[11px] text-slate-500">角色总数</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ enabledRoleCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页启用</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ disabledRoleCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页停用</p>
                    </article>
                </div>
                <div class="flex min-w-0 flex-wrap items-center gap-2 sm:flex-nowrap">
                    <div class="relative min-w-0 flex-1 sm:w-72 sm:flex-none">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        >
                            search
                        </span>
                        <input
                            v-model.trim="keyword"
                            type="text"
                            placeholder="搜索角色编码或名称"
                            class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/10"
                            @keyup.enter="handleSearch"
                        />
                    </div>
                    <button
                        type="button"
                        class="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="handleSearch"
                    >
                        <span class="material-symbols-outlined text-[18px]">search</span>
                        <span>搜索</span>
                    </button>
                </div>
            </div>
        </header>

        <div class="flex min-h-0 flex-1 flex-col p-6">
            <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
                <div class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3 min-[1680px]:grid-cols-4">
                    <article
                        v-for="role in roles"
                        :key="role.id"
                        class="flex min-h-[238px] flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-[0_10px_24px_rgba(15,23,42,0.06)]"
                    >
                        <div class="flex items-start gap-3">
                            <div
                                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-blue-100 bg-blue-50 text-blue-600"
                            >
                                <span class="material-symbols-outlined text-[21px]"
                                    >shield_person</span
                                >
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="flex items-start justify-between gap-3">
                                    <div class="min-w-0">
                                        <h3 class="truncate text-base font-bold text-slate-900">
                                            {{ role.roleName || '-' }}
                                        </h3>
                                        <p class="mt-0.5 truncate text-[11px] text-slate-400">
                                            {{ role.roleCode || '未设置编码' }}
                                        </p>
                                    </div>
                                    <span
                                        :class="[
                                            'shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold',
                                            role.enabled === 1
                                                ? 'border border-emerald-100 bg-emerald-50 text-emerald-700'
                                                : 'border border-slate-200 bg-slate-50 text-slate-500',
                                        ]"
                                    >
                                        {{ role.enabled === 1 ? '启用' : '停用' }}
                                    </span>
                                </div>
                                <p
                                    class="mt-2 line-clamp-2 min-h-10 text-xs leading-5 text-slate-500"
                                >
                                    {{ role.description || '暂无描述' }}
                                </p>
                            </div>
                        </div>

                        <div class="mt-3 flex flex-wrap gap-1.5">
                            <span
                                class="rounded-full border border-blue-100 bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700"
                            >
                                {{ role.enabled === 1 ? '可分配资源权限' : '已停用' }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                更新于 {{ formatDateTime(role.updatedAt) }}
                            </span>
                        </div>

                        <div class="mt-auto border-t border-slate-100 pt-3">
                            <div class="grid grid-cols-4 gap-1.5">
                                <button
                                    type="button"
                                    aria-label="编辑角色"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg bg-blue-600 px-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/10 transition hover:bg-blue-700"
                                    @click="emit('open-role-detail', role)"
                                >
                                    <span class="material-symbols-outlined text-[17px]">edit</span>
                                    <span>编辑</span>
                                </button>
                                <button
                                    type="button"
                                    aria-label="配置角色资源权限"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-blue-100 bg-blue-50 px-2 text-xs font-semibold text-blue-700 transition hover:border-blue-200 hover:bg-blue-100"
                                    @click="emit('open-role-resources', role)"
                                >
                                    <span class="material-symbols-outlined text-[17px]"
                                        >deployed_code</span
                                    >
                                    <span>资源</span>
                                </button>
                                <button
                                    type="button"
                                    aria-label="为角色绑定用户"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
                                    @click="openBatchBindUsersDialog(role)"
                                >
                                    <span class="material-symbols-outlined text-[17px]"
                                        >group_add</span
                                    >
                                    <span>绑定</span>
                                </button>
                                <button
                                    type="button"
                                    aria-label="删除角色"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-rose-200 bg-white px-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50"
                                    @click="openDeleteRoleDialog(role)"
                                >
                                    <span class="material-symbols-outlined text-[17px]"
                                        >delete</span
                                    >
                                    <span>删除</span>
                                </button>
                            </div>
                        </div>
                    </article>

                    <div
                        v-if="!roles.length"
                        class="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-14 text-center text-sm text-slate-400 lg:col-span-2 xl:col-span-3 min-[1680px]:col-span-4"
                    >
                        当前筛选条件下没有匹配角色
                    </div>
                </div>
            </div>
            <div class="flex h-[54px] shrink-0 items-end justify-end">
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
