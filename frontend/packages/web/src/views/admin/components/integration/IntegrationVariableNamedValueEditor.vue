<script setup>
import IntegrationVariableInput from './IntegrationVariableInput.vue';

const props = defineProps({
    modelValue: {
        type: Array,
        default: () => [],
    },
    groups: {
        type: Array,
        default: () => [],
    },
    addText: {
        type: String,
        default: '新增参数',
    },
    namePlaceholder: {
        type: String,
        default: '参数名称',
    },
    valuePlaceholder: {
        type: String,
        default: '参数值',
    },
    readonly: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['update:modelValue']);

function createRowId() {
    if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID().replaceAll('-', '');
    }
    return `${Date.now()}${Math.random().toString(16).slice(2)}`;
}

function cloneRows() {
    return (Array.isArray(props.modelValue) ? props.modelValue : []).map(item => ({
        objectId: item?.objectId || createRowId(),
        name: item?.name || '',
        value: item?.value == null ? '' : String(item.value),
    }));
}

function updateRows(rows) {
    emit('update:modelValue', rows);
}

function updateRow(index, field, value) {
    const rows = cloneRows();
    while (rows.length <= index) {
        rows.push({ objectId: createRowId(), name: '', value: '' });
    }
    rows[index] = {
        ...rows[index],
        [field]: value,
    };
    updateRows(rows);
}

function addRow() {
    updateRows([...cloneRows(), { objectId: createRowId(), name: '', value: '' }]);
}

function removeRow(index) {
    const rows = cloneRows();
    rows.splice(index, 1);
    updateRows(rows);
}
</script>

<template>
    <div class="space-y-3">
        <div
            v-for="(row, index) in modelValue"
            :key="row.objectId || index"
            class="rounded-2xl border border-slate-200 bg-slate-50 p-3"
        >
            <div class="grid gap-3 lg:grid-cols-[minmax(0,0.9fr),minmax(0,1.4fr),auto]">
                <input
                    :value="row.name"
                    :placeholder="namePlaceholder"
                    :disabled="readonly"
                    class="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-slate-100"
                    type="text"
                    @input="event => updateRow(index, 'name', event.target.value)"
                />
                <IntegrationVariableInput
                    :model-value="row.value"
                    :groups="groups"
                    :placeholder="valuePlaceholder"
                    :disabled="readonly"
                    @update:modelValue="value => updateRow(index, 'value', value)"
                />
                <button
                    v-if="!readonly"
                    class="whitespace-nowrap rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-500 transition hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
                    type="button"
                    @click="removeRow(index)"
                >
                    删除
                </button>
            </div>
        </div>

        <button
            v-if="!readonly"
            class="rounded-xl border border-dashed border-slate-300 px-4 py-2 text-sm font-semibold text-slate-600 transition hover:border-primary hover:text-primary"
            type="button"
            @click="addRow"
        >
            {{ addText }}
        </button>
    </div>
</template>
