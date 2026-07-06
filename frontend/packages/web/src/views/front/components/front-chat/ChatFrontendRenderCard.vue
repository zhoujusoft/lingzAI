<script setup>
import { computed, ref, watch } from 'vue';

const emit = defineEmits(['action', 'confirm', 'cancel']);

const props = defineProps({
    payload: {
        type: Object,
        default: () => ({}),
    },
});

const draftData = ref({});
const expanded = ref(true);
const localStateOverride = ref(null);

const componentProps = computed(() => {
    const value = props.payload?.componentProps;
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
});

const payloadState = computed(() => {
    const value = props.payload?.state;
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
});

const actionDefinitions = computed(() => {
    const rows = props.payload?.actions;
    return Array.isArray(rows) ? rows.filter(item => item && typeof item === 'object') : [];
});

const confirmAction = computed(() => findActionDefinition('confirm'));
const cancelAction = computed(() => findActionDefinition('cancel'));

const schemaFields = computed(() => {
    const rowsFromProps = componentProps.value?.fields;
    if (Array.isArray(rowsFromProps) && rowsFromProps.length) {
        return rowsFromProps;
    }
    const rows = props.payload?.schema?.fields;
    return Array.isArray(rows) ? rows : [];
});

const sectionDefinitions = computed(() => {
    const rows = componentProps.value?.sections;
    return Array.isArray(rows) ? rows.filter(item => item && typeof item === 'object') : [];
});

const dataEntries = computed(() => {
    const data = props.payload?.data;
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
        return [];
    }
    return Object.entries(data).map(([key, value]) => ({
        key,
        value,
    }));
});

const missingFields = computed(() => {
    const rows = props.payload?.validation?.missingFields;
    return Array.isArray(rows) ? rows.filter(Boolean) : [];
});

const unknownFields = computed(() => {
    const rows = props.payload?.validation?.unknownFields;
    return Array.isArray(rows) ? rows.filter(Boolean) : [];
});

const fieldDefinitionMap = computed(() => {
    const map = {};
    schemaFields.value.forEach(field => registerFieldDefinition(map, field));
    sectionDefinitions.value.forEach(section => {
        if (String(section?.type || '').trim() === 'table') {
            const columns = getSectionTableFields(section);
            columns.forEach(column => {
                registerFieldDefinition(map, applySectionFieldPolicy(section, toFieldSeed(column)));
            });
            return;
        }
        const fields = Array.isArray(section?.fields) ? section.fields : [];
        fields.forEach(field => {
            registerFieldDefinition(map, applySectionFieldPolicy(section, toFieldSeed(field)));
        });
    });
    dataEntries.value.forEach(entry => {
        if (!map[entry.key]) {
            map[entry.key] = {
                key: entry.key,
                label: entry.key,
                description: entry.key,
                required: false,
                readonly: false,
                inputType: 'text',
            };
        }
    });
    return map;
});

const displayRows = computed(() => {
    if (schemaFields.value.length) {
        return schemaFields.value.map(field => buildDisplayRow(resolveFieldDefinition(field)));
    }
    return dataEntries.value.map(entry =>
        buildDisplayRow({
            key: entry.key,
            label: entry.key,
            description: entry.key,
            required: false,
            readonly: false,
            inputType: 'text',
        })
    );
});

const displaySections = computed(() => {
    if (!sectionDefinitions.value.length) {
        return [];
    }
    return sectionDefinitions.value
        .map(section => {
            const type = normalizeSectionType(section?.type);
            if (type === 'table') {
                const columns = getSectionTableFields(section)
                    .map(column => buildDisplayRow(resolveFieldDefinition(column)))
                    .filter(column => column.key);
                if (!columns.length) {
                    return null;
                }
                return {
                    key: String(section?.key || section?.code || section?.title || 'table').trim(),
                    title: String(section?.title || '明细').trim(),
                    type,
                    columns,
                };
            }
            const fields = Array.isArray(section?.fields)
                ? section.fields
                      .map(field => buildDisplayRow(resolveFieldDefinition(field)))
                      .filter(field => field.key)
                : [];
            if (!fields.length) {
                return null;
            }
            return {
                key: String(section?.key || section?.code || section?.title || 'form').trim(),
                title: String(section?.title || '基础信息').trim(),
                type,
                fields,
            };
        })
        .filter(Boolean);
});

const cardTitle = computed(() => props.payload?.title || '结构化草稿');

