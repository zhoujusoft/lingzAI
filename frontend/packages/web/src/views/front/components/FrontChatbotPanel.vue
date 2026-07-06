<template>
    <section class="flex h-full min-h-0 flex-col">
        <div v-if="loading" class="flex flex-1 items-center justify-center text-sm text-slate-500">
            chatbot 页面加载中...
        </div>
        <div
            v-else-if="loadError"
            class="mx-auto my-12 w-full max-w-3xl rounded-2xl border border-rose-200 bg-rose-50 px-6 py-5 text-sm text-rose-700"
        >
            {{ loadError }}
        </div>
        <FrontChatWorkspace
            v-else-if="adapter"
            :adapter="adapter"
            :header-title="appContext?.displayName || '技能对话应用'"
            :header-status-text="appContext?.description || '仅支持当前发布技能会话'"
            :empty-title="appContext?.displayName || '技能对话应用'"
            :empty-description="
                appContext?.description || '围绕当前发布技能发起对话，历史会话按应用隔离。'
            "
            draft-placeholder="请输入你的问题..."
            :show-knowledge-select="false"
            :enable-attachments="true"
            :show-file-list-panel="false"
            :default-knowledge="''"
            :session-storage-key="sessionStorageKey"
            :show-sidebar-toggle="false"
            :show-sidebar-assistant-header="true"
            :chat-max-width="920"
            new-chat-label="新建一个会话"
            new-chat-icon="add"
            :show-assistant-edit-button="false"
        />
    </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { exchangeChatbotPassport, getChatbotContext, getChatbotPublishStatus } from '@/api/skills';
import FrontChatWorkspace from '@/views/front/components/front-chat/FrontChatWorkspace.vue';
import { createChatbotSkillChatAdapter } from '@/views/front/components/front-chat/adapters/chatbotSkillChatAdapter';

const route = useRoute();
const loading = ref(true);
const loadError = ref('');
const appContext = ref(null);
const adapter = ref(null);

const appCode = computed(() => String(route.params.appCode || '').trim());
const passportStorageKey = computed(() => `chatbot-app-passport:${appCode.value}`);
const sessionStorageKey = computed(() => `chatbot-session:${appCode.value}`);

async function initialize() {
    loading.value = true;
    loadError.value = '';
    adapter.value = null;
    try {
        if (!appCode.value) {
            throw new Error('访问地址缺少 AppCode。');
        }
        const status = await getChatbotPublishStatus(appCode.value);
        if (!status?.published) {
            throw new Error('当前技能未发布或访问地址无效。');
        }

        let passport = window.localStorage.getItem(passportStorageKey.value) || '';
        if (!passport) {
            const exchange = await exchangeChatbotPassport(appCode.value);
            passport = String(exchange?.passport || '').trim();
            if (!passport) {
                throw new Error('获取 X-App-Passport 失败，请稍后重试。');
            }
            window.localStorage.setItem(passportStorageKey.value, passport);
        }

        let context;
        try {
            context = await getChatbotContext(appCode.value, passport);
        } catch (error) {
            window.localStorage.removeItem(passportStorageKey.value);
            const exchange = await exchangeChatbotPassport(appCode.value);
            passport = String(exchange?.passport || '').trim();
            if (!passport) {
                throw error;
            }
            window.localStorage.setItem(passportStorageKey.value, passport);
            context = await getChatbotContext(appCode.value, passport);
        }
        appContext.value = {
            appCode: String(context?.appCode || appCode.value),
            skillId: context?.skillId ?? null,
            displayName: context?.displayName || '',
            description: context?.description || '',
        };

        adapter.value = createChatbotSkillChatAdapter({
            appCode: appCode.value,
            passport,
            sessionStorageKey: sessionStorageKey.value,
        });
    } catch (error) {
        loadError.value = error?.message || '当前技能应用不可访问，请确认已发布后重试。';
    } finally {
        loading.value = false;
    }
}

onMounted(() => {
    initialize();
});

watch(
    () => appCode.value,
    () => {
        initialize();
    }
);
</script>
