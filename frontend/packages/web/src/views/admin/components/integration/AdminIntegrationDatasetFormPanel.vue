<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import BaseModal from '@/components/feedback/BaseModal.vue';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    createIntegrationDataset,
    generateIntegrationDatasetDescription,
    getIntegrationDataset,
    listIntegrationDataSourceFields,
    listIntegrationDataSourceObjects,
    listIntegrationDataSources,
    listLowcodeIntegrationApps,
    listLowcodeIntegrationFields,
    listLowcodeIntegrationObjects,
    listLowcodeIntegrationPlatforms,
    updateIntegrationDataset,
} from '@/api/integration';
import { ROUTE_PATHS } from '@/router/routePaths';

const props = defineProps({
    mode: {
        type: String,
        default: 'create',
    },
    datasetId: {
        type: Number,
        default: null,
    },
});

const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const saveError = ref('');
const hydrating = ref(false);

const dataSources = ref([]);
const lowcodePlatforms = ref([]);
const lowcodeApps = ref([]);
const availableObjects = ref([]);
const availableFields = ref([]);
const activeObjectCode = ref('');
const lowcodeObjectMap = ref({});
const expandedLowcodeAppIds = ref([]);
const loadingLowcodeAppIds = ref([]);
const expandedLowcodeFolderCodes = ref([]);
const persistedLowcodeAppIds = ref([]);

const form = reactive({
    datasetCode: '',
    sourceKind: 'AI_SOURCE',
    name: '',
    description: '',
    businessLogic: '',
    aiDataSourceId: '',
    lowcodePlatformKey: '',
    lowcodeAppId: '',
    lowcodeAppName: '',
});

const selectedObjects = ref([]);
const selectedFields = ref([]);
const isViewMode = computed(() => props.mode === 'view');
const isEditMode = computed(() => props.mode === 'edit');
const sourceLocked = computed(() => isViewMode.value || isEditMode.value);
const descriptionModalOpen = ref(false);
const descriptionGenerating = ref(false);
const descriptionGenerateError = ref('');
const descriptionPromptHint = ref('');
const generatedSummary = ref('');
const generatedRelationDescription = ref('');
const pageTitle = computed(() => {
    if (props.mode === 'edit') {
        return '修改数据集';
    }
    if (props.mode === 'view') {
        return '查看数据集';
    }
    return '创建数据集';
});

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

const sourceOptions = [
    { value: 'AI_SOURCE', label: '基于 AI 平台数据源创建' },
    { value: 'LOWCODE_APP', label: '基于低代码应用创建' },
];

const aiDataSourceOptions = computed(() =>
    dataSources.value.map(item => ({
        value: item.id,
        label: item.alias || item.name,
    }))
);

const lowcodePlatformOptions = computed(() =>
    lowcodePlatforms.value.map(item => ({
        value: item.key,
        label: item.name,
    }))
);

const visibleFields = computed(() => {
    if (!activeObjectCode.value) {
        return [];
    }
    return availableFields.value.filter(
        item => sameCode(item.objectCode, activeObjectCode.value) || sameCode(item.subObjectCode, activeObjectCode.value)
    );
});

const loadedLowcodeObjects = computed(() => Object.values(lowcodeObjectMap.value).flat());
const currentAvailableObjects = computed(() =>
    (form.sourceKind === 'LOWCODE_APP' ? loadedLowcodeObjects.value : availableObjects.value)
        .filter(item => !isViewMode.value || objectIsSelected(item.objectCode))
);
const lowcodeVisibleTreeGroups = computed(() => {
    const expandedFolders = new Set(expandedLowcodeFolderCodes.value);
    return lowcodeApps.value.map(app => {
        const items = Array.isArray(lowcodeObjectMap.value[app.appId]) ? lowcodeObjectMap.value[app.appId] : [];
        const lookup = new Map(items.map(item => [item.objectCode, item]));
        const selectedCodes = new Set(selectedObjects.value.map(item => item.objectCode));
        const visibleItems = items.filter(item => {
            if (isViewMode.value) {
                if (item.folder) {
                    const hasSelectedDescendant = items.some(candidate => {
                        if (!selectedCodes.has(candidate.objectCode)) {
                            return false;
                        }
                        let parentCode = candidate.parentCode;
                        while (parentCode) {
                            if (parentCode === item.objectCode) {
                                return true;
                            }
                            parentCode = lookup.get(parentCode)?.parentCode || '';
                        }
                        return false;
                    });
                    if (!hasSelectedDescendant) {
                        return false;
                    }
                } else if (!selectedCodes.has(item.objectCode)) {
                    return false;
                }
            }
            let parentCode = item.parentCode;
            while (parentCode) {
                const parent = lookup.get(parentCode);
                if (!parent) {
                    return false;
                }
                if (!isViewMode.value && !expandedFolders.has(parentCode)) {
                    return false;
                }
                parentCode = parent.parentCode;
            }
            return true;
        });
        return {
            appId: app.appId,
            appName: app.name,
            expanded: expandedLowcodeAppIds.value.includes(app.appId),
            loading: loadingLowcodeAppIds.value.includes(app.appId),
            items: visibleItems,
        };
    });
});

const selectedFieldCount = computed(() => selectedFields.value.filter(item => item.selected !== 0).length);
const canGenerateDescription = computed(() => selectedObjects.value.length > 0 && selectedFields.value.length > 0);
const hasGeneratedDescription = computed(() => !!(generatedSummary.value || generatedRelationDescription.value));
const visibleFieldGroups = computed(() => {
    const groups = {};
    visibleFields.value.forEach(item => {
        const key = item.subObjectCode
            ? `子表：${item.subObjectName || item.subObjectCode}`
            : '主表字段';
        if (!groups[key]) {
            groups[key] = [];
        }
        groups[key].push(item);
    });
    return Object.entries(groups);
});

function objectIsSelected(objectCode) {
    return selectedObjects.value.some(item => sameCode(item.objectCode, objectCode));
}

function getFieldOwnerCode(fieldItem) {
    return fieldItem?.subObjectCode || fieldItem?.objectCode || '';
}

function normalizeCode(value) {
    return String(value || '').trim().toLowerCase();
}

function sameCode(left, right) {
    return normalizeCode(left) && normalizeCode(left) === normalizeCode(right);
}

