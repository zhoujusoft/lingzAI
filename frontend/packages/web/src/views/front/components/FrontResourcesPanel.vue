<template>
    <section class="flex h-full min-h-0 flex-col gap-2 text-strong">
        <!-- Top toolbar: tabs + search -->
        <div class="shrink-0 px-1">
            <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-2 overflow-x-auto">
                    <button
                        v-for="tab in tabs"
                        :key="tab.key"
                        type="button"
                        :class="[
                            'rounded-lg px-3 py-1.5 text-sm font-medium transition-all whitespace-nowrap',
                            activeTab === tab.key
                                ? 'bg-accent-soft text-primary'
                                : 'text-body hover:text-strong',
                        ]"
                        @click="activeTab = tab.key"
                    >
                        {{ tab.label }}
                        <span v-if="tab.count !== undefined" class="ml-1 text-xs opacity-60"
                            >({{ tab.count }})</span
                        >
                    </button>
                </div>

                <div class="relative w-40 shrink-0">
                    <span
                        class="material-symbols-outlined pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-sm text-muted"
                        >search</span
                    >
                    <input
                        v-model.trim="searchKeyword"
                        type="text"
                        placeholder="搜索..."
                        class="w-full rounded-lg border border-border-soft bg-surface py-1.5 pl-7 pr-2 text-sm text-strong outline-none transition focus:border-primary"
                    />
                </div>
            </div>
        </div>

        <!-- Scrollable content -->
        <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto px-1 pb-3 pt-1">
            <!-- Error message -->
            <p
                v-if="errorMessage"
                class="mb-3 rounded-lg border border-danger/15 bg-danger/10 px-3 py-2 text-sm text-danger"
            >
                {{ errorMessage }}
            </p>

            <!-- Loading state -->
            <div v-if="loading" class="flex items-center justify-center py-12 text-muted">
                <span class="material-symbols-outlined mr-2 animate-spin text-xl"
                    >progress_activity</span
                >
                <span class="text-sm">加载中...</span>
            </div>

            <template v-else>
                <!-- No data at all -->
                <div
                    v-if="skills.length === 0 && tools.length === 0"
                    class="flex flex-col items-center justify-center rounded-[28px] border border-dashed border-slate-200 bg-slate-50 px-6 py-14 text-center"
                >
                    <span class="material-symbols-outlined mb-3 text-5xl text-slate-300"
                        >inventory_2</span
                    >
                    <p class="text-sm text-slate-400">暂无资源数据</p>
                </div>

                <template v-else>
                    <!-- Authorized section -->
                    <FrontResourceList
                        title="已授权资源"
                        :is-authorized="true"
                        :skill-items="authorizedSkillItems"
                        :tool-items="authorizedToolItems"
                        :show-skills="showSkills"
                        :show-tools="showTools"
                        empty-icon="search_off"
                        empty-text="未找到匹配的已授权资源"
                        section-class="mb-6"
                    />

                    <!-- Divider -->
                    <div class="my-4 border-t border-border-soft/60" />

                    <!-- Unauthorized section -->
                    <FrontResourceList
                        title="未授权资源"
                        :is-authorized="false"
                        :skill-items="unauthorizedSkillItems"
                        :tool-items="unauthorizedToolItems"
                        :show-skills="showSkills"
                        :show-tools="showTools"
                        empty-icon="search_off"
                        empty-text="未找到未授权资源"
                    />
                </template>
            </template>
        </div>
    </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { currentUserState } from '@/composables/useCurrentUser';
import { listSkillCatalogs } from '@/api/skills';
import { listToolCatalog } from '@/api/tools';
import {
    groupSkillsByCategory,
    groupToolsByType,
    filterUserFacingTools,
} from '@/utils/groupingUtils';
import FrontResourceList from './FrontResourceList.vue';

const emit = defineEmits(['unauthorized']);

// --- State ---
const skills = ref([]);
const tools = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const activeTab = ref('all');
const searchKeyword = ref('');

// --- Computed: user permissions ---
const profile = computed(() => currentUserState.profile);
const unrestricted = computed(() => profile.value?.resourcePermissionUnrestricted ?? false);
const permittedSkillIdSet = computed(() => new Set(profile.value?.permittedSkillIds ?? []));
const permittedToolIdSet = computed(() => new Set(profile.value?.permittedToolIds ?? []));

// --- Computed: tab config ---
const showSkills = computed(() => activeTab.value === 'all' || activeTab.value === 'skills');
const showTools = computed(() => activeTab.value === 'all' || activeTab.value === 'tools');

// --- Computed: search filter ---
const filteredSkills = computed(() => {
    const keyword = (searchKeyword.value || '').toLowerCase();
    return skills.value.filter(skill => {
        const haystack =
            `${skill.displayName || ''} ${skill.description || ''} ${skill.category || ''}`.toLowerCase();
        return !keyword || haystack.includes(keyword);
    });
});

const filteredTools = computed(() => {
    const keyword = (searchKeyword.value || '').toLowerCase();
    return tools.value.filter(tool => {
        const haystack =
            `${tool.displayName || ''} ${tool.name || ''} ${tool.description || ''}`.toLowerCase();
        return !keyword || haystack.includes(keyword);
    });
});

// --- Computed: partition skills by permission, then group by category ---
const authorizedSkillItems = computed(() => {
    if (!showSkills.value) return [];
    const raw = unrestricted.value
        ? filteredSkills.value
        : filteredSkills.value.filter(s => permittedSkillIdSet.value.has(s.id));
    return groupSkillsByCategory(raw);
});

const unauthorizedSkillItems = computed(() => {
    if (!showSkills.value) return [];
    if (unrestricted.value) return [];
    const raw = filteredSkills.value.filter(s => !permittedSkillIdSet.value.has(s.id));
    return groupSkillsByCategory(raw);
});

// --- Helper: check if tool is authorized ---
function isToolAuthorized(tool) {
    // 全局可用的工具始终视为已授权
    if (tool.enabledGlobal) {
        return true;
    }
    return permittedToolIdSet.value.has(tool.id);
}

// --- Computed: partition tools by permission, then group by type ---
const authorizedToolItems = computed(() => {
    if (!showTools.value) return [];
    const raw = unrestricted.value
        ? filteredTools.value
        : filteredTools.value.filter(t => isToolAuthorized(t));
    return groupToolsByType(raw);
});

const unauthorizedToolItems = computed(() => {
    if (!showTools.value) return [];
    if (unrestricted.value) return [];
    const raw = filteredTools.value.filter(t => !isToolAuthorized(t));
    return groupToolsByType(raw);
});

// --- Computed: tabs ---
const tabs = computed(() => [
    { key: 'all', label: '全部', count: skills.value.length + tools.value.length },
    { key: 'skills', label: '技能', count: skills.value.length },
    { key: 'tools', label: '工具', count: tools.value.length },
]);

// --- Data loading ---
onMounted(async () => {
    loading.value = true;
    errorMessage.value = '';
    try {
        const [skillList, toolList] = await Promise.all([
            listSkillCatalogs({ visibleOnly: true }, () => emit('unauthorized')),
            listToolCatalog({}, () => emit('unauthorized')),
        ]);
        skills.value = Array.isArray(skillList) ? skillList : [];
        tools.value = filterUserFacingTools(toolList);
    } catch (error) {
        errorMessage.value = error.message || '加载失败';
    } finally {
        loading.value = false;
    }
});
</script>
