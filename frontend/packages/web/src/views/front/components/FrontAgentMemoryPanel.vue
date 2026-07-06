<template>
    <section :class="wrapperClass">
        <div :class="innerClass">
            <header
                v-if="showHeader"
                class="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white px-6 py-5 shadow-sm md:flex-row md:items-start md:justify-between"
            >
                <div>
                    <div class="flex items-center gap-3">
                        <button
                            v-if="showBackButton"
                            type="button"
                            class="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
                            @click="goBack"
                        >
                            <span class="material-symbols-outlined text-base">arrow_back</span>
                            返回
                        </button>
                        <span
                            class="inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700"
                        >
                            {{ currentUserState.profile?.roleName || '未绑定角色' }}
                        </span>
                    </div>
                    <h1 class="mt-4 text-2xl font-bold text-slate-900">{{ title }}</h1>
                    <p class="mt-2 text-sm text-slate-500">
                        {{ description }}
                    </p>
                </div>

                <button
                    type="button"
                    class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="savingProfile || savingSoul"
                    @click="handleSaveAll"
                >
                    {{ savingProfile || savingSoul ? '保存中...' : '保存全部' }}
                </button>
            </header>

            <div :class="panelContentClass">
                <div class="grid gap-6 lg:grid-cols-2">
                    <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="text-base font-semibold text-slate-900">PROFILE.md</h2>
                                <p class="mt-1 text-sm text-slate-500">
                                    描述你的角色、职责、业务背景和沟通偏好。
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="savingProfile || savingSoul"
                                @click="handleSaveProfile"
                            >
                                {{ savingProfile ? '保存中...' : '保存 PROFILE' }}
                            </button>
                        </div>
                        <div class="mt-4">
                            <MarkdownEditor
                                v-model="profileContent"
                                placeholder="请描述你的角色、工作职责和当前重点任务"
                                height="520px"
                                :auto-height="embedded"
                                hint="保存后会作为 PROFILE.md 参与系统提示词组装。"
                            />
                        </div>
                    </section>

                    <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="text-base font-semibold text-slate-900">SOUL.md</h2>
                                <p class="mt-1 text-sm text-slate-500">
                                    描述助手长期生效的行为风格、回答边界和协作方式。
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="savingSoul || savingProfile"
                                @click="handleSaveSoul"
                            >
                                {{ savingSoul ? '保存中...' : '保存 SOUL' }}
                            </button>
                        </div>
                        <div class="mt-4">
                            <MarkdownEditor
                                v-model="soulContent"
                                placeholder="请输入 SOUL.md 内容"
                                height="520px"
                                :auto-height="embedded"
                                hint="保存后会作为 SOUL.md 参与系统提示词组装。"
                            />
                        </div>
                    </section>
                </div>

                <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                    <div>
                        <h2 class="text-base font-semibold text-slate-900">可用技能</h2>
                        <p class="mt-1 text-sm text-slate-500">
                            当前可用技能来自角色资源权限，由管理员在角色管理中配置。
                        </p>
                    </div>

                    <div
                        class="mt-4 flex min-h-20 flex-wrap gap-2 rounded-xl border border-dashed border-slate-200 bg-slate-50 p-4"
                    >
                        <div class="grid w-full gap-4 lg:grid-cols-2">
                            <div class="rounded-xl border border-blue-100 bg-blue-50/60 p-4">
                                <div class="flex items-center justify-between gap-3">
                                    <h3 class="text-sm font-semibold text-blue-900">已启用</h3>
                                    <span class="text-xs text-blue-500">
                                        {{ enabledSkills.length }} 个
                                    </span>
                                </div>
                                <div class="mt-3 flex min-h-20 flex-wrap gap-2">
                                    <template v-if="enabledSkills.length > 0">
                                        <button
                                            v-for="skill in enabledSkills"
                                            :key="skill.id"
                                            type="button"
                                            class="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-white px-3 py-1.5 text-sm text-blue-700 hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="savingSkills"
                                            @click="disableSkill(skill.id)"
                                        >
                                            <span>{{ skill.displayName || skill.name }}</span>
                                            <span class="text-xs">移除</span>
                                        </button>
                                    </template>
                                    <p v-else class="text-sm text-blue-400">当前未启用任何技能。</p>
                                </div>
                            </div>

                            <div class="rounded-xl border border-slate-200 bg-white p-4">
                                <div class="flex items-center justify-between gap-3">
                                    <h3 class="text-sm font-semibold text-slate-700">未启用</h3>
                                    <span class="text-xs text-slate-400">
                                        {{ disabledSkills.length }} 个
                                    </span>
                                </div>
                                <div class="mt-3 flex min-h-20 flex-wrap gap-2">
                                    <template v-if="disabledSkills.length > 0">
                                        <button
                                            v-for="skill in disabledSkills"
                                            :key="skill.id"
                                            type="button"
                                            class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                                            :disabled="savingSkills"
                                            @click="enableSkill(skill.id)"
                                        >
                                            <span>{{ skill.displayName || skill.name }}</span>
                                            <span class="text-xs">启用</span>
                                        </button>
                                    </template>
                                    <p v-else class="text-sm text-slate-400">
                                        当前角色授权技能已全部启用。
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MarkdownEditor from '@/components/MarkdownEditor.vue';
import { ensureAgentConfigLoaded } from '@/composables/useAgentConfig';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import {
    getCurrentUserAgentFile,
    getCurrentUserAgentSkillPreference,
    updateCurrentUserAgentFile,
    updateCurrentUserAgentSkillPreference,
} from '@/api/user-agent-config';
import { alert } from '@/composables/useModal';
import { ROUTE_PATHS } from '@/router/routePaths';

