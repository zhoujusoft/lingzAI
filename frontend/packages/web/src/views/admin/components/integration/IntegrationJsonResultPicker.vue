<script setup>
import { computed } from 'vue';
import BaseModal from '@/components/feedback/BaseModal.vue';

const props = defineProps({
    open: {
        type: Boolean,
        default: false,
    },
    jsonText: {
        type: String,
        default: '',
    },
    selectedKeys: {
        type: Array,
        default: () => [],
    },
});

const emit = defineEmits(['close', 'select']);

const selectedKeySet = computed(() => new Set(props.selectedKeys || []));
const rows = computed(() => {
    if (!props.jsonText) {
        return [];
    }
    try {
        const parsed = JSON.parse(props.jsonText);
        const result = [];
        walk(parsed, 0, '$', 'output', result);
        return result;
    } catch (error) {
        return [];
    }
});

function walk(value, depth, path, keyPath, result) {
    if (Array.isArray(value)) {
        value.slice(0, 20).forEach((item, index) => {
            const nextPath = `${path}[${index}]`;
            const nextKey = `${keyPath}.${index}`;
            result.push(createRow(String(index), nextKey, nextPath, item, depth));
            walk(item, depth + 1, nextPath, nextKey, result);
        });
        return;
    }
    if (value && typeof value === 'object') {
        Object.entries(value).forEach(([key, item]) => {
            const nextPath = path === '$' ? `$.${key}` : `${path}.${key}`;
            const nextKey = `${keyPath}.${key}`;
            result.push(createRow(key, nextKey, nextPath, item, depth));
            walk(item, depth + 1, nextPath, nextKey, result);
        });
    }
}

function createRow(name, key, path, value, depth) {
    return {
        objectId: `${path}-${depth}`,
        name,
        key,
        path,
        paramType: detectType(value),
        value: previewValue(value),
        depth,
        rawValue: value,
    };
}

function detectType(value) {
    if (Array.isArray(value)) {
        return 'array';
    }
    if (value && typeof value === 'object') {
        return 'object';
    }
    if (typeof value === 'number') {
        return 'number';
    }
    if (typeof value === 'boolean') {
        return 'boolean';
    }
    return 'string';
}

function previewValue(value) {
    if (value && typeof value === 'object') {
        return JSON.stringify(value);
    }
    return value == null ? '' : String(value);
}

function buildOutputTree(row, parentId = '') {
    const objectId = parentId ? `${row.path}-${parentId}` : row.objectId;
    return {
        objectId,
        parentId,
        name: row.name,
        key: row.key,
        path: row.path,
        paramType: row.paramType,
        value: row.value,
        desc: '',
        children: buildChildren(row, objectId),
    };
}

function buildChildren(row, parentId) {
    if (Array.isArray(row.rawValue)) {
        return row.rawValue.slice(0, 20).map((item, index) => {
            const path = `${row.path}[${index}]`;
            const key = `${row.key}.${index}`;
            const childRow = createRow(String(index), key, path, item, row.depth + 1);
            return buildOutputTree(childRow, parentId);
        });
    }
    if (row.rawValue && typeof row.rawValue === 'object') {
        return Object.entries(row.rawValue).map(([key, item]) => {
            const path = row.path === '$' ? `$.${key}` : `${row.path}.${key}`;
            const childKey = `${row.key}.${key}`;
            const childRow = createRow(key, childKey, path, item, row.depth + 1);
            return buildOutputTree(childRow, parentId);
        });
    }
    return [];
}

function isSelected(row) {
    return selectedKeySet.value.has(row.key);
}

function selectRow(row) {
    emit('select', buildOutputTree(row));
}
</script>

<template>
    <BaseModal :open="open" panel-class="max-w-5xl" @close="emit('close')">
        <template #header>
            <div class="border-b border-slate-200 px-6 py-5">
                <div class="flex items-center justify-between gap-4">
                    <div>
                        <h2 class="text-lg font-bold text-slate-900">查看 JSON 解析结果</h2>
                        <p class="mt-1 text-sm text-slate-500">
                            从调试返回结果中选择出参；选择父级时会自动带出子级属性。
                        </p>
                    </div>
                    <button
                        class="whitespace-nowrap text-sm font-semibold text-slate-400 transition hover:text-slate-700"
                        type="button"
                        @click="emit('close')"
                    >
                        关闭
                    </button>
                </div>
            </div>
        </template>

        <template #content>
            <div class="px-6 py-6">
                <div
                    v-if="!rows.length"
                    class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-400"
                >
                    暂无可解析的 JSON 结果
                </div>
                <div v-else class="overflow-hidden rounded-2xl border border-slate-200">
                    <div
                        class="grid grid-cols-[minmax(0,1fr),120px,minmax(0,1.2fr),120px] gap-3 border-b border-slate-200 bg-slate-50 px-4 py-3 text-sm font-semibold text-slate-600"
                    >
                        <div>名称</div>
                        <div>类型</div>
                        <div>JSON 路径</div>
                        <div class="text-right">操作</div>
                    </div>
                    <div
                        class="custom-scrollbar max-h-[560px] overflow-y-auto divide-y divide-slate-100"
                    >
                        <div
                            v-for="row in rows"
                            :key="row.objectId"
                            class="grid grid-cols-[minmax(0,1fr),120px,minmax(0,1.2fr),120px] gap-3 px-4 py-3"
                        >
                            <div
                                class="flex items-center"
                                :style="{ paddingLeft: `${row.depth * 20}px` }"
                            >
                                <span v-if="row.depth > 0" class="mr-2 h-px w-4 bg-slate-200" />
                                <span class="truncate text-sm text-slate-700">{{ row.name }}</span>
                            </div>
                            <div class="text-sm text-slate-500">{{ row.paramType }}</div>
                            <div class="truncate font-mono text-sm text-slate-500">
                                {{ row.path }}
                            </div>
                            <div class="flex justify-end">
                                <button
                                    :disabled="isSelected(row)"
                                    class="whitespace-nowrap rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-500 transition hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
                                    type="button"
                                    @click="selectRow(row)"
                                >
                                    添加
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </template>
    </BaseModal>
</template>
