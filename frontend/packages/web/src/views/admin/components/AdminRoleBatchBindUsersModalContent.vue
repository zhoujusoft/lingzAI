<script setup>
import { computed, onMounted, ref } from 'vue';
import MiniPagination from '@/components/MiniPagination.vue';
import { listUsers } from '@/api/users';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const loading = ref(false);
const users = ref([]);
const pageSizeOptions = [10, 20, 50];

const roleName = computed(() => props.context.roleName || '-');
const page = computed(() => props.context.page ?? 1);
const pageSize = computed(() => props.context.pageSize ?? 10);
const total = computed(() => props.context.total ?? 0);
const keyword = computed({
    get: () => props.context.keyword ?? '',
    set: value => {
        props.context.keyword = value;
    },
});
const selectedUserIds = computed(() =>
    Array.isArray(props.context.selectedUserIds) ? props.context.selectedUserIds : []
);

function clearSubmitError() {
    if (props.context.submitError) {
        props.context.submitError = '';
    }
}

function isSelected(userId) {
    return selectedUserIds.value.includes(userId);
}

function toggleRow(user, checked) {
    if (!user?.id) {
        return;
    }
    const next = new Set(selectedUserIds.value);
    if (checked) {
        next.add(user.id);
    } else {
        next.delete(user.id);
    }
    props.context.selectedUserIds = Array.from(next);
    clearSubmitError();
}

const selectableUsers = computed(() => users.value);
const selectedCountOnPage = computed(
    () => selectableUsers.value.filter(user => isSelected(user.id)).length
);
const allCurrentPageSelected = computed(
    () =>
        selectableUsers.value.length > 0 &&
        selectedCountOnPage.value === selectableUsers.value.length
);

function toggleSelectAllOnPage(checked) {
    const next = new Set(selectedUserIds.value);
    for (const user of selectableUsers.value) {
        if (checked) {
            next.add(user.id);
        } else {
            next.delete(user.id);
        }
    }
    props.context.selectedUserIds = Array.from(next);
    clearSubmitError();
}

function formatUserType(userType) {
    return Number(userType) === 0 ? '管理员' : '普通用户';
}

function formatState(state) {
    return Number(state) === 1 ? '启用' : '停用';
}

async function loadUsers(targetPage = page.value) {
    loading.value = true;
    try {
        const data = await listUsers(
            {
                page: Math.max(1, Number(targetPage) || 1),
                pageSize: pageSize.value,
                keyword: keyword.value,
            },
            props.context.onUnauthorized
        );
        users.value = Array.isArray(data?.items) ? data.items : [];
        props.context.total = Number(data?.total ?? 0) || 0;
        props.context.page = Number(data?.page ?? targetPage) || targetPage;
        props.context.pageSize = Number(data?.pageSize ?? pageSize.value) || pageSize.value;
    } catch (error) {
        users.value = [];
        props.context.total = 0;
    } finally {
        loading.value = false;
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
    props.context.pageSize = safeSize;
    loadUsers(1);
}

function handleSearch() {
    loadUsers(1);
}

onMounted(() => {
    if (!Array.isArray(props.context.selectedUserIds)) {
        props.context.selectedUserIds = [];
    }
    if (!props.context.page) {
        props.context.page = 1;
    }
    if (!props.context.pageSize) {
        props.context.pageSize = 10;
    }
    if (!props.context.keyword) {
        props.context.keyword = '';
    }
    loadUsers(props.context.page);
});
</script>

<template>
    <div class="space-y-4 px-8 py-6">
        <div
            class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600"
        >
            将选中的用户批量绑定到角色：<span class="font-semibold text-slate-900">{{
                roleName
            }}</span>
        </div>

        <div class="flex items-center gap-2">
            <input
                v-model="keyword"
                type="text"
                placeholder="搜索账号/姓名/手机号/邮箱"
                class="w-72 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                @keyup.enter="handleSearch"
            />
            <button
                type="button"
                class="rounded-lg bg-slate-100 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-200"
                @click="handleSearch"
            >
                搜索
            </button>
            <div class="ml-auto text-sm text-slate-500">
                已选择
                <span class="font-semibold text-slate-800">{{ selectedUserIds.length }}</span> 人
            </div>
        </div>

        <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <table class="min-w-full table-fixed text-sm">
                <thead class="bg-slate-50 text-slate-600">
                    <tr>
                        <th class="w-14 px-4 py-3 text-left">
                            <input
                                type="checkbox"
                                :checked="allCurrentPageSelected"
                                @change="toggleSelectAllOnPage($event.target.checked)"
                            />
                        </th>
                        <th class="w-44 px-4 py-3 text-left">账号</th>
                        <th class="w-44 px-4 py-3 text-left">姓名</th>
                        <th class="w-40 px-4 py-3 text-left">用户类型</th>
                        <th class="w-48 px-4 py-3 text-left">当前角色</th>
                        <th class="w-28 px-4 py-3 text-left">状态</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                    <tr v-if="loading">
                        <td colspan="6" class="px-4 py-6 text-center text-slate-400">加载中...</td>
                    </tr>
                    <tr v-else-if="users.length === 0">
                        <td colspan="6" class="px-4 py-6 text-center text-slate-400">
                            暂无用户数据
                        </td>
                    </tr>
                    <tr v-for="user in users" :key="user.id" class="hover:bg-slate-50">
                        <td class="px-4 py-3">
                            <input
                                type="checkbox"
                                :checked="isSelected(user.id)"
                                @change="toggleRow(user, $event.target.checked)"
                            />
                        </td>
                        <td class="px-4 py-3 text-slate-700">{{ user.code || '-' }}</td>
                        <td class="px-4 py-3 text-slate-700">{{ user.name || '-' }}</td>
                        <td class="px-4 py-3 text-slate-700">
                            {{ formatUserType(user.userType) }}
                        </td>
                        <td class="px-4 py-3 text-slate-700">{{ user.roleName || '-' }}</td>
                        <td class="px-4 py-3 text-slate-700">{{ formatState(user.state) }}</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <div class="flex justify-end">
            <MiniPagination
                :page="page"
                :page-size="pageSize"
                :total="total"
                :page-size-options="pageSizeOptions"
                @page-change="handlePageChange"
                @page-size-change="handlePageSizeChange"
            />
        </div>

        <p v-if="context.submitError" class="text-sm text-red-500">{{ context.submitError }}</p>
    </div>
</template>
