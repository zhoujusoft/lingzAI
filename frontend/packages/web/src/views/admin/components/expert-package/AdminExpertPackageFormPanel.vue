<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import MarkdownEditor from '@/components/MarkdownEditor.vue';
import AdminExpertPackageToolSelector from './AdminExpertPackageToolSelector.vue';
import { createAgent, getAgentDetail, updateAgent } from '@/api/agents';
import { listSkillCatalogs } from '@/api/skills';
import { listToolCatalog } from '@/api/tools';
import { clearUserSession } from '@/composables/useCurrentUser';
import { showToast } from '@/composables/useToast';
import { ROUTE_PATHS } from '@/router/routePaths';

const props = defineProps({
    mode: {
        type: String,
        default: 'create',
    },
    packageId: {
        type: Number,
        default: null,
    },
});

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const saveError = ref('');
const allSkills = ref([]);
const allTools = ref([]);
const selectedSkillId = ref(null);
const activeSection = ref('base');
const contentScrollRef = ref(null);

const form = reactive({
    agentCode: '',
    agentName: '',
    description: '',
    icon: 'psychology',
    openingMessage: '',
    soulTemplate: '',
    profileTemplate: '',
    enabled: 0,
    skillIds: [],
    toolIds: [],
});

const baseIconOptions = [
    { value: 'psychology', label: '专家顾问', description: '通用专家咨询场景' },
    { value: 'analytics', label: '分析专家', description: '数据研判与洞察' },
    { value: 'account_balance', label: '合规专家', description: '制度、法律与风控' },
    { value: 'engineering', label: '技术专家', description: '技术方案与工程协作' },
    { value: 'support_agent', label: '服务专家', description: '客户服务与运营支持' },
    { value: 'business_center', label: '业务专家', description: '业务经营与管理协作' },
];

const sections = [
    { id: 'base', label: '基础信息', icon: 'tune' },
    { id: 'identity', label: '专家设定', icon: 'person_edit' },
    { id: 'capability', label: '能力组合', icon: 'extension' },
];

const isEditMode = computed(() => props.mode === 'edit');
const iconOptions = computed(() => {
    if (!form.icon || baseIconOptions.some(item => item.value === form.icon)) {
        return baseIconOptions;
    }
    return [
        {
            value: form.icon,
            label: `当前图标 ${form.icon}`,
            description: '历史专家包图标',
        },
        ...baseIconOptions,
    ];
});
const pageTitle = computed(() => (isEditMode.value ? '编辑专家技能包' : '新建专家技能包'));
const pageDescription = computed(() =>
    isEditMode.value
        ? '调整专家身份、能力边界与发布状态。保存后，新的配置会用于后续专家包对话。'
        : '组合技能与工具，配置专家身份，并决定何时向所有登录用户发布。'
);
const boundSkills = computed(() =>
    allSkills.value.filter(skill => form.skillIds.includes(skill.id))
);
const availableSkillOptions = computed(() =>
    allSkills.value
        .filter(skill => !form.skillIds.includes(skill.id))
        .map(skill => ({
            value: skill.id,
            label: skill.displayName || skill.runtimeSkillName,
            description: skill.category || skill.description || '',
        }))
);
const readinessItems = computed(() => [
    { label: '专家包名称', ready: Boolean(form.agentName.trim()) },
    { label: '专家身份设定', ready: Boolean(form.soulTemplate.trim()) },
    { label: '至少一项能力', ready: form.skillIds.length + form.toolIds.length > 0 },
]);
const readinessCount = computed(() => readinessItems.value.filter(item => item.ready).length);
const capabilityCount = computed(() => form.skillIds.length + form.toolIds.length);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function goBack() {
    router.push(ROUTE_PATHS.adminSystemAgentManagement);
}

