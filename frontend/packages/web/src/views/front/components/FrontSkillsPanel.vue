<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="sticky top-0 z-10 border-b border-slate-200 bg-slate-50/90 px-8 py-6 backdrop-blur-md">
            <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div>
                    <h2 class="text-2xl font-bold text-slate-900">技能市场</h2>
                    <p class="mt-1 text-sm text-slate-500">按业务能力发现技能，并一键进入对应技能对话。</p>
                </div>
                <div class="relative w-full lg:w-80">
                    <span
                        class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        >search</span
                    >
                    <input
                        v-model.trim="searchKeyword"
                        type="text"
                        placeholder="搜索技能名称或功能..."
                        class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-blue-100"
                    />
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 space-y-10 overflow-y-auto px-8 pb-12 pt-8">
            <section>
                <div class="mb-4 flex items-center justify-between">
                    <div>
                        <h3 class="flex items-center text-lg font-bold text-slate-900">
                            <span class="material-symbols-outlined mr-2 text-amber-400">rocket_launch</span>
                            推荐技能
                        </h3>
                        <p class="mt-1 text-xs text-slate-400">
                            {{ hasPersonalizedRecommendations ? '根据你的最近使用习惯优先推荐' : '基于当前可用技能为你优先推荐' }}
                        </p>
                    </div>
                </div>
                <div v-if="featuredSkills.length" class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
                    <article
                        v-for="(skill, index) in featuredSkills"
                        :key="`featured-${skill.id}`"
                        class="group flex h-[240px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white p-4 transition-colors duration-300 hover:border-primary/30"
                    >
                        <div class="flex flex-1 items-start gap-3">
                            <div
                                :class="[
                                    'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-white',
                                    gradientSet[index % gradientSet.length],
                                ]"
                            >
                                <span class="material-symbols-outlined text-2xl">{{
                                    iconSet[index % iconSet.length]
                                }}</span>
                            </div>
                            <div class="min-w-0 flex-1">
                                <h4 class="truncate text-base font-bold text-slate-900">{{ skill.displayName }}</h4>
                                <p class="mt-2 line-clamp-3 min-h-[3.5rem] overflow-hidden text-sm leading-5 text-slate-500">
                                    {{ skill.description }}
                                </p>
                            </div>
                        </div>
                        <div class="mt-3 flex flex-wrap gap-2">
                            <span
                                v-if="skill.recommendationReason"
                                class="rounded-md bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700"
                            >
                                {{ skill.recommendationReason }}
                            </span>
                            <span class="rounded-md bg-blue-50 px-2 py-0.5 text-xs font-medium text-primary">
                                {{ skill.category || '通用能力' }}
                            </span>
                        </div>
                        <div class="mt-auto flex gap-3 pt-3">
                            <button
                                type="button"
                                class="flex-1 rounded-xl bg-primary py-2 font-medium text-white transition-colors hover:bg-blue-700"
                                @click="openSkillChat(skill)"
                            >
                                进入对话
                            </button>
                        </div>
                    </article>
                </div>
                <div
                    v-else
                    class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-slate-400"
                >
                    <span class="material-symbols-outlined mb-2 text-3xl">widgets</span>
                    <p class="text-sm font-medium">暂无可用技能</p>
                </div>
            </section>

            <section>
                <div class="mb-8 flex items-center justify-between border-b border-slate-200">
                    <div class="flex gap-8 overflow-x-auto">
                        <button
                            v-for="tab in categories"
                            :key="tab"
                            type="button"
                            :class="[
                                'whitespace-nowrap border-b-2 pb-4 text-sm font-medium transition-colors',
                                activeTab === tab
                                    ? 'border-primary font-bold text-primary'
                                    : 'border-transparent text-slate-500 hover:text-slate-800',
                            ]"
                            @click="activeTab = tab"
                        >
                            {{ tab }}
                        </button>
                    </div>
                    <div class="pb-4 text-xs font-medium text-slate-400">点击卡片即可进入技能对话</div>
                </div>

                <p v-if="skillError" class="mb-5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                    {{ skillError }}
                </p>

                <div class="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
                    <article
                        v-for="(skill, index) in displayedSkills"
                        :key="skill.id"
                        class="group flex h-[272px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white transition-colors hover:border-primary/40"
                    >
                        <div class="flex flex-1 flex-col p-4">
                            <div class="mb-3 flex items-start justify-between">
                                <div
                                    :class="[
                                        'flex h-12 w-12 items-center justify-center rounded-xl text-white shadow-md',
                                        gradientSet[index % gradientSet.length],
                                    ]"
                                >
                                    <span class="material-symbols-outlined text-2xl">{{
                                        iconSet[index % iconSet.length]
                                    }}</span>
                                </div>
                                <div class="flex gap-2">
                                    <button
                                        type="button"
                                        class="rounded-lg bg-slate-100 p-2 text-slate-500"
                                    >
                                        <span class="material-symbols-outlined text-lg">smart_toy</span>
                                    </button>
                                </div>
                            </div>
                            <h4 class="flex items-center text-lg font-bold text-slate-900">
                                {{ skill.displayName }}
                            </h4>
                            <p class="mt-2 line-clamp-3 min-h-[3.5rem] overflow-hidden text-sm leading-5 text-slate-500">
                                {{ skill.description }}
                            </p>
                            <div class="mt-4 flex flex-wrap gap-2">
                                <span
                                    class="rounded-md bg-blue-50 px-2 py-0.5 text-xs font-medium text-primary"
                                >
                                    {{ skill.category || '通用能力' }}
                                </span>
                            </div>
                        </div>
                        <div class="mt-auto flex items-center justify-between border-t border-slate-100 px-4 pb-4 pt-2">
                            <div class="flex min-w-0 items-center gap-2">
                                <div class="h-6 w-6 rounded-full bg-slate-200"></div>
                                <span class="truncate text-xs text-slate-400">{{ skill.category || '通用能力' }} · {{ dateSet[index % dateSet.length] }}</span>
                            </div>
                            <button
                                type="button"
                                class="shrink-0 rounded-lg bg-primary/10 px-4 py-1 text-sm font-semibold text-primary transition-colors hover:bg-primary hover:text-white"
                                @click="openSkillChat(skill)"
                            >
                                进入对话
                            </button>
                        </div>
                    </article>
                </div>
            </section>
        </div>
    </section>
