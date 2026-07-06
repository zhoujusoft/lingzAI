<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { clearUserSession } from '@/composables/useCurrentUser';
import { showToast } from '@/composables/useToast';
import { ROUTE_PATHS } from '@/router/routePaths';
import { ADMIN_MENU_PERMISSION_GROUPS } from '@/model/admin-menu-permissions';
import { getRoleDetail, updateRole } from '@/api/roles';

const props = defineProps({
    roleId: {
        type: Number,
        required: true,
    },
});

const emit = defineEmits(['back', 'saved']);

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const submitError = ref('');
const detail = ref(null);

// ─── Basic info form ───
const form = reactive({
    roleCode: '',
    roleName: '',
    description: '',
    enabled: 1,
    menuPermissions: [],
});

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

// ─── Menu permissions ───
function hasMenuPermission(key) {
    return form.menuPermissions.includes(key);
}

function toggleMenuPermission(key, checked) {
    const next = new Set(form.menuPermissions);
    if (checked) next.add(key);
    else next.delete(key);
    form.menuPermissions = Array.from(next);
}

function isGroupFullySelected(options) {
    return options.length > 0 && options.every(o => hasMenuPermission(o.key));
}

function toggleGroupPermissions(options, checked) {
    const next = new Set(form.menuPermissions);
    for (const o of options) {
        if (checked) next.add(o.key);
        else next.delete(o.key);
    }
    form.menuPermissions = Array.from(next);
}

// ─── Load detail ───
async function loadDetail() {
    loading.value = true;
    loadError.value = '';
    try {
        const data = await getRoleDetail(props.roleId, handleUnauthorized);
        if (!data) throw new Error('角色不存在');
        detail.value = data;
        form.roleCode = data.roleCode || '';
        form.roleName = data.roleName || '';
        form.description = data.description || '';
        form.enabled = data.enabled ?? 1;
        form.menuPermissions = Array.isArray(data.menuPermissions) ? data.menuPermissions : [];
    } catch (error) {
        loadError.value = error?.message || '加载角色详情失败';
    } finally {
        loading.value = false;
    }
}

// ─── Save ───
function validateForm() {
    if (!form.roleCode.trim()) {
        submitError.value = '角色编码不能为空';
        return false;
    }
    if (!form.roleName.trim()) {
        submitError.value = '角色名称不能为空';
        return false;
    }
    return true;
}

async function handleSave() {
    if (saving.value) return;
    if (!validateForm()) return;

    saving.value = true;
    submitError.value = '';

    try {
        await updateRole(
            props.roleId,
            {
                roleCode: form.roleCode.trim(),
                roleName: form.roleName.trim(),
                description: form.description.trim() || null,
                enabled: form.enabled,
                menuPermissions: [...form.menuPermissions],
            },
            handleUnauthorized
        );
    } catch (error) {
        submitError.value = error?.message || '保存角色信息失败';
        saving.value = false;
        return;
    }

    saving.value = false;
    await loadDetail();
    showToast('角色信息已更新', 'success');
    emit('saved');
}