const cardSubtitle = computed(() => {
    if (componentProps.value?.subtitle) {
        return componentProps.value.subtitle;
    }
    if (missingFields.value.length) {
        return '请确认并补充缺失字段';
    }
    if (props.payload?.notes) {
        return props.payload.notes;
    }
    return '请确认表单信息后继续后续操作';
});

const effectiveState = computed(() =>
    mergeRenderState(
        mergeRenderState(buildDefaultState(), payloadState.value),
        localStateOverride.value
    )
);

const formReadonly = computed(() => effectiveState.value.editable === false);
const confirmDisabled = computed(() => isActionDisabled('confirm'));
const cancelDisabled = computed(() => isActionDisabled('cancel'));
const confirmLabel = computed(() => {
    if (effectiveState.value.stage === 'submitted') {
        return '已提交';
    }
    return String(confirmAction.value?.label || componentProps.value.confirmText || '确认').trim();
});
const cancelLabel = computed(() =>
    String(cancelAction.value?.label || componentProps.value.cancelText || '取消').trim()
);

watch(
    () => [props.payload?.schema, props.payload?.data, props.payload?.componentProps],
    () => {
        draftData.value = buildInitialDraftData();
    },
    {
        immediate: true,
        deep: true,
    }
);

watch(
    () => props.payload?.renderId,
    () => {
        localStateOverride.value = null;
    },
    {
        immediate: true,
    }
);

function buildInitialDraftData() {
    const next = {};
    const definitions = Object.values(fieldDefinitionMap.value);
    if (definitions.length) {
        definitions.forEach(field => {
            const key = field?.key;
            if (!key) {
                return;
            }
            next[key] = normalizeEditableValue(resolveFieldValue(field));
        });
        return next;
    }
    dataEntries.value.forEach(entry => {
        next[entry.key] = normalizeEditableValue(entry.value);
    });
    return next;
}

function resolveFieldLabel(field) {
    return String(field?.description || field?.label || field?.key || '-').trim();
}

function toFieldSeed(field) {
    if (typeof field === 'string') {
        return { key: field, label: field, description: field };
    }
    if (!field || typeof field !== 'object' || Array.isArray(field)) {
        return null;
    }
    return field;
}

function resolveFieldDefinition(field) {
    if (typeof field === 'string') {
        return fieldDefinitionMap.value[field] || { key: field, label: field, description: field };
    }
    if (!field || typeof field !== 'object' || Array.isArray(field)) {
        return { key: '', label: '', description: '' };
    }
    const key = String(field?.key || '').trim();
    const base = key ? fieldDefinitionMap.value[key] || {} : {};
    return {
        ...base,
        ...field,
        key,
    };
}

function registerFieldDefinition(target, field) {
    if (!field || typeof field !== 'object' || Array.isArray(field)) {
        return;
    }
    const key = String(field?.key || '').trim();
    if (!key) {
        return;
    }
    const current = target[key] || {};
    target[key] = {
        ...current,
        ...field,
        key,
        label: String(field?.label || current?.label || key).trim(),
        description: String(
            field?.description || current?.description || field?.label || key
        ).trim(),
        required: Boolean(field?.required || current?.required),
        readonly: Boolean(field?.readonly || current?.readonly),
        inputType: String(field?.inputType || current?.inputType || 'text').trim(),
    };
}

function buildDisplayRow(field) {
    const normalizedField = resolveFieldDefinition(field);
    return {
        key: normalizedField?.key || '',
        label: resolveFieldLabel(normalizedField),
        value: draftData.value[normalizedField?.key],
        required: Boolean(normalizedField?.required),
        multiline: isMultilineValue(draftData.value[normalizedField?.key]),
        readonly: Boolean(normalizedField?.readonly),
        inputType: normalizeInputType(normalizedField?.inputType),
    };
}

function getSectionTableFields(section) {
    const columns = Array.isArray(section?.columns) ? section.columns : [];
    if (columns.length) {
        return columns.map(column => applySectionFieldPolicy(section, column)).filter(Boolean);
    }
    const fields = Array.isArray(section?.fields) ? section.fields : [];
    return fields.map(field => applySectionFieldPolicy(section, field)).filter(Boolean);
}

