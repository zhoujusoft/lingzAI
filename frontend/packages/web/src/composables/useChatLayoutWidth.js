/**
 * 统一的聊天布局宽度计算 composable
 *
 * 设计原则（业界通用做法）：
 * 1. 阅读舒适度优先：每行 30-40 字符最佳，约 500-768px
 * 2. 大屏固定最大宽度，居中显示
 * 3. 小屏自适应，保证最小可用宽度
 * 4. 不使用百分比限制，固定像素值更可控
 */

import { computed, onMounted, onUnmounted, ref } from 'vue';

/**
 * 限制数值在指定范围内
 * @param {number} value - 当前值
 * @param {number} min - 最小值
 * @param {number} max - 最大值
 * @returns {number} 限制后的值
 */
function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}

/**
 * 计算聊天布局宽度
 * @param {Object} options - 配置选项
 * @param {number} options.minWidth - 最小宽度（默认 500px）
 * @param {number} options.maxWidth - 最大宽度（默认 768px）
 * @param {number} options.pagePadding - 页面左右 padding 总和（默认 64px）
 * @param {import('vue').Ref<number>} options.viewportWidth - 外部传入的视口宽度（可选）
 * @returns {Object} 宽度计算结果
 */
export function useChatLayoutWidth(options = {}) {
    const {
        minWidth = 500,
        maxWidth = 768,
        pagePadding = 64,
        viewportWidth: externalViewportWidth,
    } = options;

    // 使用外部传入的或内部创建的视口宽度
    const internalViewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth);
    const viewportWidth = externalViewportWidth || internalViewportWidth;

    // 响应式监听窗口大小变化
    function updateViewportWidth() {
        if (typeof window !== 'undefined') {
            internalViewportWidth.value = window.innerWidth;
        }
    }

    let isListening = false;

    function startListening() {
        if (isListening || externalViewportWidth) return;
        isListening = true;
        window.addEventListener('resize', updateViewportWidth);
    }

    function stopListening() {
        if (!isListening) return;
        isListening = false;
        window.removeEventListener('resize', updateViewportWidth);
    }

    // 自动管理生命周期（仅当使用内部 viewportWidth 时）
    if (!externalViewportWidth && typeof window !== 'undefined') {
        onMounted(() => {
            updateViewportWidth();
            startListening();
        });
        onUnmounted(() => {
            stopListening();
        });
    }

    /**
     * 可用空间（减去页面 padding）
     */
    const availableWidth = computed(() => {
        return Math.max(0, viewportWidth.value - pagePadding);
    });

    /**
     * 屏幕类型
     */
    const screenType = computed(() => {
        const width = viewportWidth.value;
        if (width >= 1200) return 'large';
        if (width >= 768) return 'medium';
        return 'small';
    });

    /**
     * 最终聊天区域宽度（像素值）
     *
     * 大屏 (≥1200px)：固定最大宽度 768px
     * 中屏 (768-1199px)：自适应，最大 680px
     * 小屏 (<768px)：全宽，保证最小宽度
     */
    const chatWidthPx = computed(() => {
        const available = availableWidth.value;
        const type = screenType.value;

        // 大屏：固定最大宽度，居中显示
        if (type === 'large') {
            return Math.min(maxWidth, available);
        }

        // 中屏：自适应，有上限
        if (type === 'medium') {
            const mediumMaxWidth = 680;
            return Math.min(mediumMaxWidth, available);
        }

        // 小屏：全宽，取可用空间（保证最小 320px）
        return Math.max(320, available);
    });

    /**
     * CSS 样式值（带单位）
     */
    const chatWidthStyle = computed(() => `${chatWidthPx.value}px`);

    /**
     * 是否应该居中显示
     * 大屏和中屏居中，小屏不居中
     */
    const shouldCenter = computed(() => {
        return screenType.value !== 'small' && availableWidth.value > chatWidthPx.value;
    });

    return {
        /** 视口宽度（响应式） */
        viewportWidth,
        /** 屏幕类型：large / medium / small */
        screenType,
        /** 聊天区域宽度（像素值） */
        chatWidthPx,
        /** 聊天区域宽度（CSS 样式值） */
        chatWidthStyle,
        /** 是否应该居中显示 */
        shouldCenter,
        /** 最小宽度 */
        minWidth,
        /** 最大宽度 */
        maxWidth,
        /** 可用空间 */
        availableWidth,
        /** 手动启动监听（用于外部控制） */
        startListening,
        /** 手动停止监听 */
        stopListening,
    };
}

/**
 * 预设配置：标准聊天布局
 * 用于 /chat、/agent-chat 等标准聊天页面
 */
export function useStandardChatWidth() {
    return useChatLayoutWidth({
        minWidth: 500,
        maxWidth: 768,
        pagePadding: 64,
    });
}

/**
 * 预设配置：带侧边栏的聊天布局
 * 用于有固定侧边栏的页面（如 FrontLandingLayout）
 * @param {number} sidebarWidth - 侧边栏宽度（默认 256px）
 */
export function useChatWidthWithSidebar(sidebarWidth = 256) {
    return useChatLayoutWidth({
        minWidth: 500,
        maxWidth: 768,
        // 页面 padding + 侧边栏宽度
        pagePadding: 32 + sidebarWidth,
    });
}