function fieldBindingMatches(fieldItem, selectedItem) {
    if (!selectedItem || !sameCode(selectedItem.fieldName, fieldItem.fieldName)) {
        return false;
    }
    const ownerCode = getFieldOwnerCode(fieldItem);
    if (sameCode(selectedItem.objectCode, ownerCode)) {
        return true;
    }
    if (
        form.sourceKind === 'LOWCODE_APP' &&
        !selectedItem.subObjectCode &&
        !fieldItem.subObjectCode &&
        sameCode(selectedItem.objectCode, fieldItem.objectCode)
    ) {
        return true;
    }
    return false;
}

function fieldIsSelected(fieldItem) {
    return selectedFields.value.some(item => fieldBindingMatches(fieldItem, item));
}

function upsertSelectedObject(objectItem) {
    if (isViewMode.value) {
        return;
    }
    const exists = objectIsSelected(objectItem.objectCode);
    if (exists) {
        selectedObjects.value = selectedObjects.value.filter(item => !sameCode(item.objectCode, objectItem.objectCode));
        selectedFields.value = selectedFields.value.filter(
            item => !sameCode(item.objectCode, objectItem.objectCode) && !sameCode(item.subObjectCode, objectItem.objectCode)
        );
        if (sameCode(activeObjectCode.value, objectItem.objectCode)) {
            activeObjectCode.value = selectedObjects.value[0]?.objectCode || '';
        }
        return;
    }
    selectedObjects.value = [
        ...selectedObjects.value,
        {
            objectCode: objectItem.objectCode,
            formCode: objectItem.formCode || '',
            objectName: objectItem.objectName,
            objectSource: objectItem.objectSource || '',
            appCode: objectItem.appCode || '',
            appName: objectItem.appName || '',
            selected: 1,
            sortOrder: selectedObjects.value.length,
        },
    ];
    activeObjectCode.value = objectItem.objectCode;
}

function toggleField(fieldItem) {
    if (isViewMode.value) {
        return;
    }
    const ownerCode = getFieldOwnerCode(fieldItem);
    const existingIndex = selectedFields.value.findIndex(
        item => fieldBindingMatches(fieldItem, item)
    );
    if (existingIndex >= 0) {
        selectedFields.value = selectedFields.value.filter((_, index) => index !== existingIndex);
        return;
    }
    if (!objectIsSelected(ownerCode)) {
        selectedObjects.value = [
            ...selectedObjects.value,
            {
                objectCode: ownerCode,
                formCode: fieldItem.formCode || '',
                objectName: fieldItem.subObjectName || fieldItem.objectName || ownerCode,
                objectSource: fieldItem.fieldScope?.startsWith('SUB') ? 'LOWCODE_SUBTABLE' : 'LOWCODE_MAIN',
                appCode: fieldItem.appCode || '',
                appName: fieldItem.appName || '',
                selected: 1,
                sortOrder: selectedObjects.value.length,
            },
        ];
    }
    selectedFields.value = [
        ...selectedFields.value,
        {
            objectCode: ownerCode,
            formCode: fieldItem.formCode || '',
            fieldName: fieldItem.fieldName,
            fieldAlias: fieldItem.fieldLabel || fieldItem.fieldName,
            fieldType: fieldItem.fieldType || '',
            selected: 1,
            sortOrder: selectedFields.value.length,
            fieldScope: fieldItem.fieldScope || '',
            subObjectCode: fieldItem.subObjectCode || '',
            subObjectName: fieldItem.subObjectName || '',
            objectName: fieldItem.objectName || fieldItem.subObjectName || '',
            appCode: fieldItem.appCode || '',
            appName: fieldItem.appName || '',
        },
    ];
}

function isGroupFullySelected(groupFields) {
    return Array.isArray(groupFields) && groupFields.length > 0 && groupFields.every(field => fieldIsSelected(field));
}

function toggleGroupFields(groupFields) {
    if (isViewMode.value) {
        return;
    }
    if (!Array.isArray(groupFields) || !groupFields.length) {
        return;
    }
    if (isGroupFullySelected(groupFields)) {
        const groupKeys = new Set(
            groupFields.map(field => `${normalizeCode(getFieldOwnerCode(field))}|${normalizeCode(field.fieldName)}`)
        );
        selectedFields.value = selectedFields.value.filter(item => {
            const key = `${normalizeCode(item.objectCode)}|${normalizeCode(item.fieldName)}`;
            return !groupKeys.has(key);
        });
        return;
    }
    groupFields.forEach(field => {
        if (!fieldIsSelected(field)) {
            toggleField(field);
        }
    });
}

function selectedFieldAlias(fieldItem) {
    const ownerCode = getFieldOwnerCode(fieldItem);
    return selectedFields.value.find(
        item => fieldBindingMatches(fieldItem, item)
    )?.fieldAlias || fieldItem.fieldLabel || fieldItem.fieldName;
}

function updateFieldAlias(fieldItem, alias) {
    if (isViewMode.value) {
        return;
    }
    selectedFields.value = selectedFields.value.map(item => {
        if (fieldBindingMatches(fieldItem, item)) {
            return {
                ...item,
                fieldAlias: alias,
            };
        }
        return item;
    });
}

function resetSelectionWorkspace() {
    selectedObjects.value = [];
    selectedFields.value = [];
    availableObjects.value = [];
    availableFields.value = [];
    activeObjectCode.value = '';
    lowcodeApps.value = [];
    lowcodeObjectMap.value = {};
    expandedLowcodeAppIds.value = [];
    loadingLowcodeAppIds.value = [];
    expandedLowcodeFolderCodes.value = [];
    form.lowcodeAppId = '';
    form.lowcodeAppName = '';
}

function openDescriptionModal() {
    if (isViewMode.value || !canGenerateDescription.value) {
        return;
    }
    descriptionGenerateError.value = '';
    descriptionPromptHint.value = form.businessLogic || '';
    generatedSummary.value = '';
    generatedRelationDescription.value = '';
    descriptionModalOpen.value = true;
}

function closeDescriptionModal() {
    if (descriptionGenerating.value) {
        return;
    }
    descriptionModalOpen.value = false;
    descriptionGenerateError.value = '';
}