const props = defineProps({
    embedded: {
        type: Boolean,
        default: false,
    },
    showBackButton: {
        type: Boolean,
        default: true,
    },
});

const router = useRouter();

const profileContent = ref('');
const soulContent = ref('');
const permittedSkills = ref([]);
const enabledSkillIds = ref([]);
const savingProfile = ref(false);
const savingSoul = ref(false);
const savingSkills = ref(false);

const showHeader = computed(() => !props.embedded);
const title = computed(() => (props.embedded ? '记忆' : '个人助手'));
const description = computed(() =>
    props.embedded ? '维护长期生效的 PROFILE.md 与 SOUL.md。' : '维护你的个人助手记忆。'
);
const wrapperClass = computed(() =>
    props.embedded ? 'text-slate-900' : 'min-h-screen bg-slate-50 text-slate-900'
);
const innerClass = computed(() =>
    props.embedded
        ? 'space-y-6 pb-6'
        : 'mx-auto flex min-h-screen w-full max-w-7xl flex-col px-6 py-8 lg:px-8'
);
const panelContentClass = computed(() =>
    props.embedded ? 'space-y-6' : 'mt-6 flex flex-1 flex-col gap-6'
);
const enabledSkillIdSet = computed(() => new Set(enabledSkillIds.value));
const enabledSkills = computed(() =>
    permittedSkills.value.filter(skill => enabledSkillIdSet.value.has(skill.id))
);
const disabledSkills = computed(() =>
    permittedSkills.value.filter(skill => !enabledSkillIdSet.value.has(skill.id))
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function goBack() {
    router.push(ROUTE_PATHS.frontAgentChat);
}

async function loadConfig() {
    const [profileFile, soulFile, skillPreference] = await Promise.all([
        getCurrentUserAgentFile('PROFILE.md', handleUnauthorized).catch(() => null),
        getCurrentUserAgentFile('SOUL.md', handleUnauthorized).catch(() => null),
        getCurrentUserAgentSkillPreference(handleUnauthorized).catch(() => null),
    ]);
    profileContent.value = profileFile?.content || '';
    soulContent.value = soulFile?.content || '';
    permittedSkills.value = Array.isArray(skillPreference?.permittedSkills)
        ? skillPreference.permittedSkills
        : [];
    enabledSkillIds.value = Array.isArray(skillPreference?.enabledSkillIds)
        ? skillPreference.enabledSkillIds
        : [];
}

async function saveCurrentUserFile(
    filename,
    content,
    savingState,
    successMessage,
    fallbackMessage
) {
    savingState.value = true;
    try {
        await updateCurrentUserAgentFile(filename, content, handleUnauthorized);
        await ensureAgentConfigLoaded({ force: true, onUnauthorized: handleUnauthorized });
        await alert({
            title: '保存成功',
            message: successMessage,
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || fallbackMessage,
        });
    } finally {
        savingState.value = false;
    }
}

async function handleSaveProfile() {
    await saveCurrentUserFile(
        'PROFILE.md',
        profileContent.value,
        savingProfile,
        'PROFILE.md 已更新。',
        '保存 PROFILE.md 失败'
    );
}

async function handleSaveSoul() {
    await saveCurrentUserFile(
        'SOUL.md',
        soulContent.value,
        savingSoul,
        'SOUL.md 已更新。',
        '保存 SOUL.md 失败'
    );
}

async function handleSaveAll() {
    if (savingProfile.value || savingSoul.value) {
        return;
    }
    savingProfile.value = true;
    savingSoul.value = true;
    try {
        await Promise.all([
            updateCurrentUserAgentFile('PROFILE.md', profileContent.value, handleUnauthorized),
            updateCurrentUserAgentFile('SOUL.md', soulContent.value, handleUnauthorized),
        ]);
        await ensureAgentConfigLoaded({ force: true, onUnauthorized: handleUnauthorized });
        await alert({
            title: '保存成功',
            message: 'PROFILE.md 与 SOUL.md 已更新。',
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '保存记忆配置失败',
        });
    } finally {
        savingProfile.value = false;
        savingSoul.value = false;
    }
}

async function saveSkillPreference(nextEnabledSkillIds) {
    if (savingSkills.value) {
        return;
    }
    savingSkills.value = true;
    try {
        await updateCurrentUserAgentSkillPreference(nextEnabledSkillIds, handleUnauthorized);
        enabledSkillIds.value = [...nextEnabledSkillIds];
        await ensureAgentConfigLoaded({ force: true, onUnauthorized: handleUnauthorized });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '保存技能偏好失败',
        });
    } finally {
        savingSkills.value = false;
    }
}

async function enableSkill(skillId) {
    if (!skillId || enabledSkillIdSet.value.has(skillId)) {
        return;
    }
    await saveSkillPreference([...enabledSkillIds.value, skillId]);
}

async function disableSkill(skillId) {
    if (!skillId) {
        return;
    }
    await saveSkillPreference(enabledSkillIds.value.filter(id => id !== skillId));
}

onMounted(async () => {
    await loadConfig();
});
</script>
