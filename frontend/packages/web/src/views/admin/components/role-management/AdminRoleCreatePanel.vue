<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { createRole } from '@/api/roles';
import { clearUserSession } from '@/composables/useCurrentUser';
import { showToast } from '@/composables/useToast';
import { ADMIN_MENU_PERMISSION_GROUPS } from '@/model/admin-menu-permissions';
import { ROUTE_PATHS } from '@/router/routePaths';

const emit = defineEmits(['back', 'saved']);

const router = useRouter();
const saving = ref(false);
const submitError = ref('');
const formErrors = reactive({});
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

function clearFieldError(field) {
    if (formErrors[field]) {
        delete formErrors[field];
    }
    submitError.value = '';
}

function hasMenuPermission(key) {
    return form.menuPermissions.includes(key);
}

function toggleMenuPermission(key, checked) {
    const next = new Set(form.menuPermissions);
    if (checked) {
        next.add(key);
    } else {
        next.delete(key);
    }
    form.menuPermissions = Array.from(next);
}

function isGroupFullySelected(options) {
    return (
        Array.isArray(options) &&
        options.length > 0 &&
        options.every(option => hasMenuPermission(option.key))
    );
}

function toggleGroupPermissions(options, checked) {
    const next = new Set(form.menuPermissions);
    for (const option of options) {
        if (checked) {
            next.add(option.key);
        } else {
            next.delete(option.key);
        }
    }
    form.menuPermissions = Array.from(next);
}

function validateForm() {
    Object.keys(formErrors).forEach(key => delete formErrors[key]);
    if (!form.roleCode.trim()) {
        formErrors.roleCode = '角色编码不能为空';
    }
    if (!form.roleName.trim()) {
        formErrors.roleName = '角色名称不能为空';
    }
    submitError.value = '';
    return Object.keys(formErrors).length === 0;
}

async function handleSave() {
    if (saving.value || !validateForm()) {
        return;
    }
    saving.value = true;
    try {
        await createRole(
            {
                roleCode: form.roleCode.trim(),
                roleName: form.roleName.trim(),
                description: form.description.trim() || null,
                enabled: form.enabled,
                menuPermissions: [...form.menuPermissions],
            },
            handleUnauthorized
        );
        showToast('角色已创建', 'success');
        emit('saved');
    } catch (error) {
        submitError.value = error?.message || '创建角色失败';
    } finally {
        saving.value = false;
    }
}
</script>

<template>
    <section
        class="flex h-full min-h-0 flex-col bg-slate-100"
        data-component="AdminRoleCreatePanel"
    >
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-wrap items-start justify-between gap-4">
                <div class="min-w-0">
                    <div class="flex items-center gap-3">
                        <button
                            type="button"
                            class="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 transition hover:bg-slate-50"
                            @click="emit('back')"
                        >
                            <span class="material-symbols-outlined text-[20px]">arrow_back</span>
                        </button>
                        <div>
                            <h2 class="text-3xl font-bold tracking-tight text-slate-900">
                                新增角色
                            </h2>
                            <p class="mt-2 text-sm text-slate-500">
                                创建角色基础信息并配置后台菜单权限。
                            </p>
                        </div>
                    </div>
                </div>
                <div class="flex items-center gap-3">
                    <button
                        type="button"
                        class="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="emit('back')"
                    >
                        返回列表
                    </button>
                    <button
                        type="button"
                        class="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving"
                        @click="handleSave"
                    >
                        {{ saving ? '创建中...' : '创建角色' }}
                    </button>
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-6 pt-6 pb-24">
            <p
                v-if="submitError"
                class="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
            >
                {{ submitError }}
            </p>

            <div class="space-y-5">
                <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-5">
                        <h3 class="text-lg font-bold text-slate-900">基础信息</h3>
                        <p class="mt-1 text-sm text-slate-500">角色编码、名称、描述和启用状态。</p>
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
                                :class="[
                                    'w-full rounded-lg border px-3 py-2 outline-none transition focus:ring-2',
                                    formErrors.roleCode
                                        ? 'border-red-300 focus:border-red-400 focus:ring-red-100'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-blue-100',
                                ]"
                                @input="clearFieldError('roleCode')"
                            />
                            <span v-if="formErrors.roleCode" class="text-xs text-red-500">
                                {{ formErrors.roleCode }}
                            </span>
                        </label>
                        <label class="space-y-1.5 text-sm text-slate-600">
                            <span class="font-medium"
                                >角色名称 <span class="text-red-500">*</span></span
                            >
                            <input
                                v-model="form.roleName"
                                type="text"
                                placeholder="请输入角色名称"
                                :class="[
                                    'w-full rounded-lg border px-3 py-2 outline-none transition focus:ring-2',
                                    formErrors.roleName
                                        ? 'border-red-300 focus:border-red-400 focus:ring-red-100'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-blue-100',
                                ]"
                                @input="clearFieldError('roleName')"
                            />
                            <span v-if="formErrors.roleName" class="text-xs text-red-500">
                                {{ formErrors.roleName }}
                            </span>
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
                                        :checked="form.enabled === 1"
                                        class="h-4 w-4 text-blue-600"
                                        @change="form.enabled = 1"
                                    />
                                    <span>启用</span>
                                </label>
                                <label class="flex cursor-pointer items-center gap-2">
                                    <input
                                        type="radio"
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
