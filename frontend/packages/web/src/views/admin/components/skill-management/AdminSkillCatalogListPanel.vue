<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
    confirmSkillPackageImport,
    exportSkillPackage,
    listSkillCatalogs,
    previewSkillPackageImport,
    refreshSkillPackages,
} from '@/api/skills';
import { alert, confirm } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';

const emit = defineEmits(['open-skill-detail']);

const router = useRouter();
const fileInputRef = ref(null);
const loading = ref(false);
const importing = ref(false);
const refreshing = ref(false);
const exportingSkillId = ref(null);
const loadError = ref('');
const searchKeyword = ref('');
const visibilityFilter = ref('ALL');
const categoryFilter = ref('ALL');
const skills = ref([]);

const skillIcons = ['grid_view', 'smart_toy', 'rocket_launch', 'inventory_2', 'dataset', 'hub'];
const skillGradients = [
    'from-blue-500 via-blue-500 to-cyan-400',
    'from-indigo-500 via-indigo-500 to-sky-400',
    'from-emerald-500 via-teal-500 to-cyan-400',
    'from-amber-500 via-orange-500 to-rose-400',
    'from-slate-600 via-slate-500 to-slate-400',
    'from-violet-500 via-fuchsia-500 to-pink-400',
];

const skillCategories = computed(() => {
    const categories = skills.value
        .map(item => item.category || '')
        .filter(Boolean);
    return ['ALL', ...Array.from(new Set(categories))];
});

const filteredSkills = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    return skills.value.filter(item => {
        const matchesKeyword = !keyword
            || `${item.displayName || ''} ${item.description || ''} ${item.runtimeSkillName || ''}`
                .toLowerCase()
                .includes(keyword);
        const matchesVisibility = visibilityFilter.value === 'ALL'
            || (visibilityFilter.value === 'VISIBLE' && item.visible)
            || (visibilityFilter.value === 'HIDDEN' && !item.visible);
        const matchesCategory = categoryFilter.value === 'ALL'
            || (item.category || '') === categoryFilter.value;
        return matchesKeyword && matchesVisibility && matchesCategory;
    });
});

const visibleSkillCount = computed(() => skills.value.filter(item => item.visible).length);
const hiddenSkillCount = computed(() => skills.value.filter(item => !item.visible).length);
const businessCategoryCount = computed(() => skillCategories.value.filter(item => item !== 'ALL').length);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function getSkillIcon(skillId) {
    if (!skillId) {
        return skillIcons[0];
    }
    return skillIcons[skillId % skillIcons.length];
}

function getSkillGradient(skillId) {
    if (!skillId) {
        return skillGradients[0];
    }
    return skillGradients[skillId % skillGradients.length];
}

async function loadSkills() {
    loading.value = true;
    loadError.value = '';
    try {
        const data = await listSkillCatalogs({}, handleUnauthorized);
        skills.value = Array.isArray(data) ? data : [];
    } catch (error) {
        loadError.value = error?.message || '加载技能数据失败';
    } finally {
        loading.value = false;
    }
}

function openSkillDetail(skill) {
    emit('open-skill-detail', skill);
}

function downloadBlobFile(filename, blob) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
}

async function handleExportSkill(item) {
    if (!item?.id || exportingSkillId.value) {
        return;
    }
    exportingSkillId.value = item.id;
    try {
        const result = await exportSkillPackage(item.id, handleUnauthorized);
        downloadBlobFile(result.filename, result.blob);
    } catch (error) {
        await alert({
            title: '导出失败',
            message: error?.message || '技能包导出失败',
        });
    } finally {
        exportingSkillId.value = null;
    }
}

async function handleRefreshRegistry() {
    if (refreshing.value) {
        return;
    }
    refreshing.value = true;
    try {
        const result = await refreshSkillPackages(handleUnauthorized);
        await loadSkills();
        await alert({
            title: '刷新完成',
            message: `已全量重扫 ${result?.filesystemSkillCount ?? 0} 个 filesystem skill，当前运行时技能 ${result?.runtimeSkillCount ?? 0} 个。`,
        });
    } catch (error) {
        await alert({
            title: '刷新失败',
            message: error?.message || '刷新技能目录失败',
        });
    } finally {
        refreshing.value = false;
    }
}

function triggerImport() {
    if (importing.value) {
        return;
    }
    fileInputRef.value?.click();
}

