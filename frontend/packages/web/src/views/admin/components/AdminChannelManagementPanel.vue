<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import BaseModal from '@/components/feedback/BaseModal.vue';
import { alert } from '@/composables/useModal';
import { showToast } from '@/composables/useToast';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    beginWechatLogin,
    beginDingtalkRegister,
    createChannelConfig,
    getDingtalkRegisterStatus,
    getWecomRuntimeStatus,
    getMyChannelBinding,
    getWechatLoginStatus,
    listChannelConfigs,
    pollWechatChannel,
    saveMyChannelBinding,
    saveMyWecomBinding,
    updateChannelConfig,
} from '@/api/channels';
import { listSkillCatalogs } from '@/api/skills';
import { ROUTE_PATHS } from '@/router/routePaths';
import { useRouter } from 'vue-router';

const router = useRouter();

const CHANNEL_PRESETS = Object.freeze([
    {
        type: 'weixin',
        title: '微信 iLink',
        subtitle: '微信个人号渠道。',
        description: '微信渠道接入配置',
        accentClass: 'from-emerald-500/15 via-lime-400/10 to-white',
        iconBgClass: 'bg-emerald-500 text-white',
        icon: 'forum',
        defaultName: '微信 iLink',
        defaultRouteType: 'GENERAL_CHAT',
        defaultConfigJson:
            '{\n  "autoPoll": true,\n  "pollIntervalMs": 3000,\n  "heartbeatEnabled": true\n}',
    },
    {
        type: 'wecom',
        title: '企业微信机器人',
        subtitle: '企业微信智能机器人渠道。',
        description: '企业微信渠道接入配置',
        accentClass: 'from-orange-500/15 via-amber-400/10 to-white',
        iconBgClass: 'bg-orange-500 text-white',
        icon: 'hub',
        defaultName: '企业微信机器人',
        defaultRouteType: 'GENERAL_CHAT',
        defaultConfigJson: '{\n  "welcome_text": "",\n  "max_reconnect_attempts": -1\n}',
    },
    {
        type: 'dingtalk',
        title: '钉钉机器人',
        subtitle: '钉钉 Stream 模式机器人渠道。',
        description: '钉钉渠道接入配置',
        accentClass: 'from-sky-500/15 via-cyan-400/10 to-white',
        iconBgClass: 'bg-sky-500 text-white',
        icon: 'mark_unread_chat_alt',
        defaultName: '钉钉机器人',
        defaultRouteType: 'GENERAL_CHAT',
        defaultConfigJson:
            '{\n  "connection_mode": "stream",\n  "clientId": "",\n  "clientSecret": "",\n  "robotCode": "",\n  "replyTitle": "灵洲智能体",\n  "consumeThreads": 2,\n  "connectTimeoutMs": 30000,\n  "duplicateTtlMs": 600000,\n  "replyUnsupported": false\n}',
    },
    {
        type: 'webchat',
        title: 'WebChat',
        subtitle: '站内聊天入口预置配置',
        description: 'WebChat 渠道接入配置',
        accentClass: 'from-blue-500/15 via-blue-400/10 to-white',
        iconBgClass: 'bg-blue-500 text-white',
        icon: 'chat',
        defaultName: 'WebChat',
        defaultRouteType: 'GENERAL_CHAT',
        defaultConfigJson:
            '{\n  "theme": "default",\n  "welcomeMessage": "你好，有什么可以帮你？"\n}',
    },
]);

const loading = ref(false);
const saving = ref(false);
const polling = ref(false);
const weixinQrCodeLoading = ref(false);
const wecomAuthLoading = ref(false);
const wecomAuthStage = ref('IDLE');
const dingtalkRegisterLoading = ref(false);
const dingtalkRegisterStatus = ref('');
const dingtalkRegisterQrCode = ref('');
const dingtalkRegisterUrl = ref('');
const loadError = ref('');
const channels = ref([]);
const skills = ref([]);
const myBindings = ref({});
const activeType = ref('weixin');
const showEditor = ref(false);
const weixinQrCode = ref('');
const weixinStatus = ref('NOT_STARTED');
const wecomStatus = ref('NOT_STARTED');
const statusTimer = ref(null);
const wecomStatusTimer = ref(null);
const dingtalkRegisterTimer = ref(null);
const editorTab = ref('weixin-auth');
const WECOM_SDK_URL = 'https://wwcdn.weixin.qq.com/node/wework/js/wecom-aibot-sdk@0.1.0.min.js';
const WECOM_SOURCE = 'lingzhou-agent';
let wecomSdkLoaded = false;

const form = reactive({
    id: null,
    presetType: 'weixin',
    name: '',
    skillId: '',
    botPrefix: '',
    enabled: true,
    description: '',
    configJson: '{}',
});

const activeBlock = computed(
    () => blocks.value.find(item => item.type === activeType.value) || blocks.value[0]
);

const blocks = computed(() =>
    CHANNEL_PRESETS.map(preset => {
        const config = channels.value.find(item => item.channelType === preset.type) || null;
        return {
            ...preset,
            config,
            initialized: Boolean(config),
            enabled: Boolean(config?.enabled),
        };
    })
);

const skillOptions = computed(() =>
    skills.value.map(item => ({
        value: item.id,
        label: item.displayName || item.name || `技能 #${item.id}`,
    }))
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function prettyJson(value) {
    if (!value) {
        return '{\n  \n}';
    }
    if (typeof value === 'string') {
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch (error) {
            return value;
        }
    }
    return JSON.stringify(value, null, 2);
}

function normalizeConfigJson(value) {
    const raw = String(value || '').trim();
    if (!raw) {
        return '{}';
    }
    return JSON.stringify(JSON.parse(raw));
}

function parseConfigObject(value) {
    const raw = String(value || '').trim();
    if (!raw) {
        return {};
    }
    try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed === 'object' ? parsed : {};
    } catch (error) {
        return {};
    }
}

