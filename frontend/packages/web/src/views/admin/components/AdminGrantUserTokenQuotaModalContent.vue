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

        <div class="space-y-2">
            <label class="block text-sm font-semibold text-slate-700">
                发放额度 <span class="text-red-500">*</span>
            </label>
            <input
                v-model.number="context.grantTokens"
                type="number"
                min="1"
                step="1"
                placeholder="请输入本次发放的 token 数量"
                :class="[
                    'w-full rounded-xl border bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:ring-2',
                    formErrors.grantTokens
                        ? 'border-red-300 focus:border-red-400 focus:ring-red-200'
                        : 'border-slate-200 focus:border-primary focus:ring-primary/20',
                ]"
                @input="clearFieldError('grantTokens')"
            />
            <p class="text-xs text-slate-400">发放后会直接累加到用户的“已发放”和“剩余”额度中。</p>
            <p v-if="formErrors.grantTokens" class="text-xs text-red-500">
                {{ formErrors.grantTokens }}
            </p>
        </div>
    </div>
</template>