function summarizePreview(preview) {
    const counts = {
        ADDED: 0,
        UPDATED: 0,
        REMOVED: 0,
        UNCHANGED: 0,
    };
    for (const item of preview?.fileChanges || []) {
        if (counts[item.operation] != null) {
            counts[item.operation] += 1;
        }
    }
    const lines = [
        `包标识：${preview?.packageId || '-'}`,
        `运行时技能：${preview?.runtimeSkillName || '-'}`,
        `导入模式：${preview?.installMode || '-'}`,
        `版本：${preview?.installedVersion ? `${preview.installedVersion} -> ${preview.importedVersion}` : preview?.importedVersion || '-'}`,
        `文件变更：新增 ${counts.ADDED} / 更新 ${counts.UPDATED} / 删除 ${counts.REMOVED} / 不变 ${counts.UNCHANGED}`,
    ];
    if (Number(preview?.unmanagedFileCount || 0) > 0) {
        lines.push(`非受管本地文件：${preview.unmanagedFileCount} 个（导入时保留）`);
    }
    if (Array.isArray(preview?.warnings) && preview.warnings.length) {
        lines.push('');
        lines.push(`提示：${preview.warnings.join('；')}`);
    }
    return lines.join('\n');
}

async function handleImport(event) {
    const file = event?.target?.files?.[0];
    event.target.value = '';
    if (!file) {
        return;
    }

    importing.value = true;
    try {
        const preview = await previewSkillPackageImport(file, handleUnauthorized);
        const shouldContinue = await confirm({
            title: preview?.requiresDowngradeConfirmation ? '确认导入降级包' : '确认导入技能包',
            message: summarizePreview(preview),
            confirmText: preview?.requiresDowngradeConfirmation ? '确认降级导入' : '确认导入',
            cancelText: '取消',
            destructive: Boolean(preview?.requiresDowngradeConfirmation),
        });
        if (!shouldContinue) {
            return;
        }

        const result = await confirmSkillPackageImport(
            file,
            Boolean(preview?.requiresDowngradeConfirmation),
            handleUnauthorized,
        );
        await alert({
            title: '导入完成',
            message: [
                `包标识：${result?.packageId || '-'}`,
                `安装模式：${result?.installMode || '-'}`,
                `安装状态：${result?.installStatus || '-'}`,
                `依赖安装：${result?.dependencyStatus || '-'}`,
                result?.backupDir ? `备份目录：${result.backupDir}` : '',
                '',
                '文件已写入技能目录。请再点击“刷新技能目录”使运行时生效。',
            ].filter(Boolean).join('\n'),
        });
    } catch (error) {
        await alert({
            title: '导入失败',
            message: error?.message || '技能包导入失败',
        });
    } finally {
        importing.value = false;
    }
}

