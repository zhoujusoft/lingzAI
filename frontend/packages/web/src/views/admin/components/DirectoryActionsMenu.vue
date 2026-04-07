<script setup>
import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/vue';

const emit = defineEmits(['move', 'rename', 'delete']);
</script>

<template>
    <Menu as="div" class="relative inline-block text-left" data-component="DirectoryActionsMenu" @click.stop>
        <MenuButton
            class="flex h-6 w-6 items-center justify-center rounded text-slate-400 transition-colors hover:bg-white hover:text-primary"
        >
            <span class="material-symbols-outlined fill-0 text-[18px] leading-none">more_horiz</span>
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
                class="absolute right-0 z-30 mt-1 w-36 origin-top-right rounded-lg border border-slate-200 bg-white p-1 shadow-lg outline-none"
            >
                <MenuItem v-slot="{ active }">
                    <button
                        type="button"
                        class="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-slate-700"
                        :class="active ? 'bg-slate-50' : ''"
                        @click="emit('move')"
                    >
                        <span class="material-symbols-outlined fill-0 text-base">drive_file_move</span>
                        <span>移动</span>
                    </button>
                </MenuItem>

                <MenuItem v-slot="{ active }">
                    <button
                        type="button"
                        class="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-slate-700"
                        :class="active ? 'bg-slate-50' : ''"
                        @click="emit('rename')"
                    >
                        <span class="material-symbols-outlined fill-0 text-base">drive_file_rename_outline</span>
                        <span>重命名</span>
                    </button>
                </MenuItem>

                <MenuItem v-slot="{ active }">
                    <button
                        type="button"
                        class="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-red-600"
                        :class="active ? 'bg-red-50' : ''"
                        @click="emit('delete')"
                    >
                        <span class="material-symbols-outlined fill-0 text-base">delete</span>
                        <span>删除</span>
                    </button>
                </MenuItem>
            </MenuItems>
        </transition>
    </Menu>
</template>
