<template>
    <header
        class="z-10 flex h-12 shrink-0 items-center justify-between border-b border-slate-200 bg-white/80 px-4 py-2 backdrop-blur-md"
    >
        <div class="flex items-center gap-3">
            <div
                class="flex h-7 w-7 items-center justify-center rounded bg-blue-100 text-primary"
            >
                <span class="material-symbols-outlined text-base">{{ icon }}</span>
            </div>
            <div class="min-w-0">
                <h2 class="truncate text-sm font-semibold leading-tight">{{ title }}</h2>
                <div class="flex min-w-0 items-center gap-1">
                    <span class="h-2 w-2 rounded-full bg-green-500"></span>
                    <span class="truncate text-[11px] text-slate-500">{{ statusText }}</span>
                </div>
            </div>
        </div>
        <div class="flex items-center gap-2"></div>
    </header>
</template>

<script>
export default {
    name: 'ChatHeaderBar',
    props: {
        title: {
            type: String,
            default: '',
        },
        statusText: {
            type: String,
            default: '',
        },
        icon: {
            type: String,
            default: 'description',
        },
    },
};
</script>
