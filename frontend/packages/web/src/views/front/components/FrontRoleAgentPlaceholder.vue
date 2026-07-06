<script setup>
import { computed } from 'vue';
import { marked } from 'marked';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import { isImageSource } from '@/utils/iconDisplay';

const props = defineProps({
    roleName: {
        type: String,
        default: '',
    },
    roleCode: {
        type: String,
        default: '',
    },
    agent: {
        type: Object,
        default: null,
    },
});

const router = useRouter();

const agentName = computed(() => props.agent?.displayName || props.agent?.agentName || '');
const agentDescription = computed(() => props.agent?.description || '');
const agentIcon = computed(() => props.agent?.avatarUrl || props.agent?.icon || '');
const isImageAgentIcon = computed(() => isImageSource(agentIcon.value));

const openingMessageHtml = computed(() => {
    const message = props.agent?.openingMessage;
    if (!message) return '';
    return marked(message);
});

function openConfig() {
    router.push(ROUTE_PATHS.frontAgentConfig);
}
</script>

<template>
    <section
        class="front-role-agent-placeholder flex h-full flex-col items-center justify-center bg-gradient-to-b from-slate-50 to-white p-8"
        data-component="FrontRoleAgentPlaceholder"
    >
        <div class="max-w-2xl w-full">
            <div class="text-center mb-8">
                <div
                    class="inline-flex items-center justify-center w-20 h-20 rounded-full bg-blue-100 mb-4 text-4xl text-blue-600"
                >
                    <img
                        v-if="isImageAgentIcon"
                        :src="agentIcon"
                        alt="assistant avatar"
                        class="h-full w-full rounded-full object-cover"
                    />
                    <span v-else-if="agentIcon">{{ agentIcon }}</span>
                    <span v-else class="material-symbols-outlined text-4xl">smart_toy</span>
                </div>
                <h1 class="text-2xl font-bold text-slate-900 mb-2">
                    {{ agentName || '专属助手' }}
                </h1>
                <p v-if="roleName" class="text-sm text-slate-500">角色身份：{{ roleName }}</p>
            </div>

            <div v-if="agentDescription" class="mb-6 text-center">
                <p class="text-slate-600">{{ agentDescription }}</p>
            </div>

            <div
                v-if="openingMessageHtml"
                class="prose prose-slate prose-sm max-w-none bg-white rounded-xl border border-slate-200 p-6 shadow-sm"
                v-html="openingMessageHtml"
            ></div>

            <div class="mt-8 flex flex-col items-center gap-3 text-center">
                <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50"
                    @click="openConfig"
                >
                    <span class="material-symbols-outlined text-base">settings</span>
                    <span>管理我的配置</span>
                </button>
                <div
                    class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-amber-50 text-amber-700 text-sm"
                >
                    <span class="material-symbols-outlined text-base">construction</span>
                    <span>专属 Agent 能力建设中</span>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.front-role-agent-placeholder :deep(.prose) {
    --tw-prose-body: theme('colors.slate.600');
    --tw-prose-headings: theme('colors.slate.900');
    --tw-prose-links: theme('colors.blue.600');
    --tw-prose-bullets: theme('colors.slate.400');
}
</style>