function ensureDingtalkFileConfig(config) {
    return {
        ...config,
        robotCode: String(config?.robotCode || config?.robot_code || '').trim(),
    };
}

const dingtalkConfigDraft = computed(() => parseConfigObject(form.configJson));

const dingtalkRobotCodeDraft = computed({
    get() {
        const config = dingtalkConfigDraft.value;
        return String(config.robotCode || config.robot_code || '').trim();
    },
    set(value) {
        const config = ensureDingtalkFileConfig(dingtalkConfigDraft.value);
        config.robotCode = String(value || '').trim();
        form.configJson = JSON.stringify(config, null, 2);
    },
});

function hasWecomRuntimeBinding(channelId) {
    if (!channelId) {
        return false;
    }
    return Boolean(resolveBinding(channelId)?.runtimeBound);
}

function hasDingtalkCredentialDraft() {
    const config = parseConfigObject(form.configJson);
    return Boolean(
        String(config.clientId || '').trim() && String(config.clientSecret || '').trim()
    );
}

function hasDingtalkRobotCodeDraft() {
    return Boolean(dingtalkRobotCodeDraft.value);
}

function formatRouteType(type) {
    return String(type || '').toUpperCase() === 'SKILL_CHAT' ? '技能对话' : '普通对话';
}

function formatRouteTarget(config) {
    if (config?.routeType === 'SKILL_CHAT' && config?.routeTargetId) {
        const skill = skills.value.find(item => Number(item.id) === Number(config.routeTargetId));
        return skill?.displayName || skill?.name || `技能 #${config.routeTargetId}`;
    }
    return '普通对话';
}

function resolveBinding(channelId) {
    return channelId == null ? null : myBindings.value[channelId] || null;
}

function selectBlock(type) {
    activeType.value = type;
    stopWechatStatusPolling();
    stopWecomStatusPolling();
    stopDingtalkRegisterPolling();
    if (type === 'weixin') {
        refreshWechatStatus();
        return;
    }
    if (type === 'wecom') {
        refreshWecomStatus();
        startWecomStatusPolling();
        return;
    }
    if (type !== 'weixin') {
        weixinStatus.value = 'NOT_STARTED';
        weixinQrCode.value = '';
    }
    if (type !== 'wecom') {
        wecomStatus.value = 'NOT_STARTED';
    }
}

async function openConfigModal(block) {
    stopDingtalkRegisterPolling();
    const preset = CHANNEL_PRESETS.find(item => item.type === block.type);
    const config = block.config;
    form.id = config?.id ?? null;
    form.presetType = block.type;
    form.name = config?.name || preset?.defaultName || block.title;
    form.skillId = '';
    form.botPrefix = config?.botPrefix || '';
    form.enabled = config?.enabled == null ? true : Boolean(config.enabled);
    form.description = config?.description || block.description || '';
    form.configJson = prettyJson(config?.configJson || preset?.defaultConfigJson || '{}');
    if (block.type === 'weixin') {
        editorTab.value = 'weixin-auth';
    } else if (block.type === 'wecom') {
        editorTab.value = 'wecom-auth';
        wecomAuthStage.value = form.id && hasWecomRuntimeBinding(form.id) ? 'AUTHORIZED' : 'IDLE';
    } else if (block.type === 'dingtalk') {
        editorTab.value = 'dingtalk-auth';
    } else {
        editorTab.value = 'config-json';
    }
    showEditor.value = true;
    weixinQrCode.value = '';
    dingtalkRegisterQrCode.value = '';
    dingtalkRegisterStatus.value = '';
    dingtalkRegisterUrl.value = '';
    dingtalkRegisterLoading.value = false;
    if (form.id) {
        try {
            const binding = await getMyChannelBinding(form.id, handleUnauthorized);
            myBindings.value = {
                ...myBindings.value,
                [form.id]: binding,
            };
            form.skillId =
                binding?.routeType === 'SKILL_CHAT' && binding?.routeTargetId
                    ? binding.routeTargetId
                    : '';
            if (block.type === 'wecom') {
                wecomAuthStage.value = binding?.runtimeBound ? 'AUTHORIZED' : 'IDLE';
            }
        } catch (error) {
            // ignore binding load failure to avoid blocking config dialog
        }
    }
    if (block.type === 'weixin') {
        refreshWechatStatus();
    }
    if (block.type === 'wecom') {
        refreshWecomStatus();
        startWecomStatusPolling();
    }
}

async function loadReferenceData() {
    try {
        const [skillData] = await Promise.all([listSkillCatalogs({}, handleUnauthorized)]);
        skills.value = Array.isArray(skillData) ? skillData : [];
    } catch (error) {
        await alert({
            title: '基础数据加载失败',
            message: error?.message || '技能列表加载失败',
        });
    }
}

async function loadMyBindings() {
    const nextBindings = {};
    for (const channel of channels.value) {
        if (!channel?.id) {
            continue;
        }
        try {
            const binding = await getMyChannelBinding(channel.id, handleUnauthorized);
            nextBindings[channel.id] = binding;
        } catch (error) {
            // ignore per-channel binding failure to keep page usable
        }
    }
    myBindings.value = nextBindings;
}

async function loadChannels() {
    loading.value = true;
    loadError.value = '';
    try {
        const data = await listChannelConfigs(handleUnauthorized);
        const records = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : [];
        channels.value = records.filter(item =>
            ['weixin', 'wecom', 'dingtalk', 'webchat'].includes(item.channelType)
        );
    } catch (error) {
        channels.value = [];
        loadError.value = error?.message || '渠道配置加载失败';
    } finally {
        loading.value = false;
    }
}