function scrollToSection(sectionId) {
    activeSection.value = sectionId;
    const scrollContainer = contentScrollRef.value;
    const target = document.getElementById(`expert-package-${sectionId}`);
    if (!scrollContainer || !target) {
        return;
    }
    const contentRoot = scrollContainer.firstElementChild;
    const contentTop = contentRoot instanceof HTMLElement ? contentRoot.offsetTop : 0;
    const targetTop = target.offsetTop - contentTop;
    scrollContainer.scrollTo({
        top: Math.max(0, targetTop),
        behavior: 'auto',
    });
}

function fillForm(detail) {
    form.agentCode = detail?.agentCode || '';
    form.agentName = detail?.agentName || '';
    form.description = detail?.description || '';
    form.icon = detail?.icon || 'psychology';
    form.openingMessage = detail?.openingMessage || '';
    form.soulTemplate = detail?.soulTemplate || '';
    form.profileTemplate = detail?.profileTemplate || '';
    form.enabled = detail?.enabled === 1 ? 1 : 0;
    form.skillIds = Array.isArray(detail?.skills) ? detail.skills.map(item => item.id) : [];
    form.toolIds = Array.isArray(detail?.tools) ? detail.tools.map(item => item.id) : [];
}

function validateForm() {
    if (!form.agentCode.trim()) {
        showToast('请填写专家包编码', 'warning');
        scrollToSection('base');
        return false;
    }
    if (!/^[a-zA-Z][a-zA-Z0-9-]*$/.test(form.agentCode.trim())) {
        showToast('专家包编码只能包含字母、数字和连字符，且必须以字母开头', 'warning');
        scrollToSection('base');
        return false;
    }
    if (!form.agentName.trim()) {
        showToast('请填写专家包名称', 'warning');
        scrollToSection('base');
        return false;
    }
    return true;
}

function addSkill() {
    if (selectedSkillId.value == null || form.skillIds.includes(selectedSkillId.value)) {
        return;
    }
    form.skillIds.push(selectedSkillId.value);
    selectedSkillId.value = null;
}

function removeSkill(skillId) {
    form.skillIds = form.skillIds.filter(id => id !== skillId);
}

async function loadOptions() {
    const [skills, tools] = await Promise.all([
        listSkillCatalogs({}, handleUnauthorized),
        listToolCatalog({}, handleUnauthorized),
    ]);
    allSkills.value = Array.isArray(skills) ? skills.filter(item => item.enabled !== false) : [];
    allTools.value = Array.isArray(tools) ? tools : [];
}

async function loadPage() {
    loading.value = true;
    loadError.value = '';
    try {
        await loadOptions();
        if (!isEditMode.value) {
            return;
        }
        if (!props.packageId) {
            throw new Error('专家技能包 ID 无效');
        }
        fillForm(await getAgentDetail(props.packageId, handleUnauthorized));
    } catch (error) {
        loadError.value = error?.message || '专家技能包加载失败';
    } finally {
        loading.value = false;
    }
}

async function savePackage() {
    if (saving.value || !validateForm()) {
        return;
    }
    saving.value = true;
    saveError.value = '';
    const payload = {
        agentName: form.agentName.trim(),
        description: form.description.trim() || null,
        icon: form.icon || null,
        openingMessage: form.openingMessage || null,
        soulTemplate: form.soulTemplate || null,
        profileTemplate: form.profileTemplate || null,
        enabled: form.enabled,
        skillIds: [...form.skillIds],
        toolIds: [...form.toolIds],
    };
    try {
        if (isEditMode.value) {
            await updateAgent(props.packageId, payload, handleUnauthorized);
            showToast('专家技能包已保存');
        } else {
            await createAgent(
                {
                    ...payload,
                    agentCode: form.agentCode.trim(),
                },
                handleUnauthorized
            );
            showToast('专家技能包已创建');
        }
        router.replace(ROUTE_PATHS.adminSystemAgentManagement);
    } catch (error) {
        saveError.value = error?.message || '专家技能包保存失败';
        showToast(saveError.value, 'error');
    } finally {
        saving.value = false;
    }
}

watch(
    () => [props.mode, props.packageId],
    () => loadPage()
);

