<script setup>
import { nextTick, ref } from 'vue';
import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/vue';

const emit = defineEmits(['recall-test', 'edit', 'delete']);

const menuPosition = ref({
    left: 0,
    top: 0,
});
const MENU_WIDTH = 144;
const MENU_HEIGHT = 132;
const VIEWPORT_PADDING = 8;
const MENU_OFFSET_X = -16;
const MENU_OFFSET_Y = 8;

function updateMenuPosition(event) {
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const rawLeft = event.clientX + MENU_OFFSET_X;
    const rawTop = event.clientY + MENU_OFFSET_Y;
    const maxLeft = Math.max(VIEWPORT_PADDING, viewportWidth - MENU_WIDTH - VIEWPORT_PADDING);
    const maxTop = Math.max(VIEWPORT_PADDING, viewportHeight - MENU_HEIGHT - VIEWPORT_PADDING);

    menuPosition.value = {
        left: Math.max(VIEWPORT_PADDING, Math.min(rawLeft, maxLeft)),
        top: Math.max(VIEWPORT_PADDING, Math.min(rawTop, maxTop)),
    };
}

function handleMenuButtonClick(event) {
    nextTick(() => {
        updateMenuPosition(event);
    });
}
</script>

<template>
    <Menu as="div" class="relative inline-block text-left" data-component="KnowledgeCardActionsMenu" @click.stop>
        <MenuButton
            class="flex h-8 w-8 items-center justify-center rounded text-slate-400 transition hover:bg-slate-50 hover:text-slate-600"
            @click="handleMenuButtonClick"
        >
            <span class="material-symbols-outlined">more_horiz</span>
        </MenuButton>

        <transition
            enter-active-class="transition duration-100 ease-out"
            enter-from-class="transform opacity-0 scale-95"
            enter-to-class="transform opacity-100 scale-100"
            leave-active-class="transition duration-75 ease-in"
            leave-from-class="transform opacity-100 scale-100"
            leave-to-class="transform opacity-0 scale-95"
        >
            <MenuItems
                class="fixed z-40 w-36 origin-top-left rounded-lg border border-slate-200 bg-white p-1 shadow-lg outline-none"
                :style="{
                    left: `${menuPosition.left}px`,
                    top: `${menuPosition.top}px`,
                }"
            >
                <MenuItem v-slot="{ active }">
                    <button
                        type="button"
                        class="flex h-9 w-full items-center gap-2 rounded-md px-3 text-left text-sm text-slate-700"
                        :class="active ? 'bg-slate-50' : ''"
                        @click="emit('recall-test')"
                    >
                        <span class="material-symbols-outlined block fill-0 text-[18px] leading-none">science</span>
                        <span class="leading-none">召回测试</span>
                    </button>
                </MenuItem>

                <MenuItem v-slot="{ active }">
                    <button
                        type="button"
                        class="flex h-9 w-full items-center gap-2 rounded-md px-3 text-left text-sm text-slate-700"
                        :class="active ? 'bg-slate-50' : ''"
                        @click="emit('edit')"
                    >
                        <span class="material-symbols-outlined block fill-0 text-[18px] leading-none">edit_square</span>
                        <span class="leading-none">编辑</span>
                    </button>
                </MenuItem>

                <MenuItem v-slot="{ active }">
                    <button
                        type="button"
                        class="flex h-9 w-full items-center gap-2 rounded-md px-3 text-left text-sm text-red-600"
                        :class="active ? 'bg-red-50' : ''"
                        @click="emit('delete')"
                    >
                        <span class="material-symbols-outlined block fill-0 text-[18px] leading-none">delete</span>
                        <span class="leading-none">删除</span>
                    </button>
                </MenuItem>
            </MenuItems>
        </transition>
    </Menu>
</template>
