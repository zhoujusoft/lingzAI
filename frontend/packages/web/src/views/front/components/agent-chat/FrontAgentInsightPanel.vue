<script setup>
import { computed, onMounted, reactive } from 'vue';
import { ensureAgentConfigLoaded, agentConfigState } from '@/composables/useAgentConfig';
import { AGENT_INSIGHT_SECTIONS } from '@/utils/agentInsight';
import { buildSkillMentionLookup } from '@/utils/skillMention';

const emit = defineEmits(['close', 'select-insight-item']);

const sections = AGENT_INSIGHT_SECTIONS;

const openState = reactive(Object.fromEntries(sections.map(section => [section.id, true])));

const configuredSkillLookup = computed(() => buildSkillMentionLookup(agentConfigState.skills));

function normalizeSkillName(value) {
    return String(value || '')
        .trim()
        .toLowerCase();
}

function toggleSection(sectionId) {
    openState[sectionId] = !openState[sectionId];
}

function resolveConfiguredSkill(group) {
    const normalizedSkillName = normalizeSkillName(group?.skill);
    if (!normalizedSkillName) {
        return null;
    }
    const matchedFromLookup = configuredSkillLookup.value.get(normalizedSkillName);
    if (matchedFromLookup?.id) {
        return matchedFromLookup;
    }
    return (Array.isArray(agentConfigState.skills) ? agentConfigState.skills : []).find(skill =>
        [skill?.displayName, skill?.runtimeSkillName].some(
            candidate => normalizeSkillName(candidate) === normalizedSkillName
        )
    );
}

function isGroupClickable(group) {
    return Boolean(resolveConfiguredSkill(group)?.id);
}

/**
 * 处理重点事项项点击
 * 只有配置了 skill 的 group 才能点击
 * item: { msg: string, query?: string }
 * - msg: 显示文字
 * - query: 点击时带入会话的内容（可选，默认使用 msg）
 */
function handleItemClick(group, item) {
    const skill = resolveConfiguredSkill(group);
    if (!skill?.id) {
        return;
    }
    emit('select-insight-item', {
        skill,
        skillName: group.skill,
        messageContent: item.query || item.msg,
    });
}

onMounted(() => {
    ensureAgentConfigLoaded().catch(() => {});
});
</script>

<template>
    <aside class="insight-panel flex h-full flex-col overflow-hidden">
        <div class="insight-header flex items-center justify-between px-6 py-4">
            <div class="flex min-w-0 items-center gap-4">
                <div
                    class="insight-header-icon flex h-11 w-11 items-center justify-center rounded-xl"
                >
                    <span class="material-symbols-outlined text-xl text-white">emoji_objects</span>
                </div>
                <div>
                    <h2 class="truncate text-lg font-bold leading-tight text-slate-900">
                        AI洞察提醒
                    </h2>
                    <p class="mt-1 text-xs text-slate-400">今日工作重点与风险预警</p>
                </div>
            </div>

            <button
                type="button"
                class="insight-close-btn"
                aria-label="关闭 AI 洞察提醒"
                @click="emit('close')"
            >
                <span class="material-symbols-outlined text-xl">close</span>
            </button>
        </div>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-4 pb-4">
            <article
                v-for="section in sections"
                :key="section.id"
                class="insight-card mb-4 last:mb-0"
            >
                <button
                    type="button"
                    class="flex w-full items-center justify-between gap-3 text-left"
                    @click="toggleSection(section.id)"
                >
                    <div class="flex items-center gap-3">
                        <span
                            class="insight-section-index"
                            :class="{
                                'insight-section-index--blue': section.tone === 'blue',
                                'insight-section-index--amber': section.tone === 'amber',
                                'insight-section-index--rose': section.tone === 'rose',
                            }"
                        >
                            {{ section.index }}
                        </span>
                        <h3 class="text-[16px] font-bold tracking-tight text-slate-900">
                            {{ section.title }}
                        </h3>
                    </div>

                    <span
                        class="material-symbols-outlined text-[20px] text-slate-400 transition-transform duration-200"
                        :class="{ 'rotate-180': openState[section.id] }"
                    >
                        expand_less
                    </span>
                </button>

                <div v-if="openState[section.id]" class="mt-5 space-y-6">
                    <section
                        v-for="group in section.groups"
                        :key="group.title"
                        class="insight-group space-y-3"
                    >
                        <div class="flex items-center gap-3">
                            <span
                                class="material-symbols-outlined text-[22px]"
                                :class="{
                                    'text-sky-500': section.tone === 'blue',
                                    'text-amber-500': section.tone === 'amber',
                                    'text-rose-500': section.tone === 'rose',
                                }"
                            >
                                {{ group.icon }}
                            </span>
                            <h4 class="text-[14px] font-bold text-slate-900">{{ group.title }}</h4>
                        </div>

                        <ul class="space-y-2.5 pl-4">
                            <li
                                v-for="item in group.items"
                                :key="item.msg"
                                class="relative pl-5 text-[13px] leading-6"
                            >
                                <span
                                    class="absolute left-0 top-[10px] h-1.5 w-1.5 rounded-full"
                                    :class="{
                                        'bg-sky-400': section.tone === 'blue',
                                        'bg-amber-400': section.tone === 'amber',
                                        'bg-rose-400': section.tone === 'rose',
                                    }"
                                ></span>
                                <button
                                    v-if="isGroupClickable(group)"
                                    type="button"
                                    class="insight-item-button insight-item--clickable"
                                    :title="`点击追加到对话框：${group.skill}`"
                                    @click="handleItemClick(group, item)"
                                >
                                    {{ item.msg }}
                                </button>
                                <span v-else class="block text-left text-slate-600">
                                    {{ item.msg }}
                                </span>
                            </li>
                        </ul>
                    </section>
                </div>
            </article>
        </div>
    </aside>
