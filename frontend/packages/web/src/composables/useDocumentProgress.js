/**
 * 文档处理进度轮询 Composable
 * 
 * 用法：
 * const { startPolling, stopPolling, progress, isPolling } = useDocumentProgress();
 * startPolling(docId, { onProgress, onComplete, onError });
 */

import { ref, watch } from 'vue';
import { getDocumentProgress, DocumentStatus, ProgressStage, getStageLabel } from '@/api/datasets';

/**
 * @typedef {Object} ProgressData
 * @property {number} docId - 文档 ID
 * @property {number} status - 文档状态
 * @property {number} progress - 进度百分比 (0-100)
 * @property {string} stage - 当前阶段
 * @property {string} message - 进度消息
 * @property {string} updatedAt - 更新时间
 */

/**
 * @typedef {Object} UseDocumentProgressOptions
 * @property {Function} [onProgress] - 进度更新回调 (progress: ProgressData) => void
 * @property {Function} [onComplete] - 完成回调 (progress: ProgressData) => void
 * @property {Function} [onError] - 错误回调 (error: Error, progress: ProgressData) => void
 * @property {number} [pollInterval] - 轮询间隔 (毫秒)，默认 2000
 */

export function useDocumentProgress(options = {}) {
    const {
        onProgress,
        onComplete,
        onError,
        pollInterval = 2000,
    } = options;

    const progress = ref(null);
    const isPolling = ref(false);
    const pollTimer = ref(null);
    const currentDocId = ref(null);

    /**
     * 停止轮询
     */
    function stopPolling() {
        if (pollTimer.value) {
            clearInterval(pollTimer.value);
            pollTimer.value = null;
        }
        isPolling.value = false;
        currentDocId.value = null;
    }

    /**
     * 轮询进度
     */
    async function pollProgress() {
        if (!currentDocId.value) {
            return;
        }

        try {
            const data = await getDocumentProgress(currentDocId.value);
            progress.value = data;

            // 调用进度回调
            if (onProgress && typeof onProgress === 'function') {
                onProgress(data);
            }

            // 检查是否完成或失败
            if (data.status === DocumentStatus.COMPLETED) {
                stopPolling();
                if (onComplete && typeof onComplete === 'function') {
                    onComplete(data);
                }
            } else if (data.status === DocumentStatus.FAILED) {
                stopPolling();
                if (onError && typeof onError === 'function') {
                    onError(new Error(data.message || '处理失败'), data);
                }
            }
        } catch (error) {
            console.error('轮询进度失败:', error);
            stopPolling();
            if (onError && typeof onError === 'function') {
                onError(error, progress.value);
            }
        }
    }

    /**
     * 开始轮询
     * @param {number} docId - 文档 ID
     */
    function startPolling(docId) {
        if (!docId) {
            console.error('docId 不能为空');
            return;
        }

        // 如果已经在轮询同一个文档，直接返回
        if (isPolling.value && currentDocId.value === docId) {
            return;
        }

        // 停止之前的轮询
        stopPolling();

        currentDocId.value = docId;
        isPolling.value = true;

        // 立即执行一次
        pollProgress();

        // 设置定时轮询
        pollTimer.value = setInterval(pollProgress, pollInterval);
    }

    /**
     * 获取进度阶段标签
     * @returns {string}
     */
    function getStageLabelValue() {
        if (!progress.value || !progress.value.stage) {
            return '';
        }
        return getStageLabel(progress.value.stage);
    }

    /**
     * 是否处理中
     * @returns {boolean}
     */
    function isProcessing() {
        return isPolling.value || (progress.value && progress.value.status === DocumentStatus.PROCESSING);
    }

    /**
     * 是否完成
     * @returns {boolean}
     */
    function isCompleted() {
        return progress.value && progress.value.status === DocumentStatus.COMPLETED;
    }

    /**
     * 是否失败
     * @returns {boolean}
     */
    function isFailed() {
        return progress.value && progress.value.status === DocumentStatus.FAILED;
    }

    return {
        progress,
        isPolling,
        startPolling,
        stopPolling,
        getStageLabelValue,
        isProcessing,
        isCompleted,
        isFailed,
    };
}

export default useDocumentProgress;
