<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header
            class="flex shrink-0 items-start justify-between border-b border-slate-100 bg-white px-8 py-6"
        >
            <div>
                <h1 class="text-xl font-bold text-slate-900">用户 Agent 配置</h1>
                <p class="mt-1 text-sm text-slate-500">
                    维护用户长期生效的 PROFILE.md 与 SOUL.md，并查看角色资源权限授予的可用技能。
                </p>
            </div>
            <button
                type="button"
                class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="!selectedUserId || savingProfile || savingSoul"
                @click="handleSaveAll"
            >
                {{ savingProfile || savingSoul ? '保存中...' : '保存全部' }}
            </button>
        </header>

        <div class="flex min-h-0 flex-1 flex-col gap-6 overflow-auto p-8">
            <section class="rounded-xl border border-slate-200 bg-white p-6">
                <div class="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
                    <div class="space-y-2">
                        <label class="block text-sm font-semibold text-slate-700">选择用户</label>
                        <select
                            v-model="selectedUserId"
                            class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
                        >
                            <option :value="null">请选择用户</option>
                            <option v-for="user in users" :key="user.id" :value="user.id">
                                {{ user.name || user.code }}（{{ user.code }}）
                            </option>
                        </select>
                    </div>

                    <div class="grid gap-4 sm:grid-cols-2">
                        <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
                            <p class="text-xs font-medium text-slate-500">角色</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ selectedUser?.roleName || '-' }}
                            </p>
                            <p class="mt-1 text-xs text-slate-500">
                                {{
                                    selectedUser?.roleId
                                        ? `Role ID: ${selectedUser.roleId}`
                                        : '未绑定角色'
                                }}
                            </p>
                        </div>
                        <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
                            <p class="text-xs font-medium text-slate-500">资源权限技能</p>
                            <p class="mt-2 text-sm font-semibold text-slate-900">
                                {{ enabledSkillIds.length }} / {{ permittedSkills.length }}
                            </p>
                            <p class="mt-1 text-xs text-slate-500">
                                {{ skillPreferenceText }}
                            </p>
                        </div>
                    </div>
                </div>
            </section>

            <template v-if="selectedUserId">
                <div class="grid gap-6 xl:grid-cols-2">
                    <section class="rounded-xl border border-slate-200 bg-white p-6">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="text-base font-semibold text-slate-900">PROFILE.md</h2>
                                <p class="mt-1 text-sm text-slate-500">
                                    维护用户自己的身份、职责和长期背景信息。
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
                                label=""
                                placeholder="请输入用户身份与职责"
                                height="520px"
                                hint="保存时会写入 user_agent_file 中的 PROFILE.md。"
                            />
                        </div>
                    </section>

                    <section class="rounded-xl border border-slate-200 bg-white p-6">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="text-base font-semibold text-slate-900">SOUL.md</h2>
                                <p class="mt-1 text-sm text-slate-500">
                                    维护用户助手长期生效的行为风格、回答边界和协作方式。
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
                                label=""
                                placeholder="请输入用户 SOUL.md"
                                height="520px"
                                hint="保存时会写入 user_agent_file 中的 SOUL.md。"
                            />
                        </div>
                    </section>
                </div>

                <section class="rounded-xl border border-slate-200 bg-white p-6">
                    <div>
                        <h2 class="text-base font-semibold text-slate-900">可用技能</h2>
                        <p class="mt-1 text-sm text-slate-500">
                            用户可用技能来自角色资源权限，请在角色管理的资源权限页面调整。
                        </p>
                    </div>

                    <div class="mt-4 grid gap-4 lg:grid-cols-2">
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

                        <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
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
                </section>
            </template>

            <section
                v-else
                class="flex flex-1 items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white text-sm text-slate-400"
            >
                请选择用户后查看其记忆配置。
            </section>
        </div>
    </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import MarkdownEditor from '@/components/MarkdownEditor.vue';
import {
    getUserAgentFile,
    getUserAgentSkillPreference,
    updateUserAgentFile,
    updateUserAgentSkillPreference,
} from '@/api/user-agent-config';
import { listUsers } from '@/api/users';
import { clearUserSession } from '@/composables/useCurrentUser';
import { alert } from '@/composables/useModal';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();
const users = ref([]);
const selectedUserId = ref(null);
const profileContent = ref('');
const soulContent = ref('');
const permittedSkills = ref([]);
const enabledSkillIds = ref([]);
const skillPreferenceConfigured = ref(false);
const savingProfile = ref(false);
const savingSoul = ref(false);
const savingSkills = ref(false);

