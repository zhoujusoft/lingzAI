<script setup>
import { computed } from 'vue';

const props = defineProps({
    modelValue: {
        type: Array,
        default: () => [],
    },
    readonly: {
        type: Boolean,
        default: false,
    },
    outputMode: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['update:modelValue']);

const typeOptions = [
    { value: 'string', label: '文本' },
    { value: 'number', label: '数值' },
    { value: 'boolean', label: '布尔' },
    { value: 'object', label: '对象' },
    { value: 'array', label: '对象数组' },
];

const flattenedRows = computed(() => {
    const result = [];
    walkRows(props.modelValue || [], 0, result);
    return result;
});

function createId() {
    if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID().replaceAll('-', '');
    }
    return `${Date.now()}${Math.random().toString(16).slice(2)}`;
}

function walkRows(rows, depth, result) {
    rows.forEach(row => {
        result.push({ row, depth });
        if (Array.isArray(row.children) && row.children.length) {
            walkRows(row.children, depth + 1, result);
        }
    });
}

function cloneRows(rows = props.modelValue) {
    return JSON.parse(JSON.stringify(Array.isArray(rows) ? rows : []));
}

function updateRows(rows) {
    emit('update:modelValue', rows);
}

function ensureDefaults(row) {
    return {
        objectId: row?.objectId || createId(),
        parentId: row?.parentId || '',
        name: row?.name || '',
        key: row?.key || '',
        path: row?.path || '',
        paramType: row?.paramType || 'string',
        value: row?.value ?? '',
        desc: row?.desc || '',
        children: Array.isArray(row?.children) ? row.children : [],
    };
}

function visitRows(rows, targetId, updater) {
    for (const row of rows) {
        if (row.objectId === targetId) {
            updater(row);
            return true;
        }
        if (Array.isArray(row.children) && visitRows(row.children, targetId, updater)) {
            return true;
        }
    }
    return false;
}

function updateRowValue(targetId, updater) {
    const rows = cloneRows();
    const updated = visitRows(rows, targetId, updater);
    if (updated) {
        updateRows(rows);
    }
}

function addRootRow() {
    const rows = cloneRows();
    rows.push(ensureDefaults({}));
    updateRows(rows);
}

function addChildRow(parentId) {
    const rows = cloneRows();
    visitRows(rows, parentId, row => {
        row.children = Array.isArray(row.children) ? row.children : [];
        row.children.push(ensureDefaults({ parentId }));
    });
    updateRows(rows);
}

function removeRowById(rows, targetId) {
    const index = rows.findIndex(item => item.objectId === targetId);
    if (index >= 0) {
        rows.splice(index, 1);
        return true;
    }
    return rows.some(
        item => Array.isArray(item.children) && removeRowById(item.children, targetId)
    );
}

function deleteRow(targetId) {
    const rows = cloneRows();
    if (removeRowById(rows, targetId)) {
        updateRows(rows);
    }
}

function handleTypeChange(targetId, value) {
    updateRowValue(targetId, row => {
        row.paramType = value;
        if (!['object', 'array'].includes(value)) {
            row.children = [];
        }
        if (value === 'number') {
            row.value = row.value === '' ? 0 : Number(row.value);
            return;
        }
        if (value === 'boolean') {
            row.value = Boolean(row.value);
            return;
        }
        if (value === 'object') {
            row.value = {};
            return;
        }
        if (value === 'array') {
            row.value = [];
            return;
        }
        row.value = typeof row.value === 'string' ? row.value : '';
    });
}

function updateField(targetId, field, value) {
    updateRowValue(targetId, row => {
        row[field] = value;
    });
}

function typeLabel(value) {
    return typeOptions.find(item => item.value === value)?.label || '文本';
}

function canHaveChildren(row) {
    return row.paramType === 'object' || row.paramType === 'array';
}
</script>