async function refreshAll() {
    await Promise.all([loadReferenceData(), loadChannels()]);
    await loadMyBindings();
    await Promise.all([refreshWechatStatus(), refreshWecomStatus()]);
}

async function saveChannel() {
    let payload;
    try {
        payload = buildChannelPayload();
    } catch (error) {
        await alert({
            title: '配置格式错误',
            message: 'configJson 需要是合法 JSON',
        });
        return;
    }

    if (!payload.name) {
        await alert({
            title: '信息不完整',
            message: '请填写实例名称',
        });
        return;
    }

    saving.value = true;
    try {
        await persistCurrentChannel(payload);
        stopDingtalkRegisterPolling();
        closeEditor();
        await loadChannels();
        await loadMyBindings();
        selectBlock(form.presetType);
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '渠道配置保存失败',
        });
    } finally {
        saving.value = false;
    }
}

function buildChannelPayload(options = {}) {
    const forceEnabled = options.forceEnabled;
    return {
        name: form.name.trim(),
        channelType: form.presetType,
        routeType: 'GENERAL_CHAT',
        routeTargetId: null,
        botPrefix: form.botPrefix.trim() || null,
        enabled: forceEnabled == null ? Boolean(form.enabled) : Boolean(forceEnabled),
        description: form.description.trim() || null,
        configJson: normalizeConfigJson(form.configJson),
    };
}

async function persistCurrentChannel(payload) {
    let savedChannelId = form.id;
    if (form.id) {
        await updateChannelConfig(form.id, payload, handleUnauthorized);
    } else {
        const created = await createChannelConfig(payload, handleUnauthorized);
        savedChannelId = created?.id || null;
    }
    if (savedChannelId) {
        form.id = savedChannelId;
        await saveMyChannelBinding(
            savedChannelId,
            {
                routeType: form.skillId === '' ? 'GENERAL_CHAT' : 'SKILL_CHAT',
                routeTargetId: form.skillId === '' ? null : Number(form.skillId),
            },
            handleUnauthorized
        );
    }
    return savedChannelId;
}

async function ensureDingtalkChannelReadyForRegister() {
    let payload;
    try {
        payload = buildChannelPayload({ forceEnabled: true });
    } catch (error) {
        await alert({
            title: '配置格式错误',
            message: 'configJson 需要是合法 JSON',
        });
        return false;
    }
    if (!payload.name) {
        await alert({
            title: '信息不完整',
            message: '请填写实例名称',
        });
        return false;
    }
    saving.value = true;
    try {
        const savedChannelId = await persistCurrentChannel(payload);
        if (!savedChannelId) {
            throw new Error('钉钉渠道保存失败');
        }
        form.enabled = true;
        await loadChannels();
        await loadMyBindings();
        return true;
    } catch (error) {
        await alert({
            title: '钉钉渠道保存失败',
            message: error?.message || '扫码前保存渠道失败',
        });
        return false;
    } finally {
        saving.value = false;
    }
}

async function saveDingtalkRegisteredCredential(clientId, clientSecret) {
    const config = {
        ...ensureDingtalkFileConfig(parseConfigObject(form.configJson)),
        clientId: String(clientId || '').trim(),
        clientSecret: String(clientSecret || '').trim(),
    };
    if (!String(config.replyTitle || '').trim()) {
        config.replyTitle = '灵洲智能体';
    }
    form.configJson = JSON.stringify(config, null, 2);
    form.enabled = true;
    const payload = buildChannelPayload({ forceEnabled: true });
    saving.value = true;
    try {
        const savedChannelId = await persistCurrentChannel(payload);
        if (!savedChannelId) {
            throw new Error('钉钉渠道保存失败');
        }
        await loadChannels();
        await loadMyBindings();
        showToast('钉钉授权完成，渠道已启用。', 'success');
        closeEditor();
        selectBlock('dingtalk');
    } catch (error) {
        await alert({
            title: '钉钉授权保存失败',
            message: error?.message || '钉钉应用凭证保存失败，请重新扫码。',
        });
    } finally {
        saving.value = false;
    }
}

async function startDingtalkRegister() {
    if (form.presetType !== 'dingtalk') {
        return;
    }
    const ready = await ensureDingtalkChannelReadyForRegister();
    if (!ready) {
        return;
    }
    stopDingtalkRegisterPolling();
    dingtalkRegisterLoading.value = true;
    dingtalkRegisterStatus.value = 'waiting';
    dingtalkRegisterQrCode.value = '';
    dingtalkRegisterUrl.value = '';
    try {
        const data = await beginDingtalkRegister(form.id, handleUnauthorized);
        const sessionId = data?.sessionId || data?.session_id || '';
        dingtalkRegisterUrl.value = data?.verificationUrl || data?.verification_url || '';
        if (!sessionId) {
            throw new Error('钉钉注册会话为空');
        }
        await pollDingtalkRegisterStatus(sessionId);
        dingtalkRegisterTimer.value = window.setInterval(() => {
            pollDingtalkRegisterStatus(sessionId);
        }, 2000);
    } catch (error) {
        dingtalkRegisterLoading.value = false;
        dingtalkRegisterStatus.value = 'error';
        await alert({
            title: '钉钉扫码注册失败',
            message: error?.message || '钉钉扫码注册启动失败',
        });
    }
}

async function openDingtalkRegisterShortcut() {
    if (form.presetType !== 'dingtalk') {
        return;
    }
    if (!dingtalkRegisterUrl.value) {
        await startDingtalkRegister();
    }
    if (!dingtalkRegisterUrl.value) {
        return;
    }
    window.open(dingtalkRegisterUrl.value, '_blank', 'noopener,noreferrer');
}