</template>

<style scoped>
.insight-panel {
    background: #ffffff;
}

.insight-header {
    background: #fafafa;
    border-bottom: 1px solid #f1f5f9;
}

.insight-header-icon {
    background: linear-gradient(135deg, #6da8ff, #7b5cff);
}

.insight-close-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    border: none;
    background: transparent;
    color: #94a3b8;
    transition: all 0.15s ease;
    flex-shrink: 0;
}

.insight-close-btn:hover {
    background: rgba(241, 245, 249, 0.8);
    color: #64748b;
}

.insight-card {
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.75);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    padding: 20px;
    border: 1px solid rgba(255, 255, 255, 0.4);
    box-shadow:
        0 2px 4px rgba(0, 0, 0, 0.04),
        inset 0 1px 0 rgba(255, 255, 255, 0.5);
    transition: all 0.2s ease;
}

.insight-card:hover {
    box-shadow:
        0 4px 12px rgba(0, 0, 0, 0.06),
        inset 0 1px 0 rgba(255, 255, 255, 0.6);
    border-color: rgba(99, 102, 241, 0.15);
}

.insight-section-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 30px;
    height: 30px;
    border-radius: 999px;
    font-size: 14px;
    font-weight: 700;
    color: #fff;
}

.insight-section-index--blue {
    background: linear-gradient(315deg, #60a5fa 0%, #3b82f6 100%);
}

.insight-section-index--amber {
    background: linear-gradient(315deg, #fbbf24 0%, #f59e0b 100%);
}

.insight-section-index--rose {
    background: linear-gradient(315deg, #fb7185 0%, #f43f5e 100%);
}

.insight-group + .insight-group {
    padding-top: 18px;
    border-top: 1px solid rgba(226, 232, 240, 0.72);
}

.insight-item-button {
    display: block;
    width: 100%;
    padding: 0;
    border: none;
    background: transparent;
    text-align: left;
    font: inherit;
    color: #475569;
}

.insight-item--clickable {
    cursor: pointer;
    transition: color 0.15s ease;
}

.insight-item--clickable:hover {
    color: #1e293b;
}

.insight-item--clickable:focus-visible {
    outline: 2px solid rgba(45, 212, 191, 0.35);
    outline-offset: 3px;
    border-radius: 6px;
}
</style>
