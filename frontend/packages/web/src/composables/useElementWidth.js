import { onMounted, onUnmounted, ref, watch } from 'vue';

export function useElementWidth(elementRef) {
    const width = ref(0);
    let observer = null;

    function updateWidth() {
        width.value = Number(elementRef?.value?.clientWidth || 0);
    }

    function stopObserving() {
        if (observer) {
            observer.disconnect();
            observer = null;
        }
    }

    function startObserving(target) {
        if (typeof window === 'undefined' || !target) {
            updateWidth();
            return;
        }
        updateWidth();
        if (typeof window.ResizeObserver !== 'function') {
            window.addEventListener('resize', updateWidth);
            return;
        }
        observer = new window.ResizeObserver(() => {
            updateWidth();
        });
        observer.observe(target);
    }

    onMounted(() => {
        watch(
            elementRef,
            element => {
                if (typeof window !== 'undefined' && typeof window.ResizeObserver !== 'function') {
                    updateWidth();
                    return;
                }
                stopObserving();
                if (element) {
                    startObserving(element);
                } else {
                    updateWidth();
                }
            },
            { immediate: true }
        );
        if (typeof window !== 'undefined' && typeof window.ResizeObserver !== 'function') {
            window.addEventListener('resize', updateWidth);
        }
    });

    onUnmounted(() => {
        stopObserving();
        if (typeof window !== 'undefined' && typeof window.ResizeObserver !== 'function') {
            window.removeEventListener('resize', updateWidth);
        }
    });

    return width;
}