onMounted(() => {
    loadSkills();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div class="flex flex-col gap-6 2xl:flex-row 2xl:items-start 2xl:justify-between">
                <div>
                    <p class="text-xs font-semibold tracking-[0.32em] text-slate-400">SKILLS</p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">技能管理</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        按业务能力查看技能目录，再进入详情页编辑展示信息。技能包支持导入预检、确认导入和手动刷新技能目录生效。
                    </p>
                </div>

                <div class="flex flex-wrap items-center gap-3">
                    <button
                        type="button"
                        class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="importing"
                        @click="triggerImport"
                    >
                        {{ importing ? '导入中...' : '导入技能包' }}
                    </button>
                    <button
                        type="button"
                        class="rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="refreshing"
                        @click="handleRefreshRegistry"
                    >
                        {{ refreshing ? '刷新中...' : '刷新技能目录' }}
                    </button>
                    <input
                        ref="fileInputRef"
                        type="file"
                        accept=".zip,application/zip"
                        class="hidden"
                        @change="handleImport"
                    />
                </div>
            </div>

            <div class="mt-6 grid gap-3 sm:grid-cols-4">
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">技能总数</p>
                    <p class="mt-2 text-2xl font-bold text-slate-900">{{ skills.length }}</p>
                    <p class="mt-1 text-xs text-slate-500">当前镜像技能总数</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">已上架</p>
                    <p class="mt-2 text-2xl font-bold text-emerald-600">{{ visibleSkillCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">前台技能市场可见</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">已隐藏</p>
                    <p class="mt-2 text-2xl font-bold text-slate-700">{{ hiddenSkillCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">当前未在前台展示</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">业务分类</p>
                    <p class="mt-2 text-2xl font-bold text-blue-600">{{ businessCategoryCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">按业务能力组织的分类数</p>
                </article>
            </div>

            <div class="mt-6 flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div class="flex flex-wrap gap-2">
                    <button
                        type="button"
                        class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                        :class="visibilityFilter === 'ALL' ? 'bg-blue-600 text-white shadow-sm shadow-blue-100' : 'border border-slate-200 bg-white text-slate-600 hover:border-blue-200 hover:text-blue-600'"
                        @click="visibilityFilter = 'ALL'"
                    >
                        全部
                    </button>
                    <button
                        type="button"
                        class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                        :class="visibilityFilter === 'VISIBLE' ? 'bg-blue-600 text-white shadow-sm shadow-blue-100' : 'border border-slate-200 bg-white text-slate-600 hover:border-blue-200 hover:text-blue-600'"
                        @click="visibilityFilter = 'VISIBLE'"
                    >
                        已上架
                    </button>
                    <button
                        type="button"
                        class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                        :class="visibilityFilter === 'HIDDEN' ? 'bg-blue-600 text-white shadow-sm shadow-blue-100' : 'border border-slate-200 bg-white text-slate-600 hover:border-blue-200 hover:text-blue-600'"
                        @click="visibilityFilter = 'HIDDEN'"
                    >
                        已隐藏
                    </button>
                </div>

                <div class="relative w-full max-w-sm">
                    <span class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                        search
                    </span>
                    <input
                        v-model.trim="searchKeyword"
                        type="text"
                        placeholder="搜索技能名称、描述或运行时标识"
                        class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    />
                </div>
            </div>

            <div class="mt-4 flex flex-wrap gap-2">
                <button
                    v-for="category in skillCategories"
                    :key="category"
                    type="button"
                    class="rounded-full px-3 py-1.5 text-xs font-medium transition-colors"
                    :class="categoryFilter === category ? 'bg-slate-900 text-white' : 'border border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:text-slate-700'"
                    @click="categoryFilter = category"
                >
                    {{ category === 'ALL' ? '全部分类' : category }}
                </button>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto p-6">
            <p v-if="loadError" class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                {{ loadError }}
            </p>

            <div
                v-if="loading"
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                加载中...
            </div>

            <div v-else class="grid gap-5 md:grid-cols-2 2xl:grid-cols-3">
                <article
                    v-for="item in filteredSkills"
                    :key="item.id"
                    class="flex h-[292px] flex-col overflow-hidden rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                >
                    <div class="flex flex-1 items-start gap-4">
                        <div
                            class="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-lg"
                            :class="getSkillGradient(item.id)"
                        >
                            <span class="material-symbols-outlined text-[28px]">{{ getSkillIcon(item.id) }}</span>
                        </div>
                        <div class="min-w-0 flex-1">
                            <div class="flex items-start justify-between gap-3">
                                <div class="min-w-0">
                                    <h3 class="truncate text-lg font-bold text-slate-900">{{ item.displayName }}</h3>
                                    <p class="mt-1 truncate text-xs text-slate-400">{{ item.runtimeSkillName }}</p>
                                </div>
                                <span
                                    class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                    :class="item.visible ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-500'"
                                >
                                    {{ item.visible ? '已上架' : '已隐藏' }}
                                </span>
                            </div>
                            <p class="mt-3 line-clamp-3 min-h-[4.5rem] overflow-hidden text-sm leading-6 text-slate-500">
                                {{ item.description }}
                            </p>
                            <div class="mt-4 flex flex-wrap gap-2">
                                <span class="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-600">
                                    {{ item.category || '通用能力' }}
                                </span>
                                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-500">
                                    {{ item.runtimeTools.length }} 个运行时工具
                                </span>
                            </div>
                        </div>
                    </div>

                    <div class="mt-auto flex items-center justify-between border-t border-slate-100 pt-4">
                        <p class="text-xs text-slate-400">点击查看详情并编辑展示信息</p>
                        <div class="flex items-center gap-2">
                            <button
                                type="button"
                                class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                                :disabled="exportingSkillId === item.id || item.source !== 'filesystem'"
                                @click="handleExportSkill(item)"
                            >
                                {{ exportingSkillId === item.id ? '导出中...' : '导出技能包' }}
                            </button>
                            <button
                                type="button"
                                class="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700"
                                @click="openSkillDetail(item)"
                            >
                                查看详情
                            </button>
                        </div>
                    </div>
                </article>

                <div
                    v-if="!filteredSkills.length"
                    class="md:col-span-2 2xl:col-span-3 rounded-[28px] border border-dashed border-slate-200 bg-white px-6 py-14 text-center text-sm text-slate-400"
                >
                    当前筛选条件下没有匹配技能
                </div>
            </div>
        </div>
    </section>
</template>
