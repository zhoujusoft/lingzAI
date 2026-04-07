<script setup>
defineProps({
    headerClass: {
        type: String,
        default: '',
    },
    bodyClass: {
        type: String,
        default: '',
    },
    footerClass: {
        type: String,
        default: '',
    },
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-white">
        <header
            class="flex h-16 shrink-0 items-center border-b border-slate-100 bg-white px-8"
            :class="headerClass"
        >
            <div class="flex min-w-0 flex-1 items-center">
                <slot name="left" />
            </div>
            <div class="flex shrink-0 items-center justify-center">
                <slot name="center" />
            </div>
            <div class="flex min-w-0 flex-1 items-center justify-end">
                <slot name="right" />
            </div>
        </header>

        <div class="min-h-0 flex-1" :class="bodyClass">
            <slot />
        </div>

        <footer
            v-if="$slots.footer"
            class="shrink-0 border-t border-slate-100 bg-white"
            :class="footerClass"
        >
            <slot name="footer" />
        </footer>
    </section>
</template>
