<script setup>
import { computed } from 'vue';
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue';
import {
    getSkillIconGradientClass,
    getSkillIconColorLabel,
    resolveSkillIcon,
    resolveSkillIconColor,
    SKILL_ICON_COLOR_OPTIONS,
    SKILL_ICON_OPTIONS,
} from '@/utils/skillVisuals';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const selectedIcon = computed(() => resolveSkillIcon(props.context.selectedIcon));
const selectedIconColor = computed(() => resolveSkillIconColor(props.context.selectedIconColor));
const iconOptions = computed(() =>
    SKILL_ICON_OPTIONS.map(icon => ({
        value: icon,
        label: icon,
    }))
);

function selectIcon(icon) {
    props.context.setIcon?.(icon);
}

function selectIconColor(iconColor) {
    props.context.setIconColor?.(iconColor);
}

function isActiveIcon(icon) {
    return selectedIcon.value === icon;
}

function isActiveColor(iconColor) {
    return selectedIconColor.value === iconColor;
}
</script>

<template>
    <div class="space-y-4 px-5 pb-5 pt-3">
        <section class="rounded-[24px] border border-slate-200 bg-slate-50 p-4">
            <p class="text-xs font-semibold tracking-[0.28em] text-slate-400">实时预览</p>
            <div
                class="mt-3 flex items-center gap-3 rounded-[20px] border border-slate-200 bg-white px-4 py-4"
            >
                <div
                    class="flex h-14 w-14 shrink-0 items-center justify-center rounded-[18px] text-white shadow-lg"
                    :class="getSkillIconGradientClass(selectedIconColor)"
                >
                    <span class="material-symbols-outlined text-[28px]">{{ selectedIcon }}</span>
                </div>
                <div class="min-w-0">
                    <p class="text-sm font-semibold text-slate-900">技能图标</p>
                    <p class="mt-1 text-xs text-slate-500">
                        {{ selectedIcon }} · {{ getSkillIconColorLabel(selectedIconColor) }}
                    </p>
                </div>
            </div>
        </section>

        <section class="rounded-[24px] border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between gap-4">
                <div>
                    <h3 class="text-base font-bold text-slate-900">选择图标</h3>
                    <p class="mt-1 text-xs text-slate-500">
                        这些图标会写入技能目录表中的 `icon` 字段。
                    </p>
                </div>
            </div>
            <Listbox :model-value="selectedIcon" @update:model-value="selectIcon">
                <div class="relative mt-4">
                    <ListboxButton
                        class="flex min-h-[56px] w-full items-center justify-between gap-3 rounded-[18px] border border-slate-200 bg-slate-50 px-4 py-3 text-left shadow-sm transition hover:border-slate-300 hover:bg-white"
                    >
                        <div class="flex min-w-0 items-center gap-3">
                            <div
                                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl text-white"
                                :class="getSkillIconGradientClass(selectedIconColor)"
                            >
                                <span class="material-symbols-outlined text-[22px]">{{
                                    selectedIcon
                                }}</span>
                            </div>
                            <div class="min-w-0">
                                <p class="truncate text-sm font-semibold text-slate-900">
                                    {{ selectedIcon }}
                                </p>
                                <p class="mt-0.5 text-xs text-slate-400">点击展开图标列表</p>
                            </div>
                        </div>
                        <span class="material-symbols-outlined shrink-0 text-[20px] text-slate-400"
                            >expand_more</span
                        >
                    </ListboxButton>

                    <transition
                        enter-active-class="transition duration-150 ease-out"
                        enter-from-class="translate-y-1 opacity-0"
                        enter-to-class="translate-y-0 opacity-100"
                        leave-active-class="transition duration-100 ease-in"
                        leave-from-class="translate-y-0 opacity-100"
                        leave-to-class="translate-y-1 opacity-0"
                    >
                        <ListboxOptions
                            class="absolute left-0 top-[calc(100%+0.5rem)] z-30 max-h-80 w-full overflow-y-auto rounded-[20px] border border-slate-200 bg-white p-2 shadow-[0_20px_48px_-20px_rgba(15,23,42,0.38)] ring-1 ring-slate-100/80 focus:outline-none"
                        >
                            <ListboxOption
                                v-for="option in iconOptions"
                                :key="option.value"
                                :value="option.value"
                                v-slot="{ active, selected }"
                                as="template"
                            >
                                <li
                                    :class="[
                                        'flex cursor-pointer items-center justify-between gap-3 rounded-[16px] px-3 py-2.5 transition',
                                        active ? 'bg-blue-50 text-primary' : 'text-slate-700',
                                        selected ? 'font-semibold' : 'font-medium',
                                    ]"
                                >
                                    <div class="flex min-w-0 items-center gap-3">
                                        <div
                                            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl text-white"
                                            :class="getSkillIconGradientClass(selectedIconColor)"
                                        >
                                            <span class="material-symbols-outlined text-[20px]">{{
                                                option.value
                                            }}</span>
                                        </div>
                                        <span class="truncate text-sm">{{ option.label }}</span>
                                    </div>
                                    <span
                                        v-if="selected"
                                        class="material-symbols-outlined shrink-0 text-base text-primary"
                                    >
                                        check
                                    </span>
                                </li>
                            </ListboxOption>
                        </ListboxOptions>
                    </transition>
                </div>
            </Listbox>
        </section>

        <section class="rounded-[24px] border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between gap-4">
                <div>
                    <h3 class="text-base font-bold text-slate-900">选择颜色</h3>
                    <p class="mt-1 text-xs text-slate-500">
                        实际颜色 key 将写入技能目录表中的 `iconColor` 字段。
                    </p>
                </div>
            </div>
            <div class="mt-4 grid grid-cols-3 gap-2 sm:grid-cols-6">
                <button
                    v-for="color in SKILL_ICON_COLOR_OPTIONS"
                    :key="color.key"
                    type="button"
                    class="rounded-[18px] border px-2.5 py-2.5 text-left transition"
                    :class="
                        isActiveColor(color.key)
                            ? 'border-blue-300 bg-blue-50 shadow-sm'
                            : 'border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white'
                    "
                    @click="selectIconColor(color.key)"
                >
                    <div class="flex items-center gap-2">
                        <div
                            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl text-white"
                            :class="getSkillIconGradientClass(color.key)"
                        >
                            <span class="material-symbols-outlined text-[18px]">{{
                                selectedIcon
                            }}</span>
                        </div>
                        <div class="min-w-0">
                            <p class="truncate text-xs font-semibold text-slate-900">
                                {{ color.label }}
                            </p>
                            <p class="truncate text-[11px] text-slate-400">{{ color.key }}</p>
                        </div>
                    </div>
                </button>
            </div>
        </section>
    </div>
</template>