function applySectionFieldPolicy(section, field) {
    const normalizedField = toFieldSeed(field);
    if (!normalizedField) {
        return null;
    }
    const sectionType = normalizeSectionType(section?.type);
    if (sectionType !== 'table') {
        return normalizedField;
    }
    const editableFields = Array.isArray(section?.editableFields)
        ? section.editableFields.map(item => String(item || '').trim()).filter(Boolean)
        : null;
    if (!editableFields) {
        return normalizedField;
    }
    const key = String(normalizedField?.key || '').trim();
    if (!key) {
        return normalizedField;
    }
    return {
        ...normalizedField,
        readonly: !editableFields.includes(key),
    };
}

function resolveFieldValue(field) {
    const data = props.payload?.data;
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
        return undefined;
    }
    return data[field?.key];
}

function normalizeEditableValue(value) {
    if (value == null) {
        return '';
    }
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
        return String(value);
    }
    try {
        return JSON.stringify(value, null, 2);
    } catch (error) {
        return String(value);
    }
}

function isMultilineValue(value) {
    if (typeof value !== 'string') {
        return false;
    }
    return value.includes('\n') || value.length > 48;
}

function normalizeSectionType(value) {
    return String(value || 'form').trim() === 'table' ? 'table' : 'form';
}

function normalizeInputType(value) {
    const text = String(value || '')
        .trim()
        .toLowerCase();
    if (text === 'number') {
        return 'number';
    }
    return 'text';
}

function isEmptyValue(value) {
    return value == null || String(value).trim() === '';
}

function formatDisplayValue(value) {
    return isEmptyValue(value) ? '-' : value;
}

function isFieldReadonly(row) {
    return formReadonly.value || Boolean(row?.readonly);
}

function handleCancel() {
    if (cancelDisabled.value) {
        return;
    }
    const nextState = buildTerminalState('cancelled');
    localStateOverride.value = nextState;
    const actionPayload = buildActionPayload(cancelAction.value, 'cancel', nextState);
    emit('action', actionPayload);
    emit('cancel', actionPayload);
}

function handleConfirm() {
    if (confirmDisabled.value) {
        return;
    }
    const nextState = buildTerminalState('submitted');
    localStateOverride.value = nextState;
    const actionPayload = buildActionPayload(confirmAction.value, 'confirm', nextState);
    emit('action', actionPayload);
    emit('confirm', actionPayload);
}

function toggleExpanded() {
    expanded.value = !expanded.value;
}

function buildActionPayload(actionDefinition, actionCode, state) {
    const normalizedAction =
        actionDefinition && typeof actionDefinition === 'object' ? actionDefinition : {};
    return {
        renderId: props.payload?.renderId || '',
        templateCode: props.payload?.templateCode || '',
        componentCode: props.payload?.componentCode || props.payload?.componentType || '',
        targetToolName: props.payload?.targetToolName || '',
        title: String(componentProps.value.title || cardTitle.value || '').trim(),
        actionCode,
        messageType: normalizeActionMessageType(normalizedAction.messageType),
        message: buildActionMessage(normalizedAction, actionCode),
        data: { ...draftData.value },
        state,
    };
}

function findActionDefinition(code) {
    return actionDefinitions.value.find(item => String(item?.code || '').trim() === code) || null;
}

function normalizeActionMessageType(value) {
    const text = String(value || '').trim();
    return text || 'event';
}

function buildActionMessage(actionDefinition, actionCode) {
    const template = String(actionDefinition?.messageTemplate || '').trim();
    if (template) {
        return applyTemplatePlaceholders(template);
    }
    if (actionCode === 'cancel') {
        return '用户已取消本次操作。';
    }
    return `用户已确认提交。以下是最终确认后的结构化数据，请直接继续处理：\n${buildDataJson()}`;
}

function applyTemplatePlaceholders(template) {
    return template
        .replaceAll('{{dataJson}}', buildDataJson())
        .replaceAll(
            '{{title}}',
            String(componentProps.value.title || props.payload?.title || '').trim()
        )
        .replaceAll('{{templateCode}}', String(props.payload?.templateCode || '').trim());
}

function buildDataJson() {
    try {
        return JSON.stringify(draftData.value, null, 2);
    } catch (error) {
        return '{}';
    }
}

function buildDefaultState() {
    return {
        stage: 'draft',
        editable: componentProps.value.editable !== false,
        actionStates: {
            cancel: {
                enabled: true,
                loading: false,
                visible: true,
            },
            confirm: {
                enabled: true,
                loading: false,
                visible: true,
            },
        },
    };
}