<template>
    <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div
            class="grid min-w-[900px] grid-cols-[minmax(0,1.2fr),140px,minmax(0,1.1fr),minmax(0,1fr),160px] gap-3 border-b border-slate-200 bg-slate-50 px-4 py-3 text-sm font-semibold text-slate-600"
        >
            <div>参数名称</div>
            <div>参数类型</div>
            <div>{{ outputMode ? 'JSON 路径' : '默认值' }}</div>
            <div>参数描述</div>
            <div class="text-right">操作</div>
        </div>

        <div v-if="!flattenedRows.length" class="px-4 py-10 text-center text-sm text-slate-400">
            暂无参数
        </div>

        <div v-else class="custom-scrollbar overflow-x-auto">
            <div class="min-w-[900px] divide-y divide-slate-100">
                <div
                    v-for="{ row, depth } in flattenedRows"
                    :key="row.objectId"
                    class="grid grid-cols-[minmax(0,1.2fr),140px,minmax(0,1.1fr),minmax(0,1fr),160px] gap-3 px-4 py-3"
                >
                    <div class="min-w-0">
                        <div
                            class="flex items-center gap-2"
                            :style="{ paddingLeft: `${depth * 20}px` }"
                        >
                            <span v-if="depth > 0" class="h-px w-4 shrink-0 bg-slate-200" />
                            <input
                                :value="row.name"
                                :disabled="readonly"
                                class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-slate-100"
                                type="text"
                                placeholder="请输入参数名称"
                                @input="
                                    event => updateField(row.objectId, 'name', event.target.value)
                                "
                            />
                        </div>
                    </div>

                    <div>
                        <select
                            v-if="!readonly"
                            :value="row.paramType"
                            class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                            @change="event => handleTypeChange(row.objectId, event.target.value)"
                        >
                            <option
                                v-for="item in typeOptions"
                                :key="item.value"
                                :value="item.value"
                            >
                                {{ item.label }}
                            </option>
                        </select>
                        <div
                            v-else
                            class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500"
                        >
                            {{ typeLabel(row.paramType) }}
                        </div>
                    </div>

                    <div>
                        <input
                            v-if="outputMode"
                            :value="row.path"
                            :disabled="readonly"
                            class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-slate-100"
                            type="text"
                            placeholder="请选择 JSON 路径"
                            @input="event => updateField(row.objectId, 'path', event.target.value)"
                        />
                        <input
                            v-else
                            :value="row.value"
                            :disabled="readonly || canHaveChildren(row)"
                            class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-slate-100"
                            type="text"
                            placeholder="参数默认值"
                            @input="event => updateField(row.objectId, 'value', event.target.value)"
                        />
                    </div>

                    <div>
                        <input
                            :value="row.desc"
                            :disabled="readonly"
                            class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-slate-100"
                            type="text"
                            placeholder="请输入参数描述"
                            @input="event => updateField(row.objectId, 'desc', event.target.value)"
                        />
                    </div>

                    <div
                        v-if="!readonly"
                        class="flex items-center justify-end gap-2 whitespace-nowrap"
                    >
                        <button
                            v-if="canHaveChildren(row)"
                            class="shrink-0 whitespace-nowrap rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-500 transition hover:border-primary hover:text-primary"
                            type="button"
                            @click="addChildRow(row.objectId)"
                        >
                            加子级
                        </button>
                        <button
                            class="shrink-0 whitespace-nowrap rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-500 transition hover:border-rose-200 hover:text-rose-600"
                            type="button"
                            @click="deleteRow(row.objectId)"
                        >
                            删除
                        </button>
                    </div>
                    <div v-else />
                </div>
            </div>
        </div>

        <div v-if="!readonly" class="border-t border-slate-200 bg-slate-50 px-4 py-3">
            <button
                class="rounded-xl border border-dashed border-slate-300 px-4 py-2 text-sm font-semibold text-slate-600 transition hover:border-primary hover:text-primary"
                type="button"
                @click="addRootRow"
            >
                添加参数
            </button>
        </div>
    </div>
</template>
