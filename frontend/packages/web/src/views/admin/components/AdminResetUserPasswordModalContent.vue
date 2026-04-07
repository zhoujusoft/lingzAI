<script setup>
import { computed } from 'vue';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const formErrors = computed(() => props.context.formErrors || {});

function clearFieldError(field) {
    if (props.context.formErrors && props.context.formErrors[field]) {
        delete props.context.formErrors[field];
    }
    if (props.context.submitError) {
        props.context.submitError = '';
    }
}
</script>

<template>
    <div class="space-y-5 p-8">
        <div
            v-if="context.submitError"
            class="rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-600"
        >
            {{ context.submitError }}
        </div>

        <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <p class="text-xs font-semibold tracking-[0.2em] text-slate-400">目标账号</p>
            <h3 class="mt-2 text-lg font-semibold text-slate-900">{{ context.name || '-' }}</h3>
            <p class="mt-1 text-sm text-slate-500">{{ context.account || '-' }}</p>
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                新密码 <span class="text-red-500">*</span>
            </label>
            <input
                v-model="context.password"
                type="password"
                placeholder="请输入新密码"
                autocomplete="new-password"
                name="reset-user-password"
                :class="[
                    'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                    formErrors.password
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                ]"
                @input="clearFieldError('password')"
            >
            <p class="text-xs text-slate-400">密码至少6位，建议包含字母、数字、符号中的两种</p>
            <p v-if="formErrors.password" class="text-xs text-red-500">{{ formErrors.password }}</p>
        </div>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                确认新密码 <span class="text-red-500">*</span>
            </label>
            <input
                v-model="context.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                autocomplete="new-password"
                name="reset-user-password-confirm"
                :class="[
                    'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                    formErrors.confirmPassword
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                ]"
                @input="clearFieldError('confirmPassword')"
            >
            <p v-if="formErrors.confirmPassword" class="text-xs text-red-500">
                {{ formErrors.confirmPassword }}
            </p>
        </div>
    </div>
</template>