async function pollDingtalkRegisterStatus(sessionId) {
    if (form.presetType !== 'dingtalk' || !form.id || !sessionId) {
        stopDingtalkRegisterPolling();
        return;
    }
    try {
        const data = await getDingtalkRegisterStatus(form.id, sessionId, handleUnauthorized);
        const status = String(data?.status || 'waiting').toLowerCase();
        dingtalkRegisterStatus.value = status;
        const qrCode = data?.qrcode_img || data?.qrcode_url || '';
        if (qrCode) {
            dingtalkRegisterQrCode.value = normalizeWechatQrCode(qrCode);
            dingtalkRegisterLoading.value = false;
        }
        if (status === 'confirmed') {
            stopDingtalkRegisterPolling();
            dingtalkRegisterLoading.value = false;
            const clientId = data?.clientId || data?.client_id || '';
            const clientSecret = data?.clientSecret || data?.client_secret || '';
            if (clientId && clientSecret) {
                await saveDingtalkRegisteredCredential(clientId, clientSecret);
            }
            return;
        }
        if (status === 'expired' || status === 'denied') {
            stopDingtalkRegisterPolling();
            dingtalkRegisterLoading.value = false;
            await alert({
                title: status === 'expired' ? '二维码已过期' : '授权已取消',
                message: data?.error || '请重新发起钉钉扫码注册。',
            });
        }
    } catch (error) {
        // Transient network errors should not break the current scan session.
    }
}

async function openWechatLogin() {
    if (form.presetType !== 'weixin' || !form.id) {
        await alert({
            title: '请先配置微信实例',
            message: '先完成微信 iLink 的基础配置并保存，再执行扫码登录。',
        });
        return;
    }
    weixinQrCodeLoading.value = true;
    weixinQrCode.value = '';
    try {
        const data = await beginWechatLogin(form.id, handleUnauthorized);
        const imgContent =
            data?.qrcodeImageContent || data?.qrcode_img_content || data?.qrcode_img || '';
        weixinQrCode.value = normalizeWechatQrCode(imgContent);
        await refreshWechatStatus();
        startWechatStatusPolling();
    } catch (error) {
        await alert({
            title: '获取二维码失败',
            message: error?.message || '微信二维码获取失败',
        });
    } finally {
        weixinQrCodeLoading.value = false;
    }
}

async function pollWechatUpdates() {
    if (form.presetType !== 'weixin' || !form.id) {
        await alert({
            title: '微信实例未初始化',
            message: '请先保存微信配置。',
        });
        return;
    }
    polling.value = true;
    try {
        await pollWechatChannel(form.id, handleUnauthorized);
    } catch (error) {
        await alert({
            title: '拉取消息失败',
            message: error?.message || '微信消息拉取失败',
        });
    } finally {
        polling.value = false;
    }
}

async function refreshWechatStatus() {
    const weixinId =
        form.presetType === 'weixin' && form.id
            ? form.id
            : blocks.value.find(item => item.type === 'weixin')?.config?.id;
    if (!weixinId) {
        weixinStatus.value = 'NOT_CONFIGURED';
        return;
    }
    try {
        const data = await getWechatLoginStatus(weixinId, handleUnauthorized);
        weixinStatus.value = data?.loggedIn ? 'LOGGED_IN' : data?.status || 'UNKNOWN';
        if (weixinStatus.value === 'LOGGED_IN') {
            stopWechatStatusPolling();
            weixinQrCode.value = '';
        }
    } catch (error) {
        weixinStatus.value = 'ERROR';
    }
}

function loadWecomSDK() {
    return new Promise((resolve, reject) => {
        if (window.WecomAIBotSDK || wecomSdkLoaded) {
            resolve();
            return;
        }
        const script = document.createElement('script');
        script.src = WECOM_SDK_URL;
        script.async = true;
        script.onload = () => {
            wecomSdkLoaded = true;
            resolve();
        };
        script.onerror = () => reject(new Error('WeCom SDK 加载失败'));
        document.body.appendChild(script);
    });
}

