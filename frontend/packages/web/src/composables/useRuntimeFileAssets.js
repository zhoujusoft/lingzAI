import { ref, computed, watch } from 'vue';
import { listRuntimeFileAssets } from '@/api/runtimeFileAsset';

/**
 * 运行时文件资产管理 composable
 * @param {Object} options
 * @param {import('vue').Ref<string>} options.sessionCode - 会话编码
 * @param {import('vue').Ref<boolean>} options.enabled - 是否启用加载
 * @returns {{
 *   loading: import('vue').Ref<boolean>,
 *   error: import('vue').Ref<Error|null>,
 *   assets: import('vue').Ref<Array>,
 *   groupedAssets: import('vue').ComputedRef<{UPLOAD: Array, ARTIFACT: Array, TEMP: Array}>,
 *   pagination: import('vue').Ref<{current: number, size: number, total: number}>,
 *   loadAssets: (pageNo?: number) => Promise<void>
 * }}
 */
export function useRuntimeFileAssets(options) {
    const { sessionCode, enabled } = options;

    const loading = ref(false);
    const error = ref(null);
    const assets = ref([]);
    const pagination = ref({
        current: 1,
        size: 50,
        total: 0,
    });

    // 按文件角色分组
    const groupedAssets = computed(() => {
        const groups = {
            UPLOAD: [],
            ARTIFACT: [],
            TEMP: [],
        };

        for (const asset of assets.value) {
            const role = asset.fileRole || 'TEMP';
            if (groups[role]) {
                groups[role].push(asset);
            }
        }

        return groups;
    });

    /**
     * 加载文件资产列表
     * @param {number} [pageNo=1] - 页码
     */
    async function loadAssets(pageNo = 1) {
        if (!enabled.value) return;

        loading.value = true;
        error.value = null;

        try {
            const response = await listRuntimeFileAssets({
                sessionId: sessionCode.value || undefined,
                pageNo,
                pageSize: pagination.value.size,
            });

            assets.value = response.items || [];
            pagination.value = {
                current: response.current || 1,
                size: response.size || 50,
                total: response.total || 0,
            };
        } catch (err) {
            error.value = err;
            assets.value = [];
        } finally {
            loading.value = false;
        }
    }

    // 记录上一次的 sessionCode，用于检测变化
    let lastLoadedSessionCode = null;

    // 监听 enabled 和 sessionCode 的变化
    watch(
        [enabled, sessionCode],
        ([newEnabled, newSessionCode]) => {
            // 必须启用且有 sessionCode
            if (!newEnabled || !newSessionCode) {
                return;
            }

            // sessionCode 发生变化时重新加载
            if (newSessionCode !== lastLoadedSessionCode) {
                lastLoadedSessionCode = newSessionCode;
                assets.value = []; // 清空旧数据
                loadAssets();
            }
        },
        { immediate: true }
    );

    return {
        loading,
        error,
        assets,
        groupedAssets,
        pagination,
        loadAssets,
    };
}