async function handleGenerateDescription() {
    if (!canGenerateDescription.value) {
        descriptionGenerateError.value = '请先完成对象和字段选择';
        return;
    }
    descriptionGenerating.value = true;
    descriptionGenerateError.value = '';
    try {
        const result = await generateIntegrationDatasetDescription(
            {
                sourceKind: form.sourceKind,
                datasetName: form.name,
                businessLogic: form.businessLogic,
                promptHint: descriptionPromptHint.value,
                objectBindings: selectedObjects.value.map(item => ({
                    objectCode: item.objectCode,
                    formCode: item.formCode,
                    objectName: item.objectName,
                    objectSource: item.objectSource,
                    selected: item.selected,
                    sortOrder: item.sortOrder,
                })),
                fieldBindings: selectedFields.value.map(item => ({
                    objectCode: item.objectCode,
                    formCode: item.formCode,
                    fieldName: item.fieldName,
                    fieldAlias: item.fieldAlias,
                    fieldType: item.fieldType,
                    selected: item.selected,
                    sortOrder: item.sortOrder,
                    fieldScope: item.fieldScope,
                    subObjectCode: item.subObjectCode,
                    subObjectName: item.subObjectName,
                    objectName: item.objectName,
                })),
            },
            handleUnauthorized
        );
        generatedSummary.value = result?.summary || '';
        generatedRelationDescription.value = result?.relationDescription || '';
    } catch (error) {
        descriptionGenerateError.value = error?.message || '生成说明失败';
    } finally {
        descriptionGenerating.value = false;
    }
}

function applyGeneratedDescription() {
    const relationText = String(generatedRelationDescription.value || '').trim();
    if (!relationText) {
        return;
    }
    form.businessLogic = relationText;
    descriptionModalOpen.value = false;
}

function restoreSelectedObjects(objectBindings, fieldBindings) {
    const normalizedObjects = Array.isArray(objectBindings) ? [...objectBindings] : [];
    const existingCodes = new Set(normalizedObjects.map(item => normalizeCode(item?.objectCode)).filter(Boolean));
    (Array.isArray(fieldBindings) ? fieldBindings : []).forEach((field, index) => {
        const normalizedFieldObjectCode = normalizeCode(field?.objectCode);
        if (!normalizedFieldObjectCode || existingCodes.has(normalizedFieldObjectCode)) {
            return;
        }
        existingCodes.add(normalizedFieldObjectCode);
        normalizedObjects.push({
            objectCode: field.objectCode,
            formCode: field.formCode || '',
            objectName: field.objectName || field.objectCode,
            objectSource: field.fieldScope?.startsWith('SUB') ? 'LOWCODE_SUBTABLE' : 'TABLE',
            selected: 1,
            sortOrder: normalizedObjects.length + index,
        });
    });
    return normalizedObjects;
}

async function loadBaseOptions() {
    const [sourceResult, platformResult] = await Promise.all([
        listIntegrationDataSources({}, handleUnauthorized),
        listLowcodeIntegrationPlatforms(handleUnauthorized),
    ]);
    dataSources.value = Array.isArray(sourceResult) ? sourceResult : [];
    lowcodePlatforms.value = Array.isArray(platformResult) ? platformResult : [];
}

async function loadAiSourceObjects() {
    availableObjects.value = [];
    availableFields.value = [];
    if (!form.aiDataSourceId) {
        return;
    }
    const objects = await listIntegrationDataSourceObjects(form.aiDataSourceId, handleUnauthorized);
    availableObjects.value = Array.isArray(objects)
        ? objects.map(item => ({
              objectCode: item.objectCode,
              formCode: item.formCode || '',
              objectName: item.objectName,
              objectSource: item.objectType,
              path: item.objectName,
          }))
        : [];
    const selectedObjectCodeSet = new Set(selectedObjects.value.map(item => normalizeCode(item.objectCode)));
    if (!selectedObjects.value.length && selectedFields.value.length) {
        selectedObjects.value = restoreSelectedObjects([], selectedFields.value).map(item => {
            const matched = availableObjects.value.find(object => sameCode(object.objectCode, item.objectCode));
            return matched ? { ...item, objectName: matched.objectName, objectSource: matched.objectSource } : item;
        });
    }
    activeObjectCode.value = selectedObjects.value.find(item => selectedObjectCodeSet.has(normalizeCode(item.objectCode)))?.objectCode
        || selectedObjects.value[0]?.objectCode
        || selectedFields.value[0]?.objectCode
        || availableObjects.value[0]?.objectCode
        || '';
    await loadActiveFields();
}

async function loadLowcodeApps() {
    lowcodeApps.value = [];
    lowcodeObjectMap.value = {};
    expandedLowcodeAppIds.value = [];
    expandedLowcodeFolderCodes.value = [];
    loadingLowcodeAppIds.value = [];
    if (!form.lowcodePlatformKey) {
        form.lowcodeAppId = '';
        form.lowcodeAppName = '';
        return;
    }
    const apps = await listLowcodeIntegrationApps(form.lowcodePlatformKey, handleUnauthorized);
    lowcodeApps.value = Array.isArray(apps) ? apps : [];
}

async function loadSelectedLowcodeApps(appIds) {
    lowcodeApps.value = [];
    lowcodeObjectMap.value = {};
    expandedLowcodeAppIds.value = [];
    expandedLowcodeFolderCodes.value = [];
    loadingLowcodeAppIds.value = [];
    if (!form.lowcodePlatformKey) {
        form.lowcodeAppId = '';
        form.lowcodeAppName = '';
        return;
    }
    const selectedIds = Array.isArray(appIds)
        ? appIds.map(item => String(item || '').trim()).filter(Boolean)
        : [];
    const apps = await listLowcodeIntegrationApps(form.lowcodePlatformKey, handleUnauthorized);
    const allApps = Array.isArray(apps) ? apps : [];
    lowcodeApps.value = selectedIds.length
        ? allApps.filter(item => selectedIds.includes(String(item.appId)))
        : allApps;
}

async function loadLowcodeObjectsByApp(appId, options = {}) {
    const { autoActivate = false } = options;
    if (!form.lowcodePlatformKey || !appId || lowcodeObjectMap.value[appId]) {
        if (autoActivate && !activeObjectCode.value) {
            activeObjectCode.value = lowcodeObjectMap.value[appId]?.find(item => !item.folder)?.objectCode || '';
        }
        return;
    }
    loadingLowcodeAppIds.value = [...new Set([...loadingLowcodeAppIds.value, appId])];
    try {
        const app = lowcodeApps.value.find(item => item.appId === appId);
        const items = await listLowcodeIntegrationObjects(form.lowcodePlatformKey, appId, handleUnauthorized);
        lowcodeObjectMap.value = {
            ...lowcodeObjectMap.value,
            [appId]: (Array.isArray(items) ? items : []).map(item => ({
                ...item,
                appCode: item.appCode || appId,
                appName: app?.name || appId,
                path: item.path || item.objectName,
            })),
        };
        if (autoActivate && !activeObjectCode.value) {
            activeObjectCode.value = selectedObjects.value[0]?.objectCode || lowcodeObjectMap.value[appId]?.find(item => !item.folder)?.objectCode || '';
        }
    } finally {
        loadingLowcodeAppIds.value = loadingLowcodeAppIds.value.filter(item => item !== appId);
    }
}