async function handleWecomAuth() {
    if (form.presetType !== 'wecom' || !form.id) {
        await alert({
            title: '请先保存企业微信渠道',
            message: '需要先保存当前渠道实例，再按当前账号扫码绑定企业微信登录态。',
        });
        return;
    }
    wecomAuthStage.value = 'AWAITING_SCAN';
    wecomAuthLoading.value = true;
    try {
        await loadWecomSDK();
    } catch (error) {
        wecomAuthStage.value = 'FAILED';
        wecomAuthLoading.value = false;
        await alert({
            title: '企业微信授权初始化失败',
            message: '企业微信授权 SDK 加载失败，请检查网络或浏览器弹窗拦截。',
        });
        return;
    }
    const sdk = window.WecomAIBotSDK;
    if (!sdk || typeof sdk.openBotInfoAuthWindow !== 'function') {
        wecomAuthStage.value = 'FAILED';
        wecomAuthLoading.value = false;
        await alert({
            title: '企业微信授权初始化失败',
            message: '未检测到可用的企业微信授权 SDK。',
        });
        return;
    }
    wecomAuthLoading.value = false;
    const result = sdk.openBotInfoAuthWindow({ source: WECOM_SOURCE });
    if (result && typeof result.then === 'function') {
        result.then(
            async bot => {
                try {
                    if (bot?.botid && bot?.secret) {
                        await saveMyWecomBinding(
                            form.id,
                            {
                                botId: String(bot.botid || '').trim(),
                                secret: String(bot.secret || '').trim(),
                                source: WECOM_SOURCE,
                            },
                            handleUnauthorized
                        );
                        const binding = await getMyChannelBinding(form.id, handleUnauthorized);
                        myBindings.value = {
                            ...myBindings.value,
                            [form.id]: binding,
                        };
                        wecomAuthStage.value = 'AUTHORIZED';
                        await refreshWecomStatus();
                        startWecomStatusPolling();
                        return;
                    }
                    wecomAuthStage.value = 'FAILED';
                    await alert({
                        title: '授权失败',
                        message: '企业微信授权未返回有效 bot_id/secret，请重试。',
                    });
                } catch (bindError) {
                    wecomAuthStage.value = 'FAILED';
                    await alert({
                        title: '绑定失败',
                        message: bindError?.message || '企业微信绑定失败，请重试。',
                    });
                }
            },
            async error => {
                if (error?.code === 'WINDOW_BLOCKED') {
                    wecomAuthStage.value = 'FAILED';
                    await alert({
                        title: '授权窗口被拦截',
                        message: '请允许弹窗后重试企业微信授权。',
                    });
                    return;
                }
                if (error?.code === 'CANCELLED') {
                    wecomAuthStage.value = hasWecomRuntimeBinding(form.id) ? 'AUTHORIZED' : 'IDLE';
                    return;
                }
                wecomAuthStage.value = 'FAILED';
                await alert({
                    title: '授权失败',
                    message: error?.message || error?.code || '企业微信授权失败',
                });
            }
        );
    }
}
async function refreshWecomStatus() {
    const wecomId =
        form.presetType === 'wecom' && form.id
            ? form.id
            : blocks.value.find(item => item.type === 'wecom')?.config?.id;
    if (!wecomId) {
        wecomStatus.value = 'NOT_CONFIGURED';
        return;
    }
    try {
        const data = await getWecomRuntimeStatus(wecomId, handleUnauthorized);
        const runtimeBound = Boolean(data?.runtimeBound || hasWecomRuntimeBinding(wecomId));
        wecomAuthStage.value = runtimeBound ? 'AUTHORIZED' : 'IDLE';
        if (!runtimeBound) {
            wecomStatus.value = 'NOT_BOUND';
            return;
        }
        wecomStatus.value = data?.status || (data?.running ? 'RUNNING' : 'NOT_STARTED');
    } catch (error) {
        wecomStatus.value = 'ERROR';
    }
}

function startWechatStatusPolling() {
    stopWechatStatusPolling();
    statusTimer.value = window.setInterval(() => {
        refreshWechatStatus();
    }, 2500);
}

function stopWechatStatusPolling() {
    if (statusTimer.value) {
        window.clearInterval(statusTimer.value);
        statusTimer.value = null;
    }
}

function startWecomStatusPolling() {
    stopWecomStatusPolling();
    wecomStatusTimer.value = window.setInterval(() => {
        refreshWecomStatus();
    }, 10000);
}

function stopWecomStatusPolling() {
    if (wecomStatusTimer.value) {
        window.clearInterval(wecomStatusTimer.value);
        wecomStatusTimer.value = null;
    }
}

function stopDingtalkRegisterPolling() {
    if (dingtalkRegisterTimer.value) {
        window.clearInterval(dingtalkRegisterTimer.value);
        dingtalkRegisterTimer.value = null;
    }
}

function closeEditor() {
    showEditor.value = false;
    stopWechatStatusPolling();
    stopWecomStatusPolling();
    stopDingtalkRegisterPolling();
    dingtalkRegisterUrl.value = '';
}

function getWechatStatusLabel(status) {
    switch (String(status || '').toUpperCase()) {
        case 'NOT_LOGIN':
            return '未登录';
        case 'WAITING':
            return '等待扫码';
        case 'SCANNED':
            return '已扫码';
        case 'LOGGED_IN':
            return '已登录';
        case 'EXPIRED':
            return '二维码过期';
        case 'ERROR':
            return '连接异常';
        case 'NOT_CONFIGURED':
            return '未配置';
        case 'NOT_STARTED':
            return '未启动';
        default:
            return '未知状态';
    }
}

function getWecomStatusLabel(status) {
    switch (String(status || '').toUpperCase()) {
        case 'CONNECTED':
            return '已连接';
        case 'AUTHENTICATING':
            return '鉴权中';
        case 'CONNECTING':
            return '连接中';
        case 'RECONNECTING':
            return '重连中';
        case 'AUTH_FAILED':
            return '鉴权失败';
        case 'RECONNECT_EXHAUSTED':
            return '重连耗尽';
        case 'DISCONNECTED':
            return '已断开';
        case 'NOT_BOUND':
            return '待账号绑定';
        case 'NOT_CONFIGURED':
            return '未配置';
        case 'NOT_STARTED':
            return '未启动';
        case 'ERROR':
            return '异常';
        default:
            return '未知状态';
    }
}

function normalizeWechatQrCode(raw) {
    const value = String(raw || '').trim();
    if (!value) {
        return '';
    }
    if (value.startsWith('http://') || value.startsWith('https://') || value.startsWith('data:')) {
        return value;
    }
    return `data:image/png;base64,${value}`;
}

onMounted(async () => {
    await refreshAll();
});