onMounted(() => {
    loadPage();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 pt-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div class="flex min-w-0 items-center gap-4">
                    <button
                        type="button"
                        aria-label="返回专家技能包列表"
                        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:translate-y-px"
                        @click="goBack"
                    >
                        <span class="material-symbols-outlined">arrow_back</span>
                    </button>
                    <div class="min-w-0">
                        <p class="text-xs font-semibold text-slate-400">专家技能包 / 配置详情</p>
                        <h1 class="mt-1 truncate text-2xl font-bold tracking-tight text-slate-950">
                            {{ pageTitle }}
                        </h1>
                    </div>
                </div>
                <div class="flex shrink-0 items-center gap-3">
                    <button
                        type="button"
                        class="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 active:translate-y-px"
                        @click="goBack"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 active:translate-y-px disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving || loading"
                        @click="savePackage"
                    >
                        <span class="material-symbols-outlined text-lg">save</span>
                        {{ saving ? '保存中...' : isEditMode ? '保存修改' : '创建专家包' }}
                    </button>
                </div>
            </div>
            <nav class="mt-5 flex min-w-0 gap-1 overflow-x-auto border-t border-slate-100 py-2">
                <button
                    v-for="section in sections"
                    :key="section.id"
                    type="button"
                    :class="[
                        'inline-flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition',
                        activeSection === section.id
                            ? 'bg-blue-50 text-primary'
                            : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800',
                    ]"
                    @click="scrollToSection(section.id)"
                >
                    <span class="material-symbols-outlined text-lg">{{ section.icon }}</span>
                    {{ section.label }}
                </button>
            </nav>
        </header>

        <div v-if="loading" class="flex min-h-0 flex-1 items-center justify-center text-slate-500">
            正在加载专家技能包...
        </div>
        <div
            v-else-if="loadError"
            class="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 px-6 text-center"
        >
            <span class="material-symbols-outlined text-4xl text-rose-400">error</span>
            <p class="text-sm text-rose-600">{{ loadError }}</p>
            <button
                type="button"
                class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700"
                @click="goBack"
            >
                返回列表
            </button>
        </div>

        <div ref="contentScrollRef" v-else class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
            <div
                class="mx-auto grid max-w-[1540px] gap-5 px-6 py-6 xl:grid-cols-[minmax(0,1fr)_300px]"
            >
                <main class="min-w-0 space-y-6">
                    <div
                        v-if="saveError"
                        class="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                    >
                        {{ saveError }}
                    </div>

                    <section
                        class="flex flex-col gap-5 rounded-lg border border-blue-100 bg-white p-6 shadow-sm md:flex-row md:items-center"
                    >
                        <div
                            class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-primary text-white shadow-sm"
                        >
                            <span class="material-symbols-outlined text-3xl">{{ form.icon }}</span>
                        </div>
                        <div class="min-w-0 flex-1">
                            <div class="flex flex-wrap items-center gap-3">
                                <h2 class="truncate text-xl font-bold text-slate-950">
                                    {{ form.agentName || '未命名专家技能包' }}
                                </h2>
                                <span
                                    :class="[
                                        'text-xs font-semibold',
                                        form.enabled === 1 ? 'text-emerald-600' : 'text-slate-400',
                                    ]"
                                >
                                    {{ form.enabled === 1 ? '已发布' : '未发布' }}
                                </span>
                            </div>
                            <p class="mt-1 truncate text-xs font-medium text-slate-400">
                                {{ form.agentCode || '待设置编码' }}
                            </p>
                            <p class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500">
                                {{ form.description || pageDescription }}
                            </p>
                        </div>
                        <dl
                            class="grid shrink-0 grid-cols-3 gap-px overflow-hidden rounded-lg border border-slate-200 bg-slate-200"
                        >
                            <div class="min-w-20 bg-slate-50 px-4 py-3 text-center">
                                <dt class="text-[11px] text-slate-500">技能</dt>
                                <dd class="mt-1 text-lg font-bold text-slate-950">
                                    {{ form.skillIds.length }}
                                </dd>
                            </div>
                            <div class="min-w-20 bg-slate-50 px-4 py-3 text-center">
                                <dt class="text-[11px] text-slate-500">工具</dt>
                                <dd class="mt-1 text-lg font-bold text-slate-950">
                                    {{ form.toolIds.length }}
                                </dd>
                            </div>
                            <div class="min-w-20 bg-slate-50 px-4 py-3 text-center">
                                <dt class="text-[11px] text-slate-500">能力</dt>
                                <dd class="mt-1 text-lg font-bold text-primary">
                                    {{ capabilityCount }}
                                </dd>
                            </div>
                        </dl>
                    </section>

                    <section
                        id="expert-package-base"
                        class="rounded-lg border border-slate-200 bg-white shadow-sm"
                    >
                        <div class="border-b border-slate-100 px-6 py-5">
                            <div class="flex items-center gap-3">
                                <span
                                    class="flex h-9 w-9 items-center justify-center rounded-lg border border-blue-100 bg-blue-50 text-primary"
                                >
                                    <span class="material-symbols-outlined text-xl">tune</span>
                                </span>
                                <div>
                                    <h2 class="text-base font-bold text-slate-900">基础信息</h2>
                                    <p class="mt-0.5 text-xs text-slate-500">
                                        定义专家包在管理端和前台入口中的识别信息。
                                    </p>
                                </div>
                            </div>
                        </div>
                        <div class="space-y-5 p-6">
                            <div class="grid gap-5 md:grid-cols-2">
                                <label class="space-y-2">
                                    <span class="text-sm font-semibold text-slate-700">
                                        专家包编码 <span class="text-rose-500">*</span>
                                    </span>
                                    <input
                                        v-model="form.agentCode"
                                        type="text"
                                        :disabled="isEditMode"
                                        placeholder="例如：sales-expert"
                                        class="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500"
                                    />
                                    <span class="block text-xs text-slate-400">
                                        创建后不可修改，用于稳定识别专家包。
                                    </span>
                                </label>
                                <label class="space-y-2">
                                    <span class="text-sm font-semibold text-slate-700">
                                        专家包名称 <span class="text-rose-500">*</span>
                                    </span>
                                    <input
                                        v-model="form.agentName"
                                        type="text"
                                        placeholder="请输入专家包名称"
                                        class="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                                    />
                                </label>
                            </div>
                            <div class="grid gap-5 md:grid-cols-[minmax(0,1fr)_280px]">
                                <label class="space-y-2">
                                    <span class="text-sm font-semibold text-slate-700"
                                        >场景描述</span
                                    >
                                    <textarea
                                        v-model="form.description"
                                        rows="4"
                                        placeholder="说明这个专家包解决什么问题、适合谁使用"
                                        class="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20"
                                    />
                                </label>
                                <div class="space-y-2">
                                    <span class="text-sm font-semibold text-slate-700"
                                        >专家图标</span
                                    >
                                    <AppSelect
                                        v-model="form.icon"
                                        :options="iconOptions"
                                        leading-icon="category"
                                        button-class="bg-slate-50 shadow-none"
                                    />
                                    <div
                                        class="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3"
                                    >
                                        <span
                                            class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-white"
                                        >
                                            <span class="material-symbols-outlined">{{
                                                form.icon
                                            }}</span>
                                        </span>
                                        <span class="text-xs text-slate-500">
                                            前台专家包入口将使用此图标。
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </section>

                    <section
                        id="expert-package-identity"
                        class="rounded-lg border border-slate-200 bg-white shadow-sm"
                    >
                        <div class="border-b border-slate-100 px-6 py-5">
                            <div class="flex items-center gap-3">
                                <span
                                    class="flex h-9 w-9 items-center justify-center rounded-lg border border-blue-100 bg-blue-50 text-primary"
                                >
                                    <span class="material-symbols-outlined text-xl"
                                        >person_edit</span
                                    >
                                </span>
                                <div>
                                    <h2 class="text-base font-bold text-slate-900">专家设定</h2>
                                    <p class="mt-0.5 text-xs text-slate-500">
                                        配置对话首屏引导、专家身份和适用场景档案。
                                    </p>
                                </div>
                            </div>
                        </div>
                        <div class="space-y-6 p-6">
                            <MarkdownEditor
                                v-model="form.openingMessage"
                                label="开场白"
                                placeholder="用户进入专家包对话时看到的欢迎语"
                                height="130px"
                                hint="支持 Markdown，建议简短说明专家能帮助用户完成什么。"
                            />
                            <MarkdownEditor
                                v-model="form.soulTemplate"
                                label="SOUL.md 专家身份"
                                placeholder="定义专家身份、表达方式、专业边界和行为准则"
                                height="240px"
                                hint="该内容会作为专家技能包对话中的核心身份设定。"
                            />
                            <MarkdownEditor
                                v-model="form.profileTemplate"
                                label="PROFILE.md 场景档案"
                                placeholder="描述业务背景、目标用户、常见任务和上下文约束"
                                height="190px"
                                hint="用于补充专家包的业务场景和协作背景。"
                            />
                        </div>
                    </section>

                    <section
                        id="expert-package-capability"
                        class="rounded-lg border border-slate-200 bg-white shadow-sm"
                    >
                        <div class="border-b border-slate-100 px-6 py-5">
                            <div class="flex items-center gap-3">
                                <span
                                    class="flex h-9 w-9 items-center justify-center rounded-lg border border-blue-100 bg-blue-50 text-primary"
                                >
                                    <span class="material-symbols-outlined text-xl">extension</span>
                                </span>
                                <div>
                                    <h2 class="text-base font-bold text-slate-900">能力组合</h2>
                                    <p class="mt-0.5 text-xs text-slate-500">
                                        技能用于加载专业流程，工具用于直接执行外部操作。
                                    </p>
                                </div>
                            </div>
                        </div>
                        <div class="space-y-8 p-6">
                            <div class="min-w-0 space-y-4">
                                <div class="flex items-center justify-between gap-3">
                                    <div>
                                        <h3 class="text-sm font-bold text-slate-800">技能组合</h3>
                                        <p class="mt-1 text-xs text-slate-500">
                                            当前已选择 {{ form.skillIds.length }} 项技能
                                        </p>
                                    </div>
                                    <span
                                        class="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-primary"
                                    >
                                        {{ form.skillIds.length }}
                                    </span>
                                </div>
                                <div class="flex gap-2">
                                    <AppSelect
                                        v-model="selectedSkillId"
                                        :options="availableSkillOptions"
                                        placeholder="选择技能"
                                        leading-icon="description"
                                        button-class="bg-slate-50 shadow-none"
                                        menu-class="max-h-72 overflow-y-auto"
                                    />
                                    <button
                                        type="button"
                                        aria-label="添加技能"
                                        class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg border border-blue-200 bg-blue-50 text-primary transition hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-40"
                                        :disabled="selectedSkillId == null"
                                        @click="addSkill"
                                    >
                                        <span class="material-symbols-outlined">add</span>
                                    </button>
                                </div>
                                <div class="space-y-2">
                                    <article
                                        v-for="skill in boundSkills"
                                        :key="skill.id"
                                        class="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3"
                                    >
                                        <span
                                            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-primary"
                                        >
                                            <span class="material-symbols-outlined text-lg"
                                                >description</span
                                            >
                                        </span>
                                        <span class="min-w-0 flex-1">
                                            <span
                                                class="block truncate text-sm font-semibold text-slate-800"
                                            >
                                                {{ skill.displayName || skill.runtimeSkillName }}
                                            </span>
                                            <span class="block truncate text-xs text-slate-400">
                                                {{ skill.category || '专业技能' }}
                                            </span>
                                        </span>
                                        <button
                                            type="button"
                                            aria-label="移除技能"
                                            class="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-rose-50 hover:text-rose-600"
                                            @click="removeSkill(skill.id)"
                                        >
                                            <span class="material-symbols-outlined text-lg"
                                                >close</span
                                            >
                                        </button>
                                    </article>
                                    <p
                                        v-if="!boundSkills.length"
                                        class="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400"
                                    >
                                        尚未配置技能
                                    </p>
                                </div>
                            </div>

                            <div class="min-w-0 space-y-4">
                                <div class="flex items-center justify-between gap-3">
                                    <div>
                                        <h3 class="text-sm font-bold text-slate-800">工具组合</h3>
                                        <p class="mt-1 text-xs text-slate-500">
                                            当前已选择 {{ form.toolIds.length }} 项工具
                                        </p>
                                    </div>
                                    <span class="text-xs font-semibold text-primary">
                                        {{ form.toolIds.length }}
                                    </span>
                                </div>
                                <AdminExpertPackageToolSelector
                                    v-model="form.toolIds"
                                    :tools="allTools"
                                />
                            </div>
                        </div>
                    </section>
                </main>

                <aside class="min-w-0 space-y-4 xl:sticky xl:top-6 xl:self-start">
                    <section class="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-start justify-between gap-3">
                            <div>
                                <h2 class="text-sm font-bold text-slate-950">发布控制</h2>
                                <p class="mt-1 text-xs leading-5 text-slate-500">
                                    启用后，所有登录用户都能进入并使用包内工具。
                                </p>
                            </div>
                            <button
                                type="button"
                                role="switch"
                                :aria-checked="form.enabled === 1"
                                :class="[
                                    'relative h-6 w-11 shrink-0 rounded-full transition',
                                    form.enabled === 1 ? 'bg-emerald-500' : 'bg-slate-300',
                                ]"
                                @click="form.enabled = form.enabled === 1 ? 0 : 1"
                            >
                                <span
                                    :class="[
                                        'absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition',
                                        form.enabled === 1 ? 'left-[22px]' : 'left-0.5',
                                    ]"
                                />
                            </button>
                        </div>
                        <div
                            :class="[
                                'mt-4 rounded-lg border px-3 py-3 text-xs leading-5',
                                form.enabled === 1
                                    ? 'border-amber-200 bg-amber-50 text-amber-800'
                                    : 'border-slate-200 bg-slate-50 text-slate-500',
                            ]"
                        >
                            {{
                                form.enabled === 1
                                    ? '当前将以发布状态保存，请确认包内工具适合向所有登录用户开放。'
                                    : '当前为未发布状态，保存后仅在管理端可见。'
                            }}
                        </div>
                    </section>

                    <section class="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                        <h2 class="text-sm font-bold text-slate-950">发布检查</h2>
                        <dl class="mt-4 grid grid-cols-2 gap-3">
                            <div class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
                                <dt class="text-xs text-slate-500">技能</dt>
                                <dd class="mt-1 text-xl font-bold text-primary">
                                    {{ form.skillIds.length }}
                                </dd>
                            </div>
                            <div class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
                                <dt class="text-xs text-slate-500">工具</dt>
                                <dd class="mt-1 text-xl font-bold text-primary">
                                    {{ form.toolIds.length }}
                                </dd>
                            </div>
                        </dl>
                        <div class="mt-4 border-t border-slate-100 pt-4">
                            <div class="flex items-center justify-between text-xs">
                                <span class="font-semibold text-slate-700">发布准备度</span>
                                <span class="text-slate-400">
                                    {{ readinessCount }}/{{ readinessItems.length }}
                                </span>
                            </div>
                            <div class="mt-3 space-y-2">
                                <div
                                    v-for="item in readinessItems"
                                    :key="item.label"
                                    class="flex items-center gap-2 text-xs"
                                >
                                    <span
                                        :class="[
                                            'material-symbols-outlined text-base',
                                            item.ready ? 'text-emerald-500' : 'text-slate-300',
                                        ]"
                                    >
                                        {{ item.ready ? 'check_circle' : 'radio_button_unchecked' }}
                                    </span>
                                    <span :class="item.ready ? 'text-slate-700' : 'text-slate-400'">
                                        {{ item.label }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </section>
                </aside>
            </div>
        </div>
    </section>
</template>