async function toggleLowcodeApp(appId) {
    if (!appId) {
        return;
    }
    const expanded = expandedLowcodeAppIds.value.includes(appId);
    if (expanded) {
        expandedLowcodeAppIds.value = expandedLowcodeAppIds.value.filter(item => item !== appId);
        return;
    }
    expandedLowcodeAppIds.value = [...expandedLowcodeAppIds.value, appId];
    await loadLowcodeObjectsByApp(appId, { autoActivate: true });
}

function toggleLowcodeFolder(objectCode) {
    if (!objectCode) {
        return;
    }
    expandedLowcodeFolderCodes.value = expandedLowcodeFolderCodes.value.includes(objectCode)
        ? expandedLowcodeFolderCodes.value.filter(item => item !== objectCode)
        : [...expandedLowcodeFolderCodes.value, objectCode];
}

function lowcodeFolderExpanded(objectCode) {
    return expandedLowcodeFolderCodes.value.includes(objectCode);
}

function lowcodeObjectIndentStyle(item) {
    return {
        paddingLeft: `${12 + ((item.depth || 0) * 20)}px`,
    };
}

function handleLowcodeObjectClick(item) {
    if (item.folder) {
        toggleLowcodeFolder(item.objectCode);
        return;
    }
    upsertSelectedObject(item);
}

async function preloadLowcodeAppsForEdit(appIds = []) {
    const normalizedAppIds = Array.isArray(appIds)
        ? appIds.map(item => String(item || '').trim()).filter(Boolean)
        : [];
    if (!normalizedAppIds.length) {
        return;
    }
    expandedLowcodeAppIds.value = [...new Set([...expandedLowcodeAppIds.value, ...normalizedAppIds])];
    for (const appId of normalizedAppIds) {
        await loadLowcodeObjectsByApp(appId, { autoActivate: false });
    }
    const lowcodeLookup = new Map(loadedLowcodeObjects.value.map(item => [normalizeCode(item.objectCode), item]));
    const expandedFolders = new Set(expandedLowcodeFolderCodes.value);
    selectedObjects.value.forEach(item => {
        let parentCode = lowcodeLookup.get(normalizeCode(item.objectCode))?.parentCode || '';
        while (parentCode) {
            expandedFolders.add(parentCode);
            parentCode = lowcodeLookup.get(normalizeCode(parentCode))?.parentCode || '';
        }
    });
    expandedLowcodeFolderCodes.value = Array.from(expandedFolders);
    const loadedMenuCodes = new Set(loadedLowcodeObjects.value.filter(item => !item.folder).map(item => normalizeCode(item.objectCode)));
    const preferredObjectCode = selectedObjects.value.find(item => loadedMenuCodes.has(normalizeCode(item.objectCode)))?.objectCode
        || selectedFields.value.find(item => loadedMenuCodes.has(normalizeCode(item.objectCode)))?.objectCode
        || loadedLowcodeObjects.value.find(item => !item.folder)?.objectCode
        || '';
    if (!loadedMenuCodes.has(normalizeCode(activeObjectCode.value))) {
        activeObjectCode.value = preferredObjectCode;
    } else if (!activeObjectCode.value) {
        activeObjectCode.value = preferredObjectCode;
    }
    if (activeObjectCode.value) {
        await loadActiveFields();
    }
}

async function loadActiveFields() {
    availableFields.value = [];
    if (!activeObjectCode.value) {
        return;
    }
    if (form.sourceKind === 'AI_SOURCE' && form.aiDataSourceId) {
        const fields = await listIntegrationDataSourceFields(
            form.aiDataSourceId,
            activeObjectCode.value,
            handleUnauthorized
        );
        availableFields.value = Array.isArray(fields)
            ? fields.map(item => ({
                  objectCode: item.objectCode,
                  fieldName: item.fieldName,
                  fieldLabel: item.comment || item.fieldName,
                  fieldType: item.fieldType,
                  fieldScope: 'MAIN',
                  subObjectCode: '',
                  subObjectName: '',
                  objectName: item.objectCode,
              }))
            : [];
        return;
    }
    if (form.sourceKind === 'LOWCODE_APP' && form.lowcodePlatformKey) {
        const activeObject = loadedLowcodeObjects.value.find(item => sameCode(item.objectCode, activeObjectCode.value));
        if (!activeObject?.appCode) {
            return;
        }
        const fields = await listLowcodeIntegrationFields(
            form.lowcodePlatformKey,
            activeObject.appCode,
            activeObjectCode.value,
            activeObject.formCode || '',
            handleUnauthorized
        );
        availableFields.value = Array.isArray(fields)
            ? fields.map(item => ({
                  ...item,
                  objectName: activeObject.objectName || activeObjectCode.value,
                  formCode: item.formCode || activeObject.formCode || '',
                  appCode: activeObject.appCode,
                  appName: activeObject.appName || '',
              }))
            : [];
    }
}

async function loadDatasetDetail() {
    if (!['edit', 'view'].includes(props.mode) || !props.datasetId) {
        return;
    }
    const detail = await getIntegrationDataset(props.datasetId, handleUnauthorized);
    form.datasetCode = detail?.datasetCode || '';
    form.sourceKind = detail?.sourceKind || 'AI_SOURCE';
    form.name = detail?.name || '';
    form.description = detail?.description || '';
    form.businessLogic = detail?.businessLogic || '';
    form.aiDataSourceId = detail?.aiDataSourceId || '';
    form.lowcodePlatformKey = detail?.lowcodePlatformKey || '';
    form.lowcodeAppId = detail?.lowcodeAppId || '';
    form.lowcodeAppName = detail?.lowcodeAppName || '';
    persistedLowcodeAppIds.value = (detail?.lowcodeAppId || '')
        .split(',')
        .map(item => item.trim())
        .filter(Boolean);
    selectedFields.value = Array.isArray(detail?.fieldBindings) ? detail.fieldBindings : [];
    selectedObjects.value = restoreSelectedObjects(detail?.objectBindings, selectedFields.value);
    activeObjectCode.value = selectedObjects.value[0]?.objectCode || selectedFields.value[0]?.objectCode || '';
}