onBeforeUnmount(() => {
    stopWechatStatusPolling();
    stopWecomStatusPolling();
    stopDingtalkRegisterPolling();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-[#f6f8fb]">
        <header class="border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-col gap-6 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <h2 class="text-3xl font-bold tracking-tight text-slate-900">渠道接入</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        管理渠道接入配置。
                    </p>
                </div>
                <div class="flex flex-wrap items-center gap-3">
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="refreshAll"
                    >
                        刷新
                    </button>
                </div>
            </div>
        </header>

        <div class="min-h-0 flex-1 overflow-y-auto px-8 pb-8 pt-6">
            <p
                v-if="loadError"
                class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div class="grid gap-6 lg:grid-cols-2">
                <button
                    v-for="block in blocks"
                    :key="block.type"
                    type="button"
                    class="overflow-hidden rounded-[32px] border bg-white text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                    :class="activeType === block.type ? 'border-primary' : 'border-slate-200'"
                    @click="selectBlock(block.type)"
                >
                    <div class="bg-gradient-to-br p-6" :class="block.accentClass">
                        <div class="flex items-start justify-between gap-4">
                            <div class="min-w-0">
                                <div class="flex items-center gap-2">
                                    <span
                                        class="rounded-full px-2.5 py-1 text-[11px] font-semibold"
                                        :class="
                                            block.initialized
                                                ? 'bg-slate-900 text-white'
                                                : 'bg-white text-slate-600 ring-1 ring-slate-200'
                                        "
                                    >
                                        {{ block.initialized ? '已初始化' : '待初始化' }}
                                    </span>
                                    <span
                                        v-if="block.type === 'weixin'"
                                        class="rounded-full bg-white px-2.5 py-1 text-[11px] font-semibold text-slate-700 ring-1 ring-slate-200"
                                    >
                                        {{ getWechatStatusLabel(weixinStatus) }}
                                    </span>
                                    <span
                                        v-if="block.type === 'wecom'"
                                        class="rounded-full bg-white px-2.5 py-1 text-[11px] font-semibold text-slate-700 ring-1 ring-slate-200"
                                    >
                                        {{ getWecomStatusLabel(wecomStatus) }}
                                    </span>
                                </div>
                                <h3 class="mt-4 text-2xl font-bold tracking-tight text-slate-900">
                                    {{ block.title }}
                                </h3>
                                <p class="mt-2 text-sm font-medium text-slate-600">
                                    {{ block.subtitle }}
                                </p>
                            </div>
                            <span
                                class="flex h-14 w-14 shrink-0 items-center justify-center rounded-3xl"
                                :class="block.iconBgClass"
                            >
                                <span class="material-symbols-outlined text-[28px]">{{
                                    block.icon
                                }}</span>
                            </span>
                        </div>
                        <p class="mt-5 max-w-2xl text-sm leading-6 text-slate-600">
                            {{ block.description }}
                        </p>
                    </div>

                    <div class="grid gap-4 px-6 py-5">
                        <div class="grid gap-3 rounded-3xl bg-slate-50 p-4 text-sm text-slate-600">
                            <div class="flex items-center justify-between gap-4">
                                <span class="text-slate-400">实例名称</span>
                                <span class="font-semibold text-slate-800">
                                    {{ block.config?.name || block.defaultName }}
                                </span>
                            </div>
                            <div class="flex items-center justify-between gap-4">
                                <span class="text-slate-400">路由目标</span>
                                <span class="truncate font-semibold text-slate-800">
                                    {{
                                        block.config
                                            ? formatRouteTarget(
                                                  resolveBinding(block.config.id) || block.config
                                              )
                                            : '尚未配置'
                                    }}
                                </span>
                            </div>
                            <div class="flex items-center justify-between gap-4">
                                <span class="text-slate-400">用户绑定</span>
                                <span class="truncate font-semibold text-slate-800">
                                    {{ block.config ? '当前用户' : '尚未配置' }}
                                </span>
                            </div>
                        </div>

                        <div class="flex flex-wrap gap-2">
                            <button
                                type="button"
                                class="rounded-2xl bg-primary px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-hover"
                                @click.stop="openConfigModal(block)"
                            >
                                配置
                            </button>
                        </div>
                    </div>
                </button>
            </div>
        </div>

        <BaseModal :open="showEditor" panel-class="max-w-[820px]" @close="closeEditor">
            <template #header>
                <div class="border-b border-slate-200 px-6 py-5">
                    <h3 class="text-2xl font-bold text-slate-900">
                        配置
                        {{
                            CHANNEL_PRESETS.find(item => item.type === form.presetType)?.title ||
                            '渠道'
                        }}
                    </h3>
                </div>
            </template>
            <template #content>
                <div class="space-y-6 px-6 py-6">
                    <div class="grid gap-4 md:grid-cols-2">
                        <div>
                            <label class="mb-2 block text-sm font-semibold text-slate-700"
                                >实例名称</label
                            >
                            <input
                                v-model="form.name"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                            />
                        </div>
                        <div>
                            <label class="mb-2 block text-sm font-semibold text-slate-700"
                                >用户绑定</label
                            >
                            <div
                                class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600"
                            >
                                当前用户
                            </div>
                        </div>
                        <div>
                            <label class="mb-2 block text-sm font-semibold text-slate-700"
                                >技能</label
                            >
                            <select
                                v-model="form.skillId"
                                class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:bg-slate-50"
                            >
                                <option value="">不选择</option>
                                <option
                                    v-for="option in skillOptions"
                                    :key="option.value"
                                    :value="option.value"
                                >
                                    {{ option.label }}
                                </option>
                            </select>
                            <p class="mt-2 text-xs leading-5 text-slate-500">可选</p>
                        </div>
                        <div>
                            <label class="mb-2 block text-sm font-semibold text-slate-700"
                                >Bot 前缀</label
                            >
                            <input
                                v-model="form.botPrefix"
                                type="text"
                                placeholder="可选，如 /bot"
                                class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                            />
                        </div>
                        <div class="flex items-center gap-3 pt-8">
                            <input
                                v-model="form.enabled"
                                id="channel-enabled"
                                type="checkbox"
                                class="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary/20"
                            />
                            <label
                                for="channel-enabled"
                                class="text-sm font-semibold text-slate-700"
                            >
                                保存后启用此实例
                            </label>
                        </div>
                    </div>

                    <div>
                        <label class="mb-2 block text-sm font-semibold text-slate-700">描述</label>
                        <textarea
                            v-model="form.description"
                            rows="2"
                            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                        />
                    </div>

                    <div class="overflow-hidden rounded-[28px] border border-slate-200 bg-slate-50">
                        <div class="border-b border-slate-200 bg-white/80 px-4 pt-4">
                            <div class="flex flex-wrap gap-2">
                                <button
                                    v-if="form.presetType === 'weixin'"
                                    type="button"
                                    class="rounded-t-2xl px-4 py-2.5 text-sm font-semibold transition"
                                    :class="
                                        editorTab === 'weixin-auth'
                                            ? 'bg-emerald-500 text-white'
                                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                                    "
                                    @click="editorTab = 'weixin-auth'"
                                >
                                    微信扫码状态
                                </button>
                                <button
                                    v-if="form.presetType === 'wecom'"
                                    type="button"
                                    class="rounded-t-2xl px-4 py-2.5 text-sm font-semibold transition"
                                    :class="
                                        editorTab === 'wecom-auth'
                                            ? 'bg-orange-500 text-white'
                                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                                    "
                                    @click="
                                        wecomAuthStage =
                                            form.id && hasWecomRuntimeBinding(form.id)
                                                ? 'AUTHORIZED'
                                                : 'IDLE';
                                        editorTab = 'wecom-auth';
                                        refreshWecomStatus();
                                        startWecomStatusPolling();
                                    "
                                >
                                    企业微信授权
                                </button>
                                <button
                                    v-if="form.presetType === 'dingtalk'"
                                    type="button"
                                    class="rounded-t-2xl px-4 py-2.5 text-sm font-semibold transition"
                                    :class="
                                        editorTab === 'dingtalk-auth'
                                            ? 'bg-sky-500 text-white'
                                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                                    "
                                    @click="editorTab = 'dingtalk-auth'"
                                >
                                    钉钉授权接入
                                </button>
                                <button
                                    v-if="form.presetType !== 'dingtalk'"
                                    type="button"
                                    class="rounded-t-2xl px-4 py-2.5 text-sm font-semibold transition"
                                    :class="
                                        editorTab === 'config-json'
                                            ? 'bg-slate-900 text-white'
                                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                                    "
                                    @click="editorTab = 'config-json'"
                                >
                                    configJson
                                </button>
                            </div>
                        </div>

                        <div
                            v-if="form.presetType === 'dingtalk' && editorTab === 'dingtalk-auth'"
                            class="border-t border-sky-100 bg-sky-50/70 p-5"
                        >
                            <div
                                class="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"
                            >
                                <div class="max-w-xl">
                                    <h4 class="text-xl font-bold text-slate-900">
                                        钉钉 Stream 接入
                                    </h4>
                                    <p class="mt-2 text-sm leading-6 text-slate-600">
                                        点击“扫码授权”后会生成钉钉授权二维码。扫码确认后，应用凭证会自动保存并启用
                                        Stream 长连接。
                                    </p>
                                    <div class="mt-4 flex flex-wrap items-center gap-2">
                                        <span
                                            class="rounded-full px-3 py-1 text-xs font-semibold"
                                            :class="
                                                hasDingtalkCredentialDraft()
                                                    ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
                                                    : 'bg-white text-slate-600 ring-1 ring-slate-200'
                                            "
                                        >
                                            {{
                                                hasDingtalkCredentialDraft()
                                                    ? '凭证已填写'
                                                    : '待授权接入'
                                            }}
                                        </span>
                                        <span
                                            class="rounded-full px-3 py-1 text-xs font-semibold"
                                            :class="
                                                hasDingtalkRobotCodeDraft()
                                                    ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
                                                    : 'bg-white text-slate-600 ring-1 ring-slate-200'
                                            "
                                        >
                                            {{
                                                hasDingtalkRobotCodeDraft()
                                                    ? '文件配置已填写'
                                                    : '默认使用 AppKey'
                                            }}
                                        </span>
                                        <button
                                            type="button"
                                            class="rounded-2xl bg-sky-500 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="dingtalkRegisterLoading || saving"
                                            @click="startDingtalkRegister"
                                        >
                                            {{
                                                dingtalkRegisterLoading || saving
                                                    ? '二维码生成中...'
                                                    : '扫码授权'
                                            }}
                                        </button>
                                        <button
                                            type="button"
                                            class="rounded-2xl border border-sky-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-sky-50 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="dingtalkRegisterLoading || saving"
                                            @click="openDingtalkRegisterShortcut"
                                        >
                                            {{
                                                dingtalkRegisterLoading || saving
                                                    ? '授权页生成中...'
                                                    : '打开钉钉授权页'
                                            }}
                                        </button>
                                    </div>
                                    <label class="mt-5 block">
                                        <span class="text-sm font-semibold text-slate-800">
                                            钉钉机器人编码 robotCode
                                        </span>
                                        <input
                                            v-model.trim="dingtalkRobotCodeDraft"
                                            type="text"
                                            autocomplete="off"
                                            class="mt-2 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 font-mono text-sm text-slate-900 outline-none transition focus:border-sky-400 focus:ring-2 focus:ring-sky-100"
                                            placeholder="可选；留空默认使用 AppKey"
                                        />
                                    </label>
                                    <p class="mt-2 text-xs leading-5 text-slate-500">
                                        自建应用机器人通常可留空；第三方应用或独立机器人编码与
                                        AppKey 不一致时再填写。
                                    </p>
                                </div>
                                <div
                                    class="flex min-h-[260px] w-full max-w-[300px] items-center justify-center rounded-[28px] border border-white/80 bg-white p-4 text-center shadow-sm"
                                >
                                    <div
                                        v-if="dingtalkRegisterLoading && !dingtalkRegisterQrCode"
                                        class="text-sm font-semibold text-slate-400"
                                    >
                                        正在生成二维码...
                                    </div>
                                    <div v-else-if="dingtalkRegisterQrCode" class="w-full">
                                        <img
                                            :src="dingtalkRegisterQrCode"
                                            alt="DingTalk Register QR Code"
                                            class="mx-auto h-56 w-56 rounded-2xl object-contain"
                                        />
                                        <p class="mt-3 text-sm font-semibold text-slate-600">
                                            {{
                                                dingtalkRegisterStatus === 'confirmed'
                                                    ? '授权完成'
                                                    : dingtalkRegisterStatus === 'expired'
                                                      ? '二维码已过期'
                                                      : dingtalkRegisterStatus === 'denied'
                                                        ? '授权已取消'
                                                        : '请使用钉钉扫码并确认'
                                            }}
                                        </p>
                                    </div>
                                    <div v-else class="text-sm leading-6 text-slate-400">
                                        点击“扫码授权”后，二维码会显示在这里。
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div
                            v-if="form.presetType === 'weixin' && editorTab === 'weixin-auth'"
                            class="border-t border-emerald-100 bg-emerald-50/60 p-5"
                        >
                            <div
                                class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"
                            >
                                <div class="max-w-xl">
                                    <h4 class="text-xl font-bold text-slate-900">微信扫码登录</h4>
                                    <p class="mt-2 text-sm leading-6 text-slate-600">
                                        先保存基础配置，再在当前弹窗里获取二维码。二维码内容会按
                                        MateClaw 的方式自动兼容 base64 展示。
                                    </p>
                                    <div class="mt-4 flex flex-wrap items-center gap-2">
                                        <span
                                            class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-700 ring-1 ring-emerald-200"
                                        >
                                            {{ getWechatStatusLabel(weixinStatus) }}
                                        </span>
                                        <button
                                            type="button"
                                            class="rounded-2xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="!form.id || weixinQrCodeLoading"
                                            @click="openWechatLogin"
                                        >
                                            {{
                                                weixinQrCodeLoading
                                                    ? '二维码生成中...'
                                                    : '获取二维码'
                                            }}
                                        </button>
                                        <button
                                            type="button"
                                            class="rounded-2xl border border-emerald-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="!form.id || polling"
                                            @click="pollWechatUpdates"
                                        >
                                            {{ polling ? '拉取中...' : '手动拉消息' }}
                                        </button>
                                    </div>
                                </div>

                                <div
                                    class="flex min-h-[240px] w-full max-w-[280px] items-center justify-center rounded-[28px] border border-white/80 bg-white p-4 shadow-sm"
                                >
                                    <div v-if="weixinQrCode" class="text-center">
                                        <img
                                            :src="weixinQrCode"
                                            alt="Weixin QR Code"
                                            class="mx-auto h-56 w-56 rounded-2xl object-contain"
                                        />
                                        <p class="mt-3 text-sm font-medium text-slate-600">
                                            扫码后会自动轮询登录状态
                                        </p>
                                    </div>
                                    <div
                                        v-else
                                        class="text-center text-sm leading-6 text-slate-400"
                                    >
                                        点击“获取二维码”后，扫码图片会显示在这里。
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div
                            v-if="form.presetType === 'wecom' && editorTab === 'wecom-auth'"
                            class="border-t border-orange-100 bg-orange-50/60 p-5"
                        >
                            <div class="max-w-2xl">
                                <div class="max-w-xl">
                                    <h4 class="text-xl font-bold text-slate-900">
                                        企业微信扫码授权
                                    </h4>
                                    <p class="mt-2 text-sm leading-6 text-slate-600">
                                        企业微信通过弹窗完成扫码授权：点击“获取授权二维码”后会拉起企业微信官方授权窗口，请在窗口中扫码并确认。
                                        凭据会自动回填，不再展示页面二维码，也不支持手动输入参数。
                                    </p>

                                    <div class="mt-4 flex flex-wrap items-center gap-2">
                                        <span
                                            class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-700 ring-1 ring-orange-200"
                                        >
                                            {{ getWecomStatusLabel(wecomStatus) }}
                                        </span>
                                        <span
                                            class="rounded-full px-3 py-1 text-xs font-semibold"
                                            :class="
                                                form.id && hasWecomRuntimeBinding(form.id)
                                                    ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
                                                    : 'bg-white text-slate-600 ring-1 ring-slate-200'
                                            "
                                        >
                                            {{
                                                form.id && hasWecomRuntimeBinding(form.id)
                                                    ? '凭据已获取'
                                                    : '待扫码授权'
                                            }}
                                        </span>
                                        <button
                                            type="button"
                                            class="rounded-2xl bg-orange-500 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="wecomAuthLoading"
                                            @click="handleWecomAuth"
                                        >
                                            {{
                                                wecomAuthLoading
                                                    ? '授权加载中...'
                                                    : '获取授权二维码'
                                            }}
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div v-if="editorTab === 'config-json'" class="p-4">
                            <div>
                                <h4 class="text-sm font-bold text-slate-900">configJson</h4>
                                <p class="mt-1 text-xs leading-5 text-slate-500">
                                    只保留预置渠道的原始配置
                                    JSON。微信推荐从轮询参数开始，企业微信改为按当前账号在授权页绑定登录态，WebChat
                                    保留欢迎语和主题配置。
                                </p>
                            </div>
                            <textarea
                                v-model="form.configJson"
                                rows="12"
                                class="mt-4 w-full rounded-3xl border border-slate-200 bg-[#0f172a] px-4 py-4 font-mono text-sm leading-6 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                            />
                        </div>
                    </div>
                </div>
            </template>
            <template #footer>
                <div
                    class="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4"
                >
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="closeEditor"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving"
                        @click="saveChannel"
                    >
                        {{ saving ? '保存中...' : '保存配置' }}
                    </button>
                </div>
            </template>
        </BaseModal>
    </section>
</template>