function buildTerminalState(stage) {
    return {
        stage,
        editable: false,
        actionStates: {
            cancel: {
                enabled: false,
                loading: false,
                visible: true,
            },
            confirm: {
                enabled: false,
                loading: false,
                visible: true,
            },
        },
    };
}

function mergeRenderState(base, override) {
    const baseValue = base && typeof base === 'object' && !Array.isArray(base) ? base : {};
    const overrideValue =
        override && typeof override === 'object' && !Array.isArray(override) ? override : {};
    const merged = {
        ...baseValue,
        ...overrideValue,
    };
    const baseActionStates =
        baseValue.actionStates && typeof baseValue.actionStates === 'object'
            ? baseValue.actionStates
            : {};
    const overrideActionStates =
        overrideValue.actionStates && typeof overrideValue.actionStates === 'object'
            ? overrideValue.actionStates
            : {};
    merged.actionStates = {
        ...baseActionStates,
        ...overrideActionStates,
    };
    return merged;
}

function isActionDisabled(code) {
    if (formReadonly.value) {
        return true;
    }
    const actionStates = effectiveState.value?.actionStates;
    if (!actionStates || typeof actionStates !== 'object') {
        return false;
    }
    const current = actionStates[code];
    return Boolean(current && current.enabled === false);
}
</script>