async function handleSave() {
    saveError.value = '';
    if (!form.name?.trim()) {
        saveError.value = '数据集名称不能为空';
        return;
    }
    if (form.sourceKind === 'AI_SOURCE' && !form.aiDataSourceId) {
        saveError.value = '请选择 AI 数据源';
        return;
    }
    if (form.sourceKind === 'LOWCODE_APP' && !form.lowcodePlatformKey) {
        saveError.value = '请选择低代码平台';
        return;
    }
    if (!selectedFields.value.length) {
        saveError.value = '至少选择一个字段';
        return;
    }
    saving.value = true;
    try {
        const selectedAppMap = new Map();
        [...selectedObjects.value, ...selectedFields.value].forEach(item => {
            if (item?.appCode) {
                selectedAppMap.set(item.appCode, item.appName || item.appCode);
            }
        });
        const payload = {
            name: form.name,
            sourceKind: form.sourceKind,
            aiDataSourceId: form.sourceKind === 'AI_SOURCE' ? Number(form.aiDataSourceId) : null,
            lowcodePlatformKey: form.sourceKind === 'LOWCODE_APP' ? form.lowcodePlatformKey : '',
            lowcodeAppId: form.sourceKind === 'LOWCODE_APP' ? Array.from(selectedAppMap.keys()).join(',') : '',
            lowcodeAppName: form.sourceKind === 'LOWCODE_APP' ? Array.from(selectedAppMap.values()).join('、') : '',
            description: form.description,
            businessLogic: form.businessLogic,
            status: 'ACTIVE',
            objectBindings: selectedObjects.value.map(item => ({
                objectCode: item.objectCode,
                formCode: item.formCode,
                objectName: item.objectName,
                objectSource: item.objectSource,
                selected: item.selected,
                sortOrder: item.sortOrder,
            })),
            fieldBindings: selectedFields.value.map(item => ({
                objectCode: item.objectCode,
                formCode: item.formCode,
                fieldName: item.fieldName,
                fieldAlias: item.fieldAlias,
                fieldType: item.fieldType,
                selected: item.selected,
                sortOrder: item.sortOrder,
                fieldScope: item.fieldScope,
                subObjectCode: item.subObjectCode,
                subObjectName: item.subObjectName,
                objectName: item.objectName,
            })),
            relationBindings: [],
        };
        if (props.mode === 'edit' && props.datasetId) {
            const updated = await updateIntegrationDataset(props.datasetId, payload, handleUnauthorized);
            form.datasetCode = updated?.datasetCode || form.datasetCode;
        } else {
            const created = await createIntegrationDataset(payload, handleUnauthorized);
            form.datasetCode = created?.datasetCode || form.datasetCode;
        }
        router.push(ROUTE_PATHS.adminIntegrationDatasets);
    } catch (error) {
        saveError.value = error?.message || '保存失败';
    } finally {
        saving.value = false;
    }
}

watch(
    () => form.sourceKind,
    nextValue => {
        if (hydrating.value) {
            return;
        }
        resetSelectionWorkspace();
        if (nextValue === 'AI_SOURCE') {
            form.lowcodePlatformKey = '';
            return;
        }
        form.aiDataSourceId = '';
    }
);

watch(
    () => form.aiDataSourceId,
    async nextValue => {
        if (form.sourceKind !== 'AI_SOURCE') {
            return;
        }
        if (hydrating.value) {
            return;
        }
        resetSelectionWorkspace();
        if (!nextValue) {
            return;
        }
        await loadAiSourceObjects();
    }
);

watch(
    () => form.lowcodePlatformKey,
    async nextValue => {
        if (form.sourceKind !== 'LOWCODE_APP') {
            return;
        }
        if (hydrating.value) {
            return;
        }
        resetSelectionWorkspace();
        if (!nextValue) {
            return;
        }
        await loadLowcodeApps();
    }
);

watch(activeObjectCode, async nextValue => {
    if (!nextValue) {
        return;
    }
    await loadActiveFields();
});

onMounted(async () => {
    loading.value = true;
    loadError.value = '';
    hydrating.value = true;
    try {
        await loadBaseOptions();
        await loadDatasetDetail();
        if (form.sourceKind === 'AI_SOURCE' && form.aiDataSourceId) {
            await loadAiSourceObjects();
        }
        if (form.sourceKind === 'LOWCODE_APP' && form.lowcodePlatformKey) {
            await loadLowcodeApps();
            await preloadLowcodeAppsForEdit(persistedLowcodeAppIds.value);
            if (activeObjectCode.value) {
                await loadActiveFields();
            }
        }
    } catch (error) {
        loadError.value = error?.message || '页面初始化失败';
    } finally {
        hydrating.value = false;
        loading.value = false;
    }
});
</script>

