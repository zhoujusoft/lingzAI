/**
 * Toast 通知系统
 *
 * 【Toast 使用规范】
 * 本文件是项目中唯一的标准 Toast 实现。
 *
 * 使用规则：
 * 1. 所有需要显示 Toast 通知的地方，必须使用本文件导出的 showToast 函数
 * 2. 禁止在组件中自行实现 toast/floatingMessage 等类似功能
 * 3. 禁止使用其他第三方 toast 库
 *
 * 导入方式：
 * ```javascript
 * import { showToast } from '@/composables/useToast';
 * ```
 *
 * 使用示例：
 * ```javascript
 * showToast('保存成功');                    // 成功提示
 * showToast('操作失败', 'error');           // 错误提示
 * showToast('部分保存成功', 'warning');      // 警告提示
 * showToast('消息', 'success', 5000);       // 自定义时长 5 秒
 * ```
 *
 * Toast 样式规范：
 * - 位置：顶部居中 (fixed left-1/2 top-6)
 * - 类型：success (绿色) / error (红色) / warning (黄色)
 * - 图标：check_circle / error / warning
 * - 动画：从上滑入，向上滑出
 */
import { reactive } from 'vue';

/**
 * Toast 状态管理
 */
const toastState = reactive({
    visible: false,
    message: '',
    type: 'success', // 'success' | 'error' | 'warning'
});

let toastTimer = null;

/**
 * 显示 Toast 通知
 * @param {string} message - 通知内容
 * @param {'success' | 'error' | 'warning'} type - 通知类型
 * @param {number} duration - 显示时长（毫秒）
 */
export function showToast(message, type = 'success', duration = 3000) {
    // 清除之前的定时器
    if (toastTimer) {
        clearTimeout(toastTimer);
        toastTimer = null;
    }

    toastState.message = message;
    toastState.type = type;
    toastState.visible = true;

    toastTimer = setTimeout(() => {
        toastState.visible = false;
        toastState.message = '';
        toastTimer = null;
    }, duration);
}

/**
 * 隐藏 Toast
 */
export function hideToast() {
    if (toastTimer) {
        clearTimeout(toastTimer);
        toastTimer = null;
    }
    toastState.visible = false;
    toastState.message = '';
}

/**
 * 获取 Toast 状态（供组件使用）
 */
export function useToast() {
    return {
        toastState,
        showToast,
        hideToast,
    };
}