</template>

<script>
import { listSkillCatalogs } from '@/api/skills';

export default {
    name: 'FrontSkillsPanel',
    emits: ['unauthorized', 'open-skill-chat'],
    data() {
        return {
            skills: [],
            activeTab: '全部技能',
            searchKeyword: '',
            iconSet: ['translate', 'code', 'palette', 'auto_fix_high', 'table_chart', 'movie'],
            gradientSet: [
                'bg-gradient-to-br from-cyan-400 to-blue-500 shadow-cyan-500/20',
                'bg-gradient-to-br from-amber-400 to-orange-500 shadow-amber-500/20',
                'bg-gradient-to-br from-indigo-400 to-purple-500 shadow-indigo-500/20',
                'bg-gradient-to-br from-rose-400 to-red-500 shadow-rose-500/20',
                'bg-gradient-to-br from-emerald-400 to-teal-500 shadow-emerald-500/20',
                'bg-gradient-to-br from-sky-400 to-blue-600 shadow-sky-500/20',
            ],
            dateSet: ['03-11', '03-10', '03-09', '03-08', '03-07', '03-06'],
            skillError: '',
        };
    },
    computed: {
        categories() {
            const dynamic = this.skills
                .map(skill => skill.category || '')
                .filter(Boolean);
            return ['全部技能', ...Array.from(new Set(dynamic))];
        },
        displayedSkills() {
            const keyword = (this.searchKeyword || '').toLowerCase();
            return this.skills.filter(skill => {
                const matchesTab = this.activeTab === '全部技能' || (skill.category || '') === this.activeTab;
                const haystack = `${skill.displayName || ''} ${skill.description || ''}`.toLowerCase();
                const matchesKeyword = !keyword || haystack.includes(keyword);
                return matchesTab && matchesKeyword;
            });
        },
        featuredSkills() {
            return [...this.skills]
                .sort((left, right) => {
                    const scoreDiff =
                        Number(right?.recommendationScore || 0) - Number(left?.recommendationScore || 0);
                    if (scoreDiff !== 0) {
                        return scoreDiff;
                    }

                    const usageDiff = Number(right?.usageCount || 0) - Number(left?.usageCount || 0);
                    if (usageDiff !== 0) {
                        return usageDiff;
                    }

                    const sortDiff = Number(left?.sortOrder || 0) - Number(right?.sortOrder || 0);
                    if (sortDiff !== 0) {
                        return sortDiff;
                    }

                    return String(left?.displayName || '').localeCompare(
                        String(right?.displayName || ''),
                        'zh-CN',
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
        async fetchSkills() {
            this.skillError = '';
            try {
                const list = await listSkillCatalogs(
                    {
                        visibleOnly: true,
                    },
                    () => this.$emit('unauthorized'),
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