const selectedUser = computed(
    () => users.value.find(item => item.id === selectedUserId.value) || null
);
const enabledSkillIdSet = computed(() => new Set(enabledSkillIds.value));
const enabledSkills = computed(() =>
    permittedSkills.value.filter(skill => enabledSkillIdSet.value.has(skill.id))
);
const disabledSkills = computed(() =>
    permittedSkills.value.filter(skill => !enabledSkillIdSet.value.has(skill.id))
);
const skillPreferenceText = computed(() =>
    selectedUserId.value
        ? skillPreferenceConfigured.value
            ? '个人启用子集'
            : '默认启用全部授权技能'
        : '请选择用户'
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadUsers() {
    const data = await listUsers({ page: 1, pageSize: 1000 }, handleUnauthorized);
    users.value = Array.isArray(data?.items) ? data.items : [];
}

async function loadSelectedUserConfig() {
    if (!selectedUserId.value) {
        profileContent.value = '';
        soulContent.value = '';
        permittedSkills.value = [];
        enabledSkillIds.value = [];
        skillPreferenceConfigured.value = false;
        return;
    }

    const [profileFile, soulFile, skillPreference] = await Promise.all([
        getUserAgentFile(selectedUserId.value, 'PROFILE.md', handleUnauthorized).catch(() => null),
        getUserAgentFile(selectedUserId.value, 'SOUL.md', handleUnauthorized).catch(() => null),
        getUserAgentSkillPreference(selectedUserId.value, handleUnauthorized).catch(() => null),
    ]);

    profileContent.value = profileFile?.content || '';
    soulContent.value = soulFile?.content || '';
    permittedSkills.value = Array.isArray(skillPreference?.permittedSkills)
        ? skillPreference.permittedSkills
        : [];
    enabledSkillIds.value = Array.isArray(skillPreference?.enabledSkillIds)
        ? skillPreference.enabledSkillIds
        : [];
    skillPreferenceConfigured.value = skillPreference?.configured === true;
}

async function saveSelectedUserFile(
    filename,
    content,
    savingState,
    successMessage,
    fallbackMessage
) {
    if (!selectedUserId.value) {
        return;
    }
    savingState.value = true;
    try {
        await updateUserAgentFile(selectedUserId.value, filename, content, handleUnauthorized);
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
    await saveSelectedUserFile(
        'PROFILE.md',
        profileContent.value,
        savingProfile,
        'PROFILE.md 已更新。',
        'PROFILE.md 保存失败'
    );
}

async function handleSaveSoul() {
    await saveSelectedUserFile(
        'SOUL.md',
        soulContent.value,
        savingSoul,
        'SOUL.md 已更新。',
        'SOUL.md 保存失败'
    );
}

async function handleSaveAll() {
    if (!selectedUserId.value || savingProfile.value || savingSoul.value) {
        return;
    }
    savingProfile.value = true;
    savingSoul.value = true;
    try {
        await Promise.all([
            updateUserAgentFile(
                selectedUserId.value,
                'PROFILE.md',
                profileContent.value,
                handleUnauthorized
            ),
            updateUserAgentFile(
                selectedUserId.value,
                'SOUL.md',
                soulContent.value,
                handleUnauthorized
            ),
        ]);
        await alert({
            title: '保存成功',
            message: 'PROFILE.md 与 SOUL.md 已更新。',
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '保存用户记忆配置失败',
        });
    } finally {
        savingProfile.value = false;
        savingSoul.value = false;
    }
}

async function saveSkillPreference(nextEnabledSkillIds) {
    if (!selectedUserId.value || savingSkills.value) {
        return;
    }
    savingSkills.value = true;
    try {
        await updateUserAgentSkillPreference(
            selectedUserId.value,
            nextEnabledSkillIds,
            handleUnauthorized
        );
        enabledSkillIds.value = [...nextEnabledSkillIds];
        skillPreferenceConfigured.value = true;
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

watch(selectedUserId, () => {
    loadSelectedUserConfig();
});

onMounted(async () => {
    await loadUsers();
});
</script>
