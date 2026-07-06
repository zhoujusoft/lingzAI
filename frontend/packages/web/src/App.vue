<template>
    <router-view />
    <OverlayHost />
    <Toast />
</template>

<script setup>
import OverlayHost from './components/feedback/OverlayHost.vue';
import Toast from './components/Toast.vue';
</script>
