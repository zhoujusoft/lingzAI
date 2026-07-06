<script setup>
import { computed, ref } from 'vue';
import { alert } from '@/composables/useModal';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const activeMethod = ref('iframe');

const chatbotUrl = computed(() => String(props.context.chatbotUrl || '').trim());
const appCode = computed(() => String(props.context.appCode || '').trim());
const embedScriptUrl = computed(() => String(props.context.embedScriptUrl || '').trim());
const scriptCloseTag = '<' + '/script>';

const iframeCode = computed(() => {
    return `<iframe
  src="${chatbotUrl.value}"
  style="width: 100%; height: 100%; min-height: 700px;"
  frameborder="0"
  allow="microphone"
></iframe>`;
});

const embedJsCode = computed(() => {
    return `<script>
  window.chatbotConfig = {
    AppCode: '${appCode.value}'
  };
${scriptCloseTag}
<script
  src="${embedScriptUrl.value}"
  id="${appCode.value}"
  defer
>${scriptCloseTag}
<style>
  #skill-chatbot-bubble-button {
    background-color: #1C64F2 !important;
  }
  #skill-chatbot-bubble-window {
    width: 40rem !important;
    height: 50rem !important;
  }
</style>`;
});

const selectedCode = computed(() => {
    return activeMethod.value === 'iframe' ? iframeCode.value : embedJsCode.value;
});

function setMethod(method) {
    activeMethod.value = method;
}

function copyTextWithExecCommand(text) {
    if (!text) {
        return false;
    }
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);
    let copied = false;
    try {
        copied = document.execCommand('copy');
    } catch (error) {
        copied = false;
    }
    document.body.removeChild(textarea);
    return copied;
}

async function handleCopyCode() {
    if (copyTextWithExecCommand(selectedCode.value)) {
        await alert({
            title: '复制成功',
            message: '嵌入代码已复制到剪贴板。',
        });
        return;
    }
    await alert({
        title: '复制失败',
        message: '浏览器限制导致复制失败，请手动复制代码。',
    });
}
</script>

<template>
    <div class="max-h-[76vh] overflow-y-auto rounded-[28px] bg-white p-6">
        <p class="text-sm text-slate-600">选择一种方式将聊天应用嵌入到你的网站中</p>

        <div class="mt-5 grid gap-3 md:grid-cols-2">
            <button
                type="button"
                class="rounded-2xl border p-4 text-left transition"
                :class="
                    activeMethod === 'iframe'
                        ? 'border-blue-500 bg-blue-50/50'
                        : 'border-slate-200 bg-slate-50 hover:border-slate-300'
                "
                @click="setMethod('iframe')"
            >
                <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-[20px] text-slate-500">web</span>
                    <span class="text-sm font-semibold text-slate-900">iframe 嵌入</span>
                </div>
                <p class="mt-2 text-xs leading-5 text-slate-500">
                    以页面区域嵌入，适合固定位置展示聊天窗口。
                </p>
            </button>

            <button
                type="button"
                class="rounded-2xl border p-4 text-left transition"
                :class="
                    activeMethod === 'embed-js'
                        ? 'border-blue-500 bg-blue-50/50'
                        : 'border-slate-200 bg-slate-50 hover:border-slate-300'
                "
                @click="setMethod('embed-js')"
            >
                <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-[20px] text-slate-500"
                        >code_blocks</span
                    >
                    <span class="text-sm font-semibold text-slate-900">embed.js 嵌入</span>
                </div>
                <p class="mt-2 text-xs leading-5 text-slate-500">
                    以浮动按钮方式嵌入，点击图标弹出对话窗口。
                </p>
            </button>
        </div>

        <div class="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-3">
            <div class="flex items-center justify-between gap-3">
                <p class="text-sm font-medium text-slate-600">将以下代码嵌入到你的网站中</p>
                <button
                    type="button"
                    class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-200 hover:text-slate-700"
                    title="复制代码"
                    @click="handleCopyCode"
                >
                    <span class="material-symbols-outlined text-[18px]">content_copy</span>
                </button>
            </div>
            <pre
                class="mt-2 overflow-x-auto rounded-lg bg-transparent p-2 text-xs leading-5 text-slate-700"
            ><code>{{ selectedCode }}</code></pre>
        </div>
    </div>
</template>