<template>
    <div class="flex h-full flex-col overflow-hidden bg-[#f6f7f8]">
        <header class="flex items-center justify-between border-b border-slate-200 bg-white px-8 py-4">
            <div class="flex items-center gap-4">
                <button
                    class="flex h-8 w-8 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100"
                    type="button"
                    @click="router.push(ROUTE_PATHS.adminIntegrationDatasets)"
                >
                    <span class="material-symbols-outlined">arrow_back</span>
                </button>
                <div class="flex items-center gap-3">
                    <h1 class="text-xl font-bold text-slate-900">{{ pageTitle }}</h1>
                    <span v-if="isEditMode && form.datasetCode" class="text-sm text-slate-500">
                        【{{ form.datasetCode }}】
                    </span>
                </div>
            </div>
        </header>

        <div class="flex-1 overflow-y-auto p-8">
            <div v-if="loadError" class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                {{ loadError }}
            </div>
            <div v-if="saveError" class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                {{ saveError }}
            </div>

            <div v-if="loading" class="py-16 text-center text-sm text-slate-400">页面加载中...</div>

            <div v-else class="space-y-6">
                <section class="rounded-[1.25rem] border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[1.2fr_1fr] xl:items-start">
                        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <button
                                v-for="option in sourceOptions"
                                :key="option.value"
                                class="rounded-2xl border p-4 text-left transition-all"
                                :class="
                                    form.sourceKind === option.value
                                        ? 'border-primary bg-blue-50/70 shadow-sm'
                                        : 'border-slate-200 bg-slate-50 hover:border-primary/40'
                                "
                                type="button"
                                :disabled="sourceLocked"
                                @click="!sourceLocked && (form.sourceKind = option.value)"
                            >
                                <div class="flex items-start gap-4">
                                    <div
                                        class="flex h-10 w-10 items-center justify-center rounded-xl"
                                        :class="form.sourceKind === option.value ? 'bg-primary text-white' : 'bg-white text-slate-400'"
                                    >
                                        <span class="material-symbols-outlined">{{
                                            option.value === 'AI_SOURCE' ? 'database' : 'widgets'
                                        }}</span>
                                    </div>
                                    <div>
                                        <div class="text-sm font-bold text-slate-900">{{ option.label }}</div>
                                        <div class="mt-1 text-xs leading-5 text-slate-500">
                                            {{
                                                option.value === 'AI_SOURCE'
                                                    ? '基于 AI 平台已接入的数据源创建统一数据集'
                                                    : '按低代码应用与菜单结构组合创建数据集'
                                            }}
                                        </div>
                                    </div>
                                </div>
                            </button>
                        </div>

                        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                            <label v-if="form.sourceKind === 'AI_SOURCE'" class="space-y-2 xl:col-span-1">
                                <span class="text-sm font-semibold text-slate-500">AI 数据源</span>
                                <AppSelect
                                    v-model="form.aiDataSourceId"
                                    :options="aiDataSourceOptions"
                                    placeholder="请选择数据源"
                                    :disabled="sourceLocked"
                                    button-class="border-slate-200 bg-slate-50 shadow-none"
                                    menu-class="w-full"
                                />
                            </label>
                            <label v-if="form.sourceKind === 'LOWCODE_APP'" class="space-y-2 xl:col-span-1">
                                <span class="text-sm font-semibold text-slate-500">低代码平台</span>
                                <AppSelect
                                    v-model="form.lowcodePlatformKey"
                                    :options="lowcodePlatformOptions"
                                    placeholder="请选择平台"
                                    :disabled="sourceLocked"
                                    button-class="border-slate-200 bg-slate-50 shadow-none"
                                    menu-class="w-full"
                                />
                            </label>
                            <label class="space-y-2 xl:col-span-2">
                                <span class="text-sm font-semibold text-slate-500">数据集名称</span>
                                <input v-model="form.name" :disabled="isViewMode" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:opacity-70" placeholder="输入数据集名称" />
                            </label>
                        </div>
                    </div>
                </section>

                <section class="rounded-[1.25rem] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-6 flex items-center justify-between">
                        <div class="flex items-center gap-2">
                            <span class="material-symbols-outlined text-primary text-xl">tune</span>
                            <h2 class="text-base font-bold text-slate-900">对象与字段配置</h2>
                        </div>
                        <div class="flex gap-2 text-[11px] text-slate-500">
                            <span class="rounded bg-slate-100 px-2 py-1">{{ selectedObjects.length }} 个对象</span>
                            <span class="rounded bg-slate-100 px-2 py-1">{{ selectedFieldCount }} 个字段</span>
                        </div>
                    </div>

                    <div class="grid grid-cols-1 gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
                        <aside class="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
                            <h3 class="mb-4 flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-slate-400">
                                <span class="material-symbols-outlined text-sm">table_chart</span>
                                {{ form.sourceKind === 'LOWCODE_APP' ? '应用与菜单' : '包含对象' }}
                            </h3>
                            <div v-if="form.sourceKind === 'LOWCODE_APP'" class="max-h-[760px] space-y-3 overflow-y-auto pr-1">
                                <section
                                    v-for="group in lowcodeVisibleTreeGroups"
                                    :key="group.appId"
                                    class="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50/70"
                                >
                                    <button
                                        class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-100/80"
                                        type="button"
                                        @click="toggleLowcodeApp(group.appId)"
                                    >
                                        <div class="min-w-0">
                                            <div class="truncate text-sm font-semibold text-slate-800">
                                                {{ group.appName }}
                                            </div>
                                            <div class="mt-1 text-[10px] text-slate-400">
                                                {{ group.appId }}
                                            </div>
                                        </div>
                                        <div class="flex items-center gap-2 text-slate-400">
                                            <span v-if="group.loading" class="text-[10px] font-semibold">加载中</span>
                                            <span
                                                class="material-symbols-outlined text-[18px] transition-transform"
                                                :class="group.expanded ? 'rotate-180 text-primary' : ''"
                                            >
                                                expand_more
                                            </span>
                                        </div>
                                    </button>

                                    <div v-if="group.expanded" class="border-t border-slate-200 bg-white/80 py-2">
                                        <div v-if="group.loading" class="px-4 py-3 text-xs text-slate-400">
                                            正在加载菜单...
                                        </div>
                                        <div
                                            v-else-if="!group.items.length"
                                            class="px-4 py-3 text-xs text-slate-400"
                                        >
                                            暂无可用菜单
                                        </div>
                                        <div v-else class="space-y-1">
                                            <button
                                                v-for="item in group.items"
                                                :key="item.objectCode"
                                                class="flex w-full items-center gap-3 py-2 pr-3 text-left transition-colors hover:bg-slate-50"
                                                :class="
                                                    !item.folder && objectIsSelected(item.objectCode)
                                                        ? 'bg-blue-50/70'
                                                        : ''
                                                "
                                                :style="lowcodeObjectIndentStyle(item)"
                                                type="button"
                                                @click="handleLowcodeObjectClick(item)"
                                            >
                                                <span
                                                    v-if="item.folder"
                                                    class="material-symbols-outlined text-[18px] text-slate-400 transition-transform"
                                                    :class="lowcodeFolderExpanded(item.objectCode) ? 'rotate-90 text-primary' : ''"
                                                >
                                                    chevron_right
                                                </span>
                                                <span
                                                    v-else
                                                    class="flex h-5 w-5 items-center justify-center rounded border-2 text-white"
                                                    :class="
                                                        objectIsSelected(item.objectCode)
                                                            ? 'border-primary bg-primary'
                                                            : 'border-slate-200 bg-white'
                                                    "
                                                >
                                                    <span
                                                        v-if="objectIsSelected(item.objectCode)"
                                                        class="material-symbols-outlined text-[14px]"
                                                    >
                                                        check
                                                    </span>
                                                </span>
                                                <div class="min-w-0 flex-1">
                                                    <div
                                                        class="truncate text-sm"
                                                        :class="item.folder ? 'font-semibold text-slate-700' : 'font-medium text-slate-800'"
                                                    >
                                                        {{ item.objectName }}
                                                    </div>
                                                    <div
                                                        v-if="!item.folder && item.description"
                                                        class="mt-0.5 truncate text-[10px] text-slate-500"
                                                    >
                                                        表名：{{ item.description }}
                                                    </div>
                                                </div>
                                            </button>
                                        </div>
                                    </div>
                                </section>
                            </div>
                            <div v-else class="max-h-[760px] space-y-3 overflow-y-auto pr-1">
                                <button
                                    v-for="item in currentAvailableObjects"
                                    :key="item.objectCode"
                                    class="relative w-full rounded-xl border p-3 text-left transition-all"
                                    :class="
                                        objectIsSelected(item.objectCode)
                                            ? 'border-primary bg-blue-50/60'
                                            : 'border-transparent hover:bg-slate-50'
                                    "
                                    type="button"
                                    @click="upsertSelectedObject(item)"
                                >
                                    <div class="flex items-center gap-3">
                                        <div
                                            class="flex h-5 w-5 items-center justify-center rounded border-2 text-white"
                                            :class="objectIsSelected(item.objectCode) ? 'border-primary bg-primary' : 'border-slate-200 bg-white'"
                                        >
                                            <span v-if="objectIsSelected(item.objectCode)" class="material-symbols-outlined text-[14px]">check</span>
                                        </div>
                                        <div class="min-w-0">
                                            <div class="truncate text-sm font-semibold text-slate-800">{{ item.objectName }}</div>
                                            <div class="mt-0.5 truncate text-[10px] text-slate-400">
                                                表名：{{ item.objectCode }}
                                            </div>
                                        </div>
                                    </div>
                                </button>
                            </div>
                        </aside>

                        <div class="space-y-5">
                            <section class="rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
                                <div class="mb-4 flex items-center justify-between gap-4">
                                    <div class="flex items-center gap-2">
                                        <span class="material-symbols-outlined text-primary text-xl">rule_settings</span>
                                        <h2 class="text-base font-bold text-slate-900">字段选择</h2>
                                    </div>
                                    <div class="flex gap-2 text-[11px] text-slate-500">
                                        <span class="rounded bg-white px-2 py-1 ring-1 ring-slate-200">{{ selectedObjects.length }} 个对象</span>
                                        <span class="rounded bg-white px-2 py-1 ring-1 ring-slate-200">{{ selectedFieldCount }} 个字段</span>
                                    </div>
                                </div>
                                <div class="mb-4 flex flex-wrap gap-2">
                                    <button
                                        v-for="item in selectedObjects"
                                        :key="item.objectCode"
                                        class="rounded-full px-3 py-1 text-xs font-semibold transition-colors"
                                        :class="activeObjectCode === item.objectCode ? 'bg-primary text-white' : 'bg-white text-slate-500 ring-1 ring-slate-200'"
                                        type="button"
                                        @click="activeObjectCode = item.objectCode"
                                    >
                                        {{ item.objectName || item.objectCode }}
                                    </button>
                                </div>

                                <div class="max-h-[760px] space-y-5 overflow-y-auto pr-1">
                                    <section v-for="[groupName, groupFields] in visibleFieldGroups" :key="groupName">
                                        <div class="mb-3 flex items-center gap-3">
                                            <h3 class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">{{ groupName }}</h3>
                                            <div class="h-px flex-1 bg-slate-100" />
                                            <button
                                                class="rounded-full border border-slate-200 bg-white px-3 py-1 text-[11px] font-semibold text-slate-500 transition-colors hover:border-primary hover:text-primary"
                                                type="button"
                                                @click="toggleGroupFields(groupFields)"
                                            >
                                                {{ isGroupFullySelected(groupFields) ? '取消全选' : '全选' }}
                                            </button>
                                        </div>
                                        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                                            <article
                                                v-for="field in groupFields"
                                                :key="`${field.subObjectCode || field.objectCode}-${field.fieldName}`"
                                                class="relative cursor-pointer rounded-xl border p-3 transition-all"
                                                :class="
                                                    fieldIsSelected(field)
                                                        ? 'border-primary bg-blue-50/40 shadow-sm'
                                                        : 'border-slate-200 bg-white hover:border-primary/40'
                                                "
                                                @click="toggleField(field)"
                                            >
                                                <div class="mb-2 flex items-start justify-between gap-3">
                                                    <span class="text-[10px] text-slate-400">{{ field.fieldScope || 'MAIN' }}</span>
                                                    <span
                                                        class="flex h-5 w-5 shrink-0 items-center justify-center rounded border-2 transition-colors"
                                                        :class="
                                                            fieldIsSelected(field)
                                                                ? 'border-primary bg-primary text-white'
                                                                : 'border-slate-200 bg-white text-transparent'
                                                        "
                                                    >
                                                        <span class="material-symbols-outlined text-[14px]">check</span>
                                                    </span>
                                                </div>
                                                <div class="mb-1 text-sm font-bold leading-5 text-slate-900">{{ field.fieldLabel || field.fieldName }}</div>
                                                <div class="text-[11px] leading-5 text-slate-400">原始字段：{{ field.fieldName }}</div>
                                                <div v-if="field.subObjectCode" class="mt-1 text-[11px] leading-5 text-emerald-500">
                                                    子表：{{ field.subObjectName || field.subObjectCode }}
                                                </div>
                                                <input
                                                    v-if="fieldIsSelected(field)"
                                                    :value="selectedFieldAlias(field)"
                                                    :disabled="isViewMode"
                                                    class="mt-2 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:opacity-70"
                                                    placeholder="字段别名"
                                                    @click.stop
                                                    @input="updateFieldAlias(field, $event.target.value)"
                                                />
                                            </article>
                                        </div>
                                    </section>
                                </div>
                            </section>

                        </div>
                    </div>
                </section>

                <section class="rounded-[1.25rem] border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="mb-4 flex items-center justify-between">
                        <div class="flex items-center gap-2">
                            <span class="material-symbols-outlined text-primary text-xl">description</span>
                            <h2 class="text-base font-bold text-slate-900">数据集业务逻辑说明</h2>
                        </div>
                        <button
                            v-if="!isViewMode"
                            class="rounded-lg bg-blue-50 px-4 py-1.5 text-sm font-semibold text-primary transition-colors hover:bg-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                            type="button"
                            :disabled="!canGenerateDescription"
                            @click="openDescriptionModal"
                        >
                            AI 生成说明
                        </button>
                    </div>
                    <div
                        v-if="form.sourceKind === 'LOWCODE_APP'"
                        class="mb-4 rounded-2xl border border-blue-100 bg-blue-50/70 px-4 py-3 text-sm leading-6 text-slate-600"
                    >
                        当前低代码数据集默认按主子表结构组织，系统会基于主表与子表字段自动识别关联关系。
                        建议在这里重点描述业务实体含义、主子表语义、关键字段用途和查询口径，后续可由 AI 结合对象与字段描述自动补全说明。
                    </div>
                    <textarea
                        v-model="form.businessLogic"
                        :disabled="isViewMode"
                        class="h-40 w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:opacity-70"
                        :placeholder="
                            form.sourceKind === 'LOWCODE_APP'
                                ? '请描述主表和子表分别承载什么业务、关键字段代表什么含义，以及查询时应如何理解这些数据...'
                                : '请描述数据集的业务逻辑、核心对象含义、关键字段语义和常见查询口径...'
                        "
                    />
                </section>

                <div class="flex justify-end gap-3">
                    <button class="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-white" type="button" @click="router.push(ROUTE_PATHS.adminIntegrationDatasets)">
                        {{ isViewMode ? '返回' : '取消' }}
                    </button>
                    <button v-if="!isViewMode" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition-all active:scale-95" type="button" :disabled="saving" @click="handleSave">
                        {{ saving ? '保存中...' : '保存数据集' }}
                    </button>
                </div>
            </div>
        </div>

        <BaseModal :open="descriptionModalOpen" panel-class="max-w-5xl" @close="closeDescriptionModal">
            <template #header>
                <div class="border-b border-slate-200 px-6 py-5">
                    <div class="flex items-center justify-between">
                        <div>
                            <h2 class="text-lg font-bold text-slate-900">AI 生成说明</h2>
                            <p class="mt-1 text-sm text-slate-400">结合已选对象、字段和简短业务描述，生成可回填的数据集说明草稿。</p>
                        </div>
                        <button class="text-slate-400 transition-colors hover:text-slate-700" type="button" @click="closeDescriptionModal">
                            <span class="material-symbols-outlined">close</span>
                        </button>
                    </div>
                </div>
            </template>

            <template #content>
                <div class="grid grid-cols-1 gap-0 lg:grid-cols-[320px_minmax(0,1fr)]">
                    <section class="border-b border-slate-200 bg-slate-50/70 px-6 py-6 lg:border-b-0 lg:border-r">
                        <div class="space-y-5">
                            <div>
                                <h3 class="text-sm font-bold text-slate-900">补充描述</h3>
                                <p class="mt-1 text-xs leading-5 text-slate-500">输入简短业务背景、重点问题或希望 AI 强调的对象关系。</p>
                            </div>
                            <label class="space-y-2">
                                <span class="text-sm font-semibold text-slate-500">简短描述</span>
                                <textarea
                                    v-model="descriptionPromptHint"
                                    class="h-44 w-full resize-none rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm leading-6 outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    placeholder="例如：重点说明客户主表与跟进记录子表之间的关系，并提醒补充状态字段的枚举含义。"
                                />
                            </label>
                            <div class="rounded-2xl border border-slate-200 bg-white px-4 py-4">
                                <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">当前上下文</div>
                                <div class="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                                    <span class="rounded-full bg-slate-100 px-3 py-1">{{ selectedObjects.length }} 个对象</span>
                                    <span class="rounded-full bg-slate-100 px-3 py-1">{{ selectedFieldCount }} 个字段</span>
                                </div>
                                <div class="mt-3 text-xs leading-6 text-slate-500">
                                    {{
                                        form.sourceKind === 'LOWCODE_APP'
                                            ? 'AI 会结合当前低代码菜单、主表/子表字段与别名生成说明。'
                                            : 'AI 会结合当前已选表、字段、注释名与别名生成说明。'
                                    }}
                                </div>
                            </div>
                        </div>
                    </section>

                    <section class="px-6 py-6">
                        <div v-if="descriptionGenerateError" class="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                            {{ descriptionGenerateError }}
                        </div>
                        <div class="space-y-5">
                            <div class="rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
                                <div class="mb-3 flex items-center justify-between gap-3">
                                    <h3 class="text-sm font-bold text-slate-900">数据集摘要</h3>
                                    <button
                                        class="rounded-lg bg-blue-50 px-4 py-1.5 text-sm font-semibold text-primary transition-colors hover:bg-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                                        type="button"
                                        :disabled="descriptionGenerating"
                                        @click="handleGenerateDescription"
                                    >
                                        {{ descriptionGenerating ? '生成中...' : '开始生成' }}
                                    </button>
                                </div>
                                <div class="min-h-[120px] rounded-xl border border-dashed border-slate-200 bg-white px-4 py-3 text-sm leading-7 text-slate-600">
                                    {{ generatedSummary || '生成后将在这里展示该数据集的整体用途和推荐查询方向。' }}
                                </div>
                            </div>

                            <div class="rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
                                <div class="mb-3 flex items-center justify-between gap-3">
                                    <h3 class="text-sm font-bold text-slate-900">关系说明</h3>
                                    <span class="text-xs text-slate-400">需要人工补充的信息会直接写在这段文字里</span>
                                </div>
                                <div class="min-h-[220px] rounded-xl border border-dashed border-slate-200 bg-white px-4 py-3 text-sm leading-7 text-slate-600 whitespace-pre-wrap">
                                    {{ generatedRelationDescription || '生成后将在这里展示对象关系、关键字段作用以及建议补充的业务口径。' }}
                                </div>
                            </div>
                        </div>
                    </section>
                </div>
            </template>

            <template #footer>
                <div class="flex items-center justify-between border-t border-slate-200 px-6 py-4">
                    <div class="text-xs text-slate-400">
                        生成结果不会自动覆盖原说明，需你确认后回填。
                    </div>
                    <div class="flex gap-3">
                        <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50" type="button" @click="closeDescriptionModal">
                            关闭
                        </button>
                        <button
                            class="rounded-xl bg-primary px-5 py-2 text-sm font-semibold text-white transition-all active:scale-95 disabled:cursor-not-allowed disabled:bg-slate-300"
                            type="button"
                            :disabled="!hasGeneratedDescription"
                            @click="applyGeneratedDescription"
                        >
                            回填说明
                        </button>
                    </div>
                </div>
            </template>
        </BaseModal>
    </div>
</template>
