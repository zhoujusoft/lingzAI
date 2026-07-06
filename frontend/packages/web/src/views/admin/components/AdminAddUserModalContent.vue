<script setup>
import { computed, onMounted, ref } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import { USER_TYPES } from '@/model/enums/user-type';
import { listRoles } from '@/api/roles';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    mode: {
        type: String,
        default: 'create',
    },
});

const userTypeOptions = [
    { label: '普通用户', value: USER_TYPES.NORMAL },
    { label: '管理员', value: USER_TYPES.ADMIN },
];

const roles = ref([]);
const roleOptions = computed(() => {
    // 使用特殊值 'none' 表示不绑定角色，避免 null 值在 Headless UI 中的兼容问题
    const options = [{ label: '不绑定角色', value: 'none' }];
    for (const role of roles.value) {
        options.push({
            label: role.roleName,
            value: role.id,
        });
    }
    return options;
});

// 内部选中的角色 ID（可能是 'none' 或实际 ID）
const internalRoleId = computed({
    get() {
        // 如果 context.roleId 是 null/undefined，返回 'none'
        return props.context.roleId ?? 'none';
    },
    set(value) {
        // 如果选择的是 'none'，设置为 null
        props.context.roleId = value === 'none' ? null : value;
    },
});

const selectedUserType = computed({
    get() {
        return props.context.userType;
    },
    set(value) {
        props.context.userType = value;
    },
});

const selectedRoleId = internalRoleId;

const formErrors = computed(() => props.context.formErrors || {});
const isCreateMode = computed(() => props.mode === 'create');
const isAccountReadonly = computed(() => props.mode !== 'create');

function clearFieldError(field) {
    if (props.context.formErrors && props.context.formErrors[field]) {
        delete props.context.formErrors[field];
    }
    if (props.context.submitError) {
        props.context.submitError = '';
    }
}

async function loadRoles() {
    try {
        const data = await listRoles({ pageSize: 1000 });
        roles.value = Array.isArray(data?.items) ? data.items.filter(r => r.enabled === 1) : [];
    } catch (error) {
        roles.value = [];
    }
}

onMounted(() => {
    loadRoles();
});
</script>

<template>
    <div class="space-y-5 p-8">
        <div
            v-if="context.submitError"
            class="rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-600"
        >
            {{ context.submitError }}
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                姓名 <span class="text-red-500">*</span>
            </label>
            <input
                v-model="context.name"
                type="text"
                placeholder="请输入姓名"
                :class="[
                    'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                    formErrors.name
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                ]"
                @input="clearFieldError('name')"
            />
            <p v-if="formErrors.name" class="text-xs text-red-500">{{ formErrors.name }}</p>
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                登录名
                <span v-if="isCreateMode" class="text-red-500">*</span>
            </label>
            <input
                v-model="context.account"
                type="text"
                placeholder="请输入登录名"
                autocomplete="off"
                :name="isCreateMode ? 'create-user-account' : 'edit-user-account'"
                :class="[
                    'w-full rounded-xl border px-4 py-3 text-sm outline-none transition-all placeholder:text-slate-400',
                    isAccountReadonly
                        ? 'cursor-not-allowed border-slate-200 bg-slate-100 text-slate-500'
                        : 'bg-[#f8fafc] text-slate-700 focus:border-primary focus:ring-2 focus:ring-primary/20',
                    formErrors.account
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : '',
                ]"
                :disabled="isAccountReadonly"
                @input="clearFieldError('account')"
            />
            <p v-if="isAccountReadonly" class="text-xs text-slate-400">登录名不可更改</p>
            <p v-if="formErrors.account" class="text-xs text-red-500">{{ formErrors.account }}</p>
        </div>

        <div v-if="isCreateMode" class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                密码 <span class="text-red-500">*</span>
            </label>
            <input
                v-model="context.password"
                type="password"
                placeholder="请输入密码"
                autocomplete="new-password"
                name="create-user-password"
                :class="[
                    'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                    formErrors.password
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                ]"
                @input="clearFieldError('password')"
            />
            <p class="text-xs text-slate-400">密码至少6位，建议包含字母、数字、符号中的两种</p>
            <p v-if="formErrors.password" class="text-xs text-red-500">{{ formErrors.password }}</p>
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">手机号</label>
            <input
                v-model="context.mobile"
                type="text"
                placeholder="请输入手机号"
                class="w-full rounded-xl border border-slate-200 bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                @input="clearFieldError('mobile')"
            />
            <p v-if="formErrors.mobile" class="text-xs text-red-500">{{ formErrors.mobile }}</p>
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">邮箱</label>
            <input
                v-model="context.email"
                type="email"
                placeholder="请输入邮箱地址"
                class="w-full rounded-xl border border-slate-200 bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                @input="clearFieldError('email')"
            />
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">用户类型</label>
            <AppSelect
                v-model="selectedUserType"
                :options="userTypeOptions"
                placeholder="请选择用户类型"
                button-class="bg-slate-50 shadow-none hover:bg-white"
            />
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">角色</label>
            <AppSelect
                v-model="selectedRoleId"
                :options="roleOptions"
                placeholder="请选择角色"
                button-class="bg-slate-50 shadow-none hover:bg-white"
                menu-placement="top"
            />
        </div>
    </div>
</template>
