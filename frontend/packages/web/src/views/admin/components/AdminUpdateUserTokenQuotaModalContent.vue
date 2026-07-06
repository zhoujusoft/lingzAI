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
            <p class="mt-3 text-xs text-slate-500">
                当前已发放 {{ context.currentGrantedText || '0' }} / 已用
                {{ context.currentConsumedText || '0' }} / 剩余
                {{ context.currentRemainingText || '0' }}
            </p>
        </div>

        <label
            class="flex items-start gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3"
        >
            <input
                v-model="context.unlimited"
                type="checkbox"
                class="mt-1 h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary"
                @change="clearFieldError('remainingTokens')"
            />
            <div>
                <p class="text-sm font-semibold text-slate-700">无限制额度</p>
                <p class="mt-1 text-xs text-slate-500">
                    开启后该用户不会再因为 token 配额不足而被拦截，usage 统计仍会继续记录。
                </p>
            </div>
        </label>

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                剩余额度 <span class="text-red-500">*</span>
            </label>
            <input
                v-model.number="context.remainingTokens"
                type="number"
                min="0"
                step="1"
                placeholder="请输入用户剩余 token"
                :class="[
                    'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                    formErrors.remainingTokens
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                ]"
                @input="clearFieldError('remainingTokens')"
            />
            <p class="text-xs text-slate-400">
                保存后会直接重算该用户账户：`总额度 = 已用 + 剩余`。
            </p>
            <p v-if="formErrors.remainingTokens" class="text-xs text-red-500">
                {{ formErrors.remainingTokens }}
            </p>
        </div>
    </div>
</template>