<template>
    <div
        class="w-full max-w-full space-y-4 rounded-2xl border border-emerald-200 bg-emerald-50/80 p-4 sm:max-w-[640px] lg:max-w-[760px]"
    >
        <button
            type="button"
            class="flex w-full items-start justify-between gap-3 text-left"
            @click="toggleExpanded"
        >
            <div class="min-w-0">
                <h4 class="truncate text-sm font-semibold text-emerald-900">
                    {{ componentProps.title || cardTitle }}
                </h4>
                <p class="mt-1 text-xs leading-5 text-emerald-700">
                    {{ cardSubtitle }}
                </p>
            </div>
            <span
                class="material-symbols-outlined mt-0.5 shrink-0 text-[20px] text-emerald-600 transition"
            >
                {{ expanded ? 'expand_less' : 'expand_more' }}
            </span>
        </button>

        <div
            v-if="expanded"
            class="space-y-2 rounded-xl border border-emerald-100 bg-white p-3 shadow-sm"
        >
            <template v-if="displaySections.length">
                <section
                    v-for="section in displaySections"
                    :key="section.key"
                    class="space-y-3 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0"
                >
                    <div class="flex items-center justify-between gap-3">
                        <h5 class="text-sm font-semibold text-slate-900">
                            {{ section.title }}
                        </h5>
                        <span
                            class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                        >
                            {{ section.type === 'table' ? '明细' : '主表' }}
                        </span>
                    </div>

                    <div
                        v-if="section.type === 'table'"
                        class="overflow-hidden rounded-xl border border-slate-200"
                    >
                        <div class="overflow-x-auto">
                            <table class="min-w-full divide-y divide-slate-200 text-sm">
                                <thead class="bg-slate-50">
                                    <tr>
                                        <th
                                            v-for="column in section.columns"
                                            :key="column.key"
                                            class="whitespace-nowrap px-3 py-2 text-left text-xs font-semibold text-slate-500"
                                        >
                                            <span v-if="column.required" class="mr-1 text-rose-500">
                                                *
                                            </span>
                                            {{ column.label }}
                                        </th>
                                    </tr>
                                </thead>
                                <tbody class="divide-y divide-slate-100 bg-white">
                                    <tr>
                                        <td
                                            v-for="column in section.columns"
                                            :key="column.key"
                                            class="px-3 py-2 align-top"
                                        >
                                            <input
                                                v-if="!isFieldReadonly(column)"
                                                v-model="draftData[column.key]"
                                                :type="column.inputType"
                                                class="h-10 w-full min-w-[96px] rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                                                :placeholder="`请输入${column.label}`"
                                            />
                                            <div
                                                v-else
                                                class="min-h-10 min-w-[96px] rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700"
                                            >
                                                {{ formatDisplayValue(draftData[column.key]) }}
                                            </div>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div v-else class="space-y-2">
                        <div
                            v-for="row in section.fields"
                            :key="row.key"
                            class="border-b border-slate-100 py-2.5 last:border-b-0 last:pb-0 first:pt-0"
                        >
                            <div class="flex items-start gap-3">
                                <label
                                    class="flex w-28 shrink-0 items-center gap-1 pt-2 text-sm text-slate-500"
                                >
                                    <span
                                        v-if="row.required"
                                        class="text-sm font-semibold leading-none text-rose-500"
                                    >
                                        *
                                    </span>
                                    <span class="break-all">{{ row.label }}</span>
                                </label>
                                <div class="min-w-0 flex-1">
                                    <textarea
                                        v-if="row.multiline"
                                        v-model="draftData[row.key]"
                                        rows="3"
                                        class="min-h-[88px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                                        :placeholder="`请输入${row.label}`"
                                        :readonly="isFieldReadonly(row)"
                                        :disabled="isFieldReadonly(row)"
                                    />
                                    <input
                                        v-else
                                        v-model="draftData[row.key]"
                                        :type="row.inputType"
                                        class="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                                        :placeholder="`请输入${row.label}`"
                                        :readonly="isFieldReadonly(row)"
                                        :disabled="isFieldReadonly(row)"
                                    />
                                    <p
                                        v-if="row.required && isEmptyValue(draftData[row.key])"
                                        class="mt-1 text-[11px] text-rose-500"
                                    >
                                        该字段建议补充
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>
            </template>

            <div
                v-else
                v-for="row in displayRows"
                :key="row.key"
                class="border-b border-slate-100 py-2.5 last:border-b-0 last:pb-0 first:pt-0"
            >
                <div class="flex items-start gap-3">
                    <label
                        class="flex w-28 shrink-0 items-center gap-1 pt-2 text-sm text-slate-500"
                    >
                        <span
                            v-if="row.required"
                            class="text-sm font-semibold leading-none text-rose-500"
                        >
                            *
                        </span>
                        <span class="break-all">{{ row.label }}</span>
                    </label>
                    <div class="min-w-0 flex-1">
                        <textarea
                            v-if="row.multiline"
                            v-model="draftData[row.key]"
                            rows="3"
                            class="min-h-[88px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                            :placeholder="`请输入${row.label}`"
                            :readonly="isFieldReadonly(row)"
                            :disabled="isFieldReadonly(row)"
                        />
                        <input
                            v-else
                            v-model="draftData[row.key]"
                            :type="row.inputType"
                            class="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                            :placeholder="`请输入${row.label}`"
                            :readonly="isFieldReadonly(row)"
                            :disabled="isFieldReadonly(row)"
                        />
                        <p
                            v-if="row.required && isEmptyValue(draftData[row.key])"
                            class="mt-1 text-[11px] text-rose-500"
                        >
                            该字段建议补充
                        </p>
                    </div>
                </div>
            </div>
        </div>

        <div
            v-if="expanded && displaySections.length && unknownFields.length"
            class="space-y-1 rounded-xl border border-slate-200 bg-slate-50/80 p-3"
        >
            <p class="text-xs leading-5 text-slate-500">
                未展示字段：{{ unknownFields.join('、') }}
            </p>
        </div>

        <div
            v-if="expanded && missingFields.length"
            class="space-y-1 rounded-xl border border-amber-200 bg-amber-50/80 p-3"
        >
            <p class="text-xs leading-5 text-amber-700">缺失字段：{{ missingFields.join('、') }}</p>
        </div>

        <div
            v-if="expanded && !displaySections.length && unknownFields.length"
            class="space-y-1 rounded-xl border border-slate-200 bg-slate-50/80 p-3"
        >
            <p class="text-xs leading-5 text-slate-500">
                未映射字段：{{ unknownFields.join('、') }}
            </p>
        </div>

        <div v-if="expanded" class="flex justify-end gap-3">
            <button
                type="button"
                class="inline-flex h-10 items-center justify-center rounded-xl border border-slate-200 bg-white px-4 text-sm font-medium text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:border-slate-100 disabled:bg-slate-50 disabled:text-slate-300"
                :disabled="cancelDisabled"
                @click="handleCancel"
            >
                {{ cancelLabel }}
            </button>
            <button
                type="button"
                class="inline-flex h-10 items-center justify-center rounded-xl bg-emerald-500 px-4 text-sm font-medium text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:bg-emerald-200"
                :disabled="confirmDisabled"
                @click="handleConfirm"
            >
                {{ confirmLabel }}
            </button>
        </div>
    </div>
</template>