onMounted(() => {
    loadDetail();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <!-- Header -->
        <header class="border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-wrap items-center justify-between gap-4">
                <div class="min-w-0 flex-1">
                    <template v-if="detail">
                        <div class="flex items-start gap-4">
                            <div
                                class="flex h-12 w-12 shrink-0 items-center justify-center rounded-[18px] bg-gradient-to-br from-blue-500 to-blue-600 text-white shadow-sm"
                            >
                                <span class="material-symbols-outlined text-[24px]"
                                    >shield_person</span
                                >
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="flex flex-wrap items-center gap-3">
                                    <h2
                                        class="truncate text-3xl font-bold tracking-tight text-slate-900"
                                    >
                                        角色管理 - {{ detail.roleName }}
                                    </h2>
                                    <span
                                        :class="[
                                            'rounded-full px-3 py-1 text-xs font-semibold',
                                            detail.enabled === 1
                                                ? 'bg-emerald-50 text-emerald-600'
                                                : 'bg-slate-100 text-slate-500',
                                        ]"
                                    >
                                        {{ detail.enabled === 1 ? '启用' : '停用' }}
                                    </span>
                                    <span
                                        v-if="detail.roleCode"
                                        class="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500"
                                    >
                                        {{ detail.roleCode }}
                                    </span>
                                </div>
                                <p
                                    class="mt-2 line-clamp-2 max-w-4xl text-sm leading-6 text-slate-500"
                                >
                                    {{ detail.description || '暂无描述' }}
                                </p>
                            </div>
                        </div>
                    </template>
                    <template v-else>
                        <h2 class="text-3xl font-bold tracking-tight text-slate-900">角色管理</h2>
                    </template>
                </div>
                <div class="flex items-center gap-3">
                    <button
                        type="button"
                        class="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving || loading"
                        @click="handleSave"
                    >
                        {{ saving ? '保存中...' : '保存' }}
                    </button>
                    <button
                        type="button"
                        class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="emit('back')"
                    >
                        返回列表
                    </button>
                </div>
            </div>
        </header>

        <!-- Content -->
        <div class="custom-scrollbar flex-1 overflow-y-auto px-6 pt-6 pb-24">
            <p
                v-if="loadError"
                class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div
                v-if="loading"
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                加载中...
            </div>

            <div v-else-if="detail" class="space-y-5">
                <!-- Error banner -->
                <div
                    v-if="submitError"
                    class="rounded-2xl border border-red-200 bg-red-50 px-5 py-3 text-sm text-red-600"
                >
                    {{ submitError }}
                </div>

                <!-- ─── Card 1: 基础信息 ─── -->
                <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-5">
                        <h3 class="text-lg font-bold text-slate-900">基础信息</h3>
                        <p class="mt-1 text-sm text-slate-500">
                            角色的编码、名称、描述和启用状态。
                        </p>
                    </div>
                    <div class="grid gap-4 md:grid-cols-2">
                        <label class="space-y-1.5 text-sm text-slate-600">
                            <span class="font-medium"
                                >角色编码 <span class="text-red-500">*</span></span
                            >
                            <input
                                v-model="form.roleCode"
                                type="text"
                                placeholder="请输入角色编码"
                                class="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                            />
                        </label>
                        <label class="space-y-1.5 text-sm text-slate-600">
                            <span class="font-medium"
                                >角色名称 <span class="text-red-500">*</span></span
                            >
                            <input
                                v-model="form.roleName"
                                type="text"
                                placeholder="请输入角色名称"
                                class="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                            />
                        </label>
                        <label class="space-y-1.5 text-sm text-slate-600 md:col-span-2">
                            <span class="font-medium">描述</span>
                            <textarea
                                v-model="form.description"
                                rows="2"
                                placeholder="请输入角色描述"
                                class="w-full resize-none rounded-lg border border-slate-200 px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                            />
                        </label>
                        <div class="space-y-1.5 text-sm text-slate-600 md:col-span-2">
                            <span class="font-medium">状态</span>
                            <div class="flex items-center gap-6">
                                <label class="flex cursor-pointer items-center gap-2">
                                    <input
                                        type="radio"
                                        :value="1"
                                        :checked="form.enabled === 1"
                                        class="h-4 w-4 text-blue-600"
                                        @change="form.enabled = 1"
                                    />
                                    <span>启用</span>
                                </label>
                                <label class="flex cursor-pointer items-center gap-2">
                                    <input
                                        type="radio"
                                        :value="0"
                                        :checked="form.enabled === 0"
                                        class="h-4 w-4 text-blue-600"
                                        @change="form.enabled = 0"
                                    />
                                    <span>停用</span>
                                </label>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- ─── Card 2: 菜单权限 ─── -->
                <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-5 flex items-center justify-between gap-4">
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">菜单权限</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                控制该角色在管理后台可见的功能模块。
                            </p>
                        </div>
                        <span
                            class="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600"
                        >
                            {{ form.menuPermissions.length }} 项已勾选
                        </span>
                    </div>
                    <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
                        <div
                            v-for="group in ADMIN_MENU_PERMISSION_GROUPS"
                            :key="group.id"
                            class="rounded-xl border border-slate-200 bg-slate-50 p-3"
                        >
                            <div class="mb-2 flex items-center justify-between gap-3">
                                <h5 class="text-sm font-semibold text-slate-700">
                                    {{ group.label }}
                                </h5>
                                <label
                                    class="inline-flex items-center gap-1.5 text-xs text-slate-500"
                                >
                                    <input
                                        type="checkbox"
                                        :checked="isGroupFullySelected(group.options)"
                                        class="h-3.5 w-3.5 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                        @change="
                                            toggleGroupPermissions(
                                                group.options,
                                                $event.target.checked
                                            )
                                        "
                                    />
                                    全选
                                </label>
                            </div>
                            <div class="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
                                <label
                                    v-for="option in group.options"
                                    :key="option.key"
                                    class="inline-flex items-center gap-2 text-sm text-slate-700"
                                >
                                    <input
                                        type="checkbox"
                                        :checked="hasMenuPermission(option.key)"
                                        class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                        @change="
                                            toggleMenuPermission(option.key, $event.target.checked)
                                        "
                                    />
                                    <span>{{ option.label }}</span>
                                </label>
                            </div>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>
