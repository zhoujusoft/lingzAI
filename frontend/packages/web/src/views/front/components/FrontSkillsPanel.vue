<template>
    <section class="flex h-full min-h-0 flex-col gap-2 text-strong">
        <div class="shrink-0 px-1">
            <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-2 overflow-x-auto">
                    <button
                        v-for="tab in categories"
                        :key="tab"
                        type="button"
                        :class="[
                            'rounded-lg px-3 py-1.5 text-sm font-medium transition-all whitespace-nowrap',
                            activeTab === tab
                                ? 'bg-accent-soft text-primary'
                                : 'text-body hover:text-strong',
                        ]"
                        @click="activeTab = tab"
                    >
                        {{ tab }}
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

        <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto px-1 pb-3 pt-1">
            <p
                v-if="skillError"
                class="mb-3 rounded-lg border border-danger/15 bg-danger/10 px-3 py-2 text-sm text-danger"
            >
                {{ skillError }}
            </p>

            <div class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
                <article
                    v-for="skill in displayedSkills"
                    :key="skill.id"
                    class="front-card group flex flex-col overflow-hidden transition-colors hover:border-primary/40"
                >
                    <div class="flex flex-1 flex-col p-3">
                        <div class="flex items-start gap-2.5">
                            <div
                                :class="[
                                    'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-white',
                                    getSkillGradient(skill),
                                ]"
                            >
                                <span class="material-symbols-outlined text-lg">{{
                                    getSkillIcon(skill)
                                }}</span>
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="flex items-center gap-1.5">
                                    <h4 class="truncate text-sm font-semibold text-strong">
                                        {{ skill.displayName }}
                                    </h4>
                                    <span
                                        v-if="skill.recommendationScore > 0"
                                        class="shrink-0 rounded bg-warning/15 px-1 py-0.5 text-[9px] font-medium text-warning"
                                    >
                                        推荐
                                    </span>
                                </div>
                                <p
                                    class="mt-0.5 line-clamp-2 overflow-hidden text-xs leading-4 text-body"
                                >
                                    {{ skill.description }}
                                </p>
                            </div>
                        </div>
                        <div class="mt-2 flex flex-wrap gap-1">
                            <span
                                class="rounded bg-accent-soft px-1.5 py-0.5 text-[10px] font-medium text-primary"
                            >
                                {{ skill.category || '通用能力' }}
                            </span>
                            <span
                                v-if="skill.version"
                                class="rounded bg-warning/10 px-1.5 py-0.5 text-[10px] font-medium text-warning"
                            >
                                v{{ skill.version }}
                            </span>
                        </div>
                    </div>
                    <div
                        class="flex items-center justify-end border-t border-border-soft/50 px-3 py-2"
                    >
                        <button
                            type="button"
                            class="rounded-lg bg-accent-soft px-3 py-1 text-xs font-medium text-primary transition-colors hover:bg-primary hover:text-white"
                            @click="openSkillChat(skill)"
                        >
                            进入对话
                        </button>
                    </div>
                </article>
            </div>

            <div
                v-if="!displayedSkills.length && !skillError"
                class="flex flex-col items-center justify-center py-12 text-muted"
            >
                <span class="material-symbols-outlined mb-2 text-3xl">search_off</span>
                <p class="text-sm">未找到匹配的技能</p>
            </div>
        </div>
    </section>
</template>

<script>
import { listSkillCatalogs } from '@/api/skills';
import {
    getSkillIconGradientClass,
    getSkillIconSwatchClass,
    resolveSkillIcon,
} from '@/utils/skillVisuals';

export default {
    name: 'FrontSkillsPanel',
    emits: ['unauthorized', 'open-skill-chat'],
    data() {
        return {
            skills: [],
            activeTab: '全部技能',
            searchKeyword: '',
            dateSet: ['03-11', '03-10', '03-09', '03-08', '03-07', '03-06'],
            skillError: '',
        };
    },
    computed: {
        categories() {
            const dynamic = this.skills.map(skill => skill.category || '').filter(Boolean);
            return ['全部技能', ...Array.from(new Set(dynamic))];
        },
        displayedSkills() {
            const keyword = (this.searchKeyword || '').toLowerCase();
            return this.skills.filter(skill => {
                const matchesTab =
                    this.activeTab === '全部技能' || (skill.category || '') === this.activeTab;
                const haystack =
                    `${skill.displayName || ''} ${skill.description || ''}`.toLowerCase();
                const matchesKeyword = !keyword || haystack.includes(keyword);
                return matchesTab && matchesKeyword;
            });
        },
        featuredSkills() {
            return [...this.skills]
                .sort((left, right) => {
                    const scoreDiff =
                        Number(right?.recommendationScore || 0) -
                        Number(left?.recommendationScore || 0);
                    if (scoreDiff !== 0) {
                        return scoreDiff;
                    }

                    const usageDiff =
                        Number(right?.usageCount || 0) - Number(left?.usageCount || 0);
                    if (usageDiff !== 0) {
                        return usageDiff;
                    }

                    const sortDiff = Number(left?.sortOrder || 0) - Number(right?.sortOrder || 0);
                    if (sortDiff !== 0) {
                        return sortDiff;
                    }

                    return String(left?.displayName || '').localeCompare(
                        String(right?.displayName || ''),
                        'zh-CN'
                    );
                })
                .slice(0, 4);
        },
        hasPersonalizedRecommendations() {
            return this.skills.some(skill => Number(skill?.recommendationScore || 0) > 0);
        },
    },
    mounted() {
        this.fetchSkills();
    },
    methods: {
        getSkillIcon(skill) {
            return resolveSkillIcon(skill?.icon);
        },
        getSkillGradient(skill) {
            return getSkillIconGradientClass(skill?.iconColor);
        },
        getSkillSwatch(skill) {
            return getSkillIconSwatchClass(skill?.iconColor);
        },
        getSkillMetaLine(skill) {
            const parts = [];
            if (skill?.author) {
                parts.push(skill.author);
            }
            if (skill?.version) {
                parts.push(`v${skill.version}`);
            }
            return parts.join(' · ');
        },
        async fetchSkills() {
            this.skillError = '';
            try {
                const list = await listSkillCatalogs(
                    {
                        visibleOnly: true,
                    },
                    () => this.$emit('unauthorized')
                );
                this.skills = Array.isArray(list) ? list : [];
            } catch (error) {
                this.skillError = error.message || '加载失败';
            }
        },
        openSkillChat(skill) {
            this.$emit('open-skill-chat', skill);
        },
    },
};
</script>
