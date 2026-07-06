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
                placeholder="请输入邮箱"
                class="w-full rounded-xl border border-slate-200 bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                @input="clearFieldError('email')"
            />
            <p v-if="formErrors.email" class="text-xs text-red-500">{{ formErrors.email }}</p>
        </div>
    </div>
</template>
