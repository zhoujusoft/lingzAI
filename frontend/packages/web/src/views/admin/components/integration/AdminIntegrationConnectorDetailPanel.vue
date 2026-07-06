<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import {
    createIntegrationConnectorApi,
    deleteIntegrationConnectorApi,
    disableIntegrationConnectorApi,
    getIntegrationConnector,
    getIntegrationConnectorApi,
    listIntegrationConnectorApis,
    previewIntegrationConnectorApiCode,
    publishIntegrationConnectorApi,
    testIntegrationConnectorApi,
    testIntegrationConnectorAuth,
    updateIntegrationConnector,
    updateIntegrationConnectorApi,
} from '@/api/integration';
import { clearUserSession } from '@/composables/useCurrentUser';
import { confirm } from '@/composables/useModal';
import {
    getResourcePermissionBadgeClass,
    getResourcePermissionDescription,
    getResourcePermissionLabel,
    normalizeResourcePermissionScope,
} from '@/model/resource-permissions';
import { ROUTE_PATHS } from '@/router/routePaths';
import AdminIntegrationConnectorApiSection from './AdminIntegrationConnectorApiSection.vue';
import AdminIntegrationConnectorAuthSection from './AdminIntegrationConnectorAuthSection.vue';
import IntegrationJsonResultPicker from './IntegrationJsonResultPicker.vue';
import {
    INTEGRATION_CONTENT_TYPE_OPTIONS,
    INTEGRATION_METHOD_OPTIONS,
    INTEGRATION_PERMISSION_OPTIONS,
    INTEGRATION_REQUEST_TABS,
    INTEGRATION_SELECT_BUTTON_CLASS,
    INTEGRATION_STATUS_OPTIONS,
    buildIntegrationInputClass,
    getIntegrationStatusMeta,
} from './integrationConnectorShared';

const props = defineProps({
    connectorId: {
        type: Number,
        default: null,
    },
    defaultTab: {
        type: String,
        default: 'base',
    },
});

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const loadError = ref('');
const connector = ref(null);
const connectorApis = ref([]);
const apiListLoading = ref(false);
const apiListError = ref('');
const message = ref('');

const activeTab = ref(props.defaultTab || 'base');
const activeAuthRequestTab = ref('headers');
const activeApiRequestTab = ref('headers');
const selectedAuthId = ref('');
const authItems = ref([]);

const basicSaving = ref(false);
const basicSaveError = ref('');
const authSaving = ref(false);
const authSaveError = ref('');
const authTesting = ref(false);
const authTestError = ref('');
const authTestResult = ref(null);
const authTestVariablesText = ref('{}');

const apiEditorOpen = ref(false);
const apiEditorMode = ref('create');
const editingApiId = ref(null);
const apiSaveError = ref('');
const apiSaving = ref(false);
const apiTesting = ref(false);
const apiTestError = ref('');
const apiTestResult = ref(null);
const apiTestArgumentsText = ref('{}');
const apiPublishLoadingId = ref(null);
const outputPickerOpen = ref(false);
const outputPickerTarget = ref('api');

const apiCodeEditedManually = ref(false);
const lastGeneratedApiCode = ref('');
const apiCodeGenerating = ref(false);
let apiCodeGenerateTimer = null;
let apiCodeGenerateToken = 0;
let messageTimer = null;

const basicForm = reactive({
    name: '',
    alias: '',
    baseUrl: '',
    permissionScope: normalizeResourcePermissionScope(),
    status: 'ACTIVE',
});

const authForm = reactive(createEmptyAuthForm());
const apiForm = reactive(createEmptyApiForm());
const apiFieldTouched = reactive({
    apiName: false,
    apiCode: false,
    url: false,
});

const mainTabs = [
    { value: 'base', label: '基础信息' },
    { value: 'auth', label: '鉴权' },
    { value: 'api', label: 'API 管理' },
];

const connectorStatusOptions = INTEGRATION_STATUS_OPTIONS;
const permissionOptions = INTEGRATION_PERMISSION_OPTIONS;
const methodOptions = INTEGRATION_METHOD_OPTIONS;
const contentTypeOptions = INTEGRATION_CONTENT_TYPE_OPTIONS;
const requestTabs = INTEGRATION_REQUEST_TABS;
const formSelectButtonClass = INTEGRATION_SELECT_BUTTON_CLASS;

const connectorIdValue = computed(() => {
    const raw = props.connectorId ?? route.params.connectorId;
    const id = Number(raw);
    return Number.isFinite(id) ? id : null;
});

const pageTitle = computed(() => connector.value?.name || connector.value?.alias || '连接器详情');
const pageSubtitle = computed(() => connector.value?.baseUrl || '未配置接口基础地址');
const canOperateConnector = computed(() => connector.value?.canOperate !== false);
const currentPermissionLabel = computed(() =>
    getResourcePermissionLabel(basicForm.permissionScope)
);
const currentPermissionDescription = computed(() =>
    getResourcePermissionDescription(basicForm.permissionScope)
);
const currentPermissionClass = computed(() =>
    getResourcePermissionBadgeClass(basicForm.permissionScope)
);
const selectedAuth = computed(
    () => authItems.value.find(item => item.id === selectedAuthId.value) || null
);
const authOptions = computed(() =>
    authItems.value.map(item => ({
        value: item.id,
        label: item.name || '未命名鉴权',
    }))
);

const commonVariableGroups = computed(() => [
    {
        label: '触发者信息',
        items: [
            { key: 'user.id', label: '用户ID' },
            { key: 'user.code', label: '用户名' },
            { key: 'user.name', label: '姓名' },
            { key: 'user.phone', label: '手机号' },
            { key: 'user.email', label: '邮箱' },
            { key: 'user.roleId', label: '角色ID' },
            { key: 'role.id', label: '角色对象ID' },
            { key: 'role.code', label: '角色编码' },
            { key: 'role.name', label: '角色名称' },
        ],
    },
]);

const authVariableGroups = computed(() => commonVariableGroups.value);
const apiVariableGroups = computed(() => {
    const groups = [...commonVariableGroups.value];
    const inputItems = flattenParamItems(apiForm.inputParams, 'input');
    if (inputItems.length) {
        groups.push({
            label: '输入参数',
            items: inputItems,
        });
    }
    const authItem = authItems.value.find(item => item.id === apiForm.connectId);
    const returnInfo = authItem?.authInfo?.returnInfo;
    if (Array.isArray(returnInfo) && returnInfo.length) {
        groups.push({
            label: '鉴权参数',
            items: flattenParamItems(returnInfo, 'auth'),
        });
    }
    return groups;
});

const duplicateApiNameItem = computed(() => {
    const name = apiForm.apiName.trim().toLowerCase();
    if (!name) {
        return null;
    }
    return (
        connectorApis.value.find(
            item =>
                item.id !== editingApiId.value &&
                String(item.apiName || '')
                    .trim()
                    .toLowerCase() === name
        ) || null
    );
});

const duplicateApiCodeItem = computed(() => {
    const code = apiForm.apiCode.trim().toLowerCase();
    if (!code) {
        return null;
    }
    return (
        connectorApis.value.find(
            item =>
                item.id !== editingApiId.value &&
                String(item.apiCode || '')
                    .trim()
                    .toLowerCase() === code
        ) || null
    );
});

const apiFormErrors = computed(() => ({
    apiName: !apiForm.apiName.trim()
        ? '请输入 API 名称'
        : duplicateApiNameItem.value
          ? 'API 名称不能重复'
          : '',
    apiCode: !apiForm.apiCode.trim()
        ? '请输入 API 编码'
        : duplicateApiCodeItem.value
          ? 'API 编码不能重复'
          : '',
    url: !apiForm.url.trim() ? '请输入接口地址' : '',
}));

const outputPickerJsonText = computed(() => {
    if (outputPickerTarget.value === 'auth') {
        return authTestResult.value?.jsonStr || formatJson(authTestResult.value?.rawResponse);
    }
    return apiTestResult.value?.jsonStr || formatJson(apiTestResult.value?.rawResponse);
});

const selectedOutputKeys = computed(() => {
    const rows =
        outputPickerTarget.value === 'auth' ? authForm.authInfo.returnInfo : apiForm.outputParams;
    return collectParamKeys(rows);
});

function createEmptyAuthForm() {
    return {
        id: '',
        name: '',
        remark: '',
        authType: 'OAUTH2_CLIENT_CREDENTIALS',
        state: 1,
        authInfo: {
            method: 'POST',
            url: '',
            headers: [],
            forms: [],
            body: '',
            expireAfterMinutes: 120,
            returnInfo: [],
            returnJsonStr: '',
        },
    };
}

function createEmptyApiForm() {
    return {
        apiCode: '',
        apiName: '',
        apiRemark: '',
        connectId: '',
        connectName: '',
        method: 'GET',
        url: '',
        headers: [],
        forms: [],
        body: '',
        contentType: 'application/json',
        inputParams: [],
        outputParams: [],
        returnInfo: [],
        returnJsonStr: '',
        enabled: true,
    };
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function showMessage(text) {
    message.value = text;
    if (messageTimer) {
        clearTimeout(messageTimer);
    }
    messageTimer = setTimeout(() => {
        message.value = '';
    }, 2200);
}

function createId() {
    if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID().replaceAll('-', '');
    }
    return `${Date.now()}${Math.random().toString(16).slice(2)}`;
}

function clone(value) {
    return JSON.parse(JSON.stringify(value ?? null));
}

function normalizeRows(rows) {
    return Array.isArray(rows)
        ? rows.map(item => ({
              objectId: item?.objectId || createId(),
              name: item?.name || '',
              value: item?.value == null ? '' : String(item.value),
          }))
        : [];
}

function normalizeParams(rows) {
    return Array.isArray(rows)
        ? rows.map(item => ({
              objectId: item?.objectId || createId(),
              parentId: item?.parentId || '',
              name: item?.name || '',
              key: item?.key || '',
              path: item?.path || '',
              paramType: item?.paramType || 'string',
              value: item?.value ?? '',
              desc: item?.desc || '',
              children: normalizeParams(item?.children || []),
          }))
        : [];
}

function normalizeAuthItems(list) {
    return Array.isArray(list)
        ? list.map(item => {
              const authInfo = item?.authInfo || {};
              return {
                  id: item?.id || createId(),
                  name: item?.name || '',
                  remark: item?.remark || '',
                  authType: item?.authType || 'OAUTH2_CLIENT_CREDENTIALS',
                  state: item?.state == null ? 1 : Number(item.state),
                  authInfo: {
                      method: authInfo.method || 'POST',
                      url: authInfo.url || '',
                      headers: normalizeRows(authInfo.headers),
                      forms: normalizeRows(authInfo.forms),
                      body: authInfo.body || '',
                      expireAfterMinutes: authInfo.expireAfterMinutes || 120,
                      returnInfo: normalizeParams(authInfo.returnInfo),
                      returnJsonStr: authInfo.returnJsonStr || '',
                  },
              };
          })
        : [];
}

function assignAuthForm(item) {
    Object.assign(authForm, clone(item || createEmptyAuthForm()));
}

function resetAuthForm() {
    assignAuthForm(createEmptyAuthForm());
    authTestError.value = '';
    authTestResult.value = null;
}

function buildAuthPayload() {
    return {
        id: authForm.id || createId(),
        name: authForm.name.trim(),
        remark: authForm.remark.trim(),
        authType: authForm.authType,
        state: Number(authForm.state) === 0 ? 0 : 1,
        authInfo: {
            method: authForm.authInfo.method,
            url: authForm.authInfo.url.trim(),
            headers: normalizeRows(authForm.authInfo.headers),
            forms: normalizeRows(authForm.authInfo.forms),
            body: authForm.authInfo.body || '',
            expireAfterMinutes: Number(authForm.authInfo.expireAfterMinutes) || 120,
            returnInfo: normalizeParams(authForm.authInfo.returnInfo),
            returnJsonStr: authForm.authInfo.returnJsonStr || '',
        },
    };
}

function assignApiForm(data) {
    Object.assign(apiForm, {
        apiCode: data?.apiCode || '',
        apiName: data?.apiName || '',
        apiRemark: data?.apiRemark || data?.description || '',
        connectId: data?.connectId || '',
        connectName: data?.connectName || '',
        method: data?.method || 'GET',
        url: data?.url || data?.pathTemplate || '',
        headers: normalizeRows(data?.headers),
        forms: normalizeRows(data?.forms),
        body: data?.body || data?.bodyTemplate || '',
        contentType: data?.contentType || 'application/json',
        inputParams: normalizeParams(data?.inputParams),
        outputParams: normalizeParams(data?.outputParams),
        returnInfo: normalizeParams(data?.returnInfo),
        returnJsonStr: data?.returnJsonStr || '',
        enabled: data?.enabled !== false,
    });
}

function resetApiEditor() {
    apiEditorOpen.value = false;
    apiEditorMode.value = 'create';
    editingApiId.value = null;
    assignApiForm(createEmptyApiForm());
    apiSaveError.value = '';
    apiTestError.value = '';
    apiTestResult.value = null;
    apiTestArgumentsText.value = '{}';
    apiCodeEditedManually.value = false;
    lastGeneratedApiCode.value = '';
    apiFieldTouched.apiName = false;
    apiFieldTouched.apiCode = false;
    apiFieldTouched.url = false;
    clearApiCodeTimer();
}

function buildApiPayload() {
    const auth = authItems.value.find(item => item.id === apiForm.connectId);
    return {
        apiCode: apiForm.apiCode.trim(),
        apiName: apiForm.apiName.trim(),
        apiRemark: apiForm.apiRemark.trim(),
        connectId: apiForm.connectId || '',
        connectName: auth?.name || '',
        method: apiForm.method,
        url: apiForm.url.trim(),
        headers: normalizeRows(apiForm.headers),
        forms: normalizeRows(apiForm.forms),
        body: apiForm.body || '',
        contentType: apiForm.contentType,
        inputParams: normalizeParams(apiForm.inputParams),
        outputParams: normalizeParams(apiForm.outputParams),
        returnInfo: normalizeParams(apiForm.outputParams),
        returnJsonStr: apiForm.returnJsonStr || '',
        enabled: Boolean(apiForm.enabled),
    };
}

function flattenParamItems(rows, prefix) {
    const result = [];
    function walk(items) {
        (items || []).forEach(item => {
            const key = item.key || item.path || item.name;
            if (key) {
                result.push({
                    key: `${prefix}.${key}`.replaceAll('..', '.'),
                    label: item.name || item.path || key,
                });
            }
            if (Array.isArray(item.children) && item.children.length) {
                walk(item.children);
            }
        });
    }
    walk(rows);
    return result;
}

function collectParamKeys(rows) {
    const result = [];
    function walk(items) {
        (items || []).forEach(item => {
            if (item.key) {
                result.push(item.key);
            }
            if (Array.isArray(item.children)) {
                walk(item.children);
            }
        });
    }
    walk(rows);
    return result;
}

function parseJsonObject(text) {
    if (!text || !text.trim()) {
        return {};
    }
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
}

function formatJson(value) {
    if (value == null || value === '') {
        return '';
    }
    if (typeof value === 'string') {
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch (error) {
            return value;
        }
    }
    try {
        return JSON.stringify(value, null, 2);
    } catch (error) {
        return String(value);
    }
}

function inputClass(invalid = false, disabled = false) {
    return buildIntegrationInputClass(invalid, disabled);
}

function clearApiCodeTimer() {
    if (apiCodeGenerateTimer) {
        clearTimeout(apiCodeGenerateTimer);
        apiCodeGenerateTimer = null;
    }
}

function extractApiCode(result) {
    if (result && typeof result === 'object') {
        return String(result.apiCode || '').trim();
    }
    return String(result || '').trim();
}

async function generateApiCodePreview(apiName) {
    if (!connectorIdValue.value) {
        return;
    }
    const currentToken = ++apiCodeGenerateToken;
    apiCodeGenerating.value = true;
    try {
        const result = await previewIntegrationConnectorApiCode(
            connectorIdValue.value,
            apiName,
            handleUnauthorized
        );
        if (currentToken !== apiCodeGenerateToken) {
            return;
        }
        const nextCode = extractApiCode(result);
        if (!nextCode) {
            return;
        }
        const previousGeneratedCode = lastGeneratedApiCode.value;
        lastGeneratedApiCode.value = nextCode;
        if (
            !apiCodeEditedManually.value ||
            !apiForm.apiCode ||
            apiForm.apiCode === previousGeneratedCode
        ) {
            apiForm.apiCode = nextCode;
        }
    } catch (error) {
        apiSaveError.value = error?.message || 'API 编码生成失败';
    } finally {
        if (currentToken === apiCodeGenerateToken) {
            apiCodeGenerating.value = false;
        }
    }
}

function scheduleApiCodePreview() {
    clearApiCodeTimer();
    const apiName = apiForm.apiName.trim();
    if (!apiName) {
        if (!apiCodeEditedManually.value || apiForm.apiCode === lastGeneratedApiCode.value) {
            apiForm.apiCode = '';
            lastGeneratedApiCode.value = '';
        }
        apiCodeGenerating.value = false;
        return;
    }
    apiCodeGenerateTimer = setTimeout(() => {
        generateApiCodePreview(apiName);
    }, 250);
}

function handleApiNameInput(value) {
    apiForm.apiName = value;
    apiFieldTouched.apiName = true;
    if (apiEditorMode.value === 'create') {
        const currentCode = apiForm.apiCode.trim();
        if (
            !apiCodeEditedManually.value ||
            !currentCode ||
            currentCode === lastGeneratedApiCode.value
        ) {
            scheduleApiCodePreview();
        }
    }
}

function handleApiCodeInput(value) {
    apiForm.apiCode = value;
    apiFieldTouched.apiCode = true;
    apiCodeEditedManually.value =
        Boolean(value.trim()) && value.trim() !== lastGeneratedApiCode.value;
}

async function loadConnector() {
    if (!connectorIdValue.value) {
        loadError.value = '连接器不存在';
        return;
    }
    loading.value = true;
    loadError.value = '';
    try {
        const detail = await getIntegrationConnector(connectorIdValue.value, handleUnauthorized);
        connector.value = detail;
        Object.assign(basicForm, {
            name: detail.name || '',
            alias: detail.alias || '',
            baseUrl: detail.baseUrl || '',
            permissionScope: normalizeResourcePermissionScope(detail.permissionScope),
            status: detail.status || 'ACTIVE',
        });
        authItems.value = normalizeAuthItems(detail.authList);
        if (!selectedAuthId.value && authItems.value.length) {
            selectedAuthId.value = authItems.value[0].id;
        }
        const selected = authItems.value.find(item => item.id === selectedAuthId.value);
        if (selected) {
            assignAuthForm(selected);
        } else {
            resetAuthForm();
        }
    } catch (error) {
        loadError.value = error?.message || '连接器详情加载失败';
    } finally {
        loading.value = false;
    }
}

async function loadApis() {
    if (!connectorIdValue.value) {
        return;
    }
    apiListLoading.value = true;
    apiListError.value = '';
    try {
        connectorApis.value = await listIntegrationConnectorApis(
            connectorIdValue.value,
            handleUnauthorized
        );
    } catch (error) {
        apiListError.value = error?.message || 'API 列表加载失败';
    } finally {
        apiListLoading.value = false;
    }
}

async function saveBasicInfo() {
    basicSaveError.value = '';
    if (!basicForm.name.trim()) {
        basicSaveError.value = '请输入连接器名称';
        return;
    }
    if (!basicForm.alias.trim()) {
        basicSaveError.value = '连接器编码不能为空';
        return;
    }
    basicSaving.value = true;
    try {
        await updateIntegrationConnector(
            connectorIdValue.value,
            {
                name: basicForm.name.trim(),
                alias: basicForm.alias.trim(),
                baseUrl: basicForm.baseUrl.trim(),
                permissionScope: basicForm.permissionScope,
                status: basicForm.status,
                authList: authItems.value,
            },
            handleUnauthorized
        );
        showMessage('保存成功');
        await loadConnector();
        await loadApis();
    } catch (error) {
        basicSaveError.value = error?.message || '保存基础信息失败';
    } finally {
        basicSaving.value = false;
    }
}

async function persistAuthItems(successText = '保存成功') {
    authSaving.value = true;
    authSaveError.value = '';
    try {
        const saved = await updateIntegrationConnector(
            connectorIdValue.value,
            {
                name: basicForm.name.trim(),
                alias: basicForm.alias.trim(),
                baseUrl: basicForm.baseUrl.trim(),
                permissionScope: basicForm.permissionScope,
                status: basicForm.status,
                authList: authItems.value,
            },
            handleUnauthorized
        );
        connector.value = saved;
        authItems.value = normalizeAuthItems(saved.authList);
        showMessage(successText);
    } catch (error) {
        authSaveError.value = error?.message || '保存鉴权失败';
        throw error;
    } finally {
        authSaving.value = false;
    }
}

async function saveAuth() {
    authSaveError.value = '';
    if (!authForm.name.trim()) {
        authSaveError.value = '请输入鉴权名称';
        return;
    }
    if (!authForm.authInfo.url.trim()) {
        authSaveError.value = '请输入接口地址';
        return;
    }
    const next = buildAuthPayload();
    const index = authItems.value.findIndex(item => item.id === next.id);
    if (index >= 0) {
        authItems.value.splice(index, 1, next);
    } else {
        authItems.value.push(next);
    }
    selectedAuthId.value = next.id;
    assignAuthForm(next);
    try {
        await persistAuthItems('保存成功');
    } catch (error) {
        await loadConnector();
    }
}

function createAuth() {
    selectedAuthId.value = '';
    resetAuthForm();
    authForm.id = createId();
}

function selectAuth(id) {
    selectedAuthId.value = id;
    const selected = authItems.value.find(item => item.id === id);
    if (selected) {
        assignAuthForm(selected);
    }
}

async function deleteAuth() {
    if (!authForm.id) {
        resetAuthForm();
        return;
    }
    const ok = await confirm({
        title: '删除鉴权',
        message: `确认删除鉴权“${authForm.name || '未命名鉴权'}”吗？`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!ok) {
        return;
    }
    authItems.value = authItems.value.filter(item => item.id !== authForm.id);
    selectedAuthId.value = authItems.value[0]?.id || '';
    assignAuthForm(authItems.value[0] || createEmptyAuthForm());
    try {
        await persistAuthItems('删除成功');
    } catch (error) {
        await loadConnector();
    }
}

async function testAuth() {
    authTesting.value = true;
    authTestError.value = '';
    authTestResult.value = null;
    try {
        const variables = parseJsonObject(authTestVariablesText.value);
        authTestResult.value = await testIntegrationConnectorAuth(
            connectorIdValue.value,
            {
                authId: authForm.id,
                authItem: buildAuthPayload(),
                variables,
            },
            handleUnauthorized
        );
        if (authTestResult.value?.jsonStr) {
            authForm.authInfo.returnJsonStr = authTestResult.value.jsonStr;
        }
        showMessage('调试完成');
    } catch (error) {
        authTestError.value = error?.message || '鉴权调试失败';
    } finally {
        authTesting.value = false;
    }
}

function openCreateApi() {
    resetApiEditor();
    apiEditorOpen.value = true;
    apiEditorMode.value = 'create';
}

async function openEditApi(item) {
    resetApiEditor();
    apiEditorOpen.value = true;
    apiEditorMode.value = 'edit';
    editingApiId.value = item.id;
    apiSaving.value = true;
    try {
        const detail = await getIntegrationConnectorApi(item.id, handleUnauthorized);
        assignApiForm(detail);
        lastGeneratedApiCode.value = detail.apiCode || '';
    } catch (error) {
        apiSaveError.value = error?.message || 'API 详情加载失败';
    } finally {
        apiSaving.value = false;
    }
}

function validateApiForm() {
    apiFieldTouched.apiName = true;
    apiFieldTouched.apiCode = true;
    apiFieldTouched.url = true;
    return !apiFormErrors.value.apiName && !apiFormErrors.value.apiCode && !apiFormErrors.value.url;
}

async function saveApi() {
    apiSaveError.value = '';
    if (!validateApiForm()) {
        return;
    }
    apiSaving.value = true;
    try {
        if (editingApiId.value) {
            await updateIntegrationConnectorApi(
                editingApiId.value,
                buildApiPayload(),
                handleUnauthorized
            );
            showMessage('保存成功');
        } else {
            await createIntegrationConnectorApi(
                connectorIdValue.value,
                buildApiPayload(),
                handleUnauthorized
            );
            showMessage('创建成功');
        }
        resetApiEditor();
        await loadApis();
    } catch (error) {
        apiSaveError.value = error?.message || '保存 API 失败';
    } finally {
        apiSaving.value = false;
    }
}

async function deleteApi(item) {
    const ok = await confirm({
        title: '删除 API',
        message: `确认删除 API“${item.apiName || item.apiCode}”吗？删除后不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!ok) {
        return;
    }
    try {
        await deleteIntegrationConnectorApi(item.id, handleUnauthorized);
        showMessage('删除成功');
        await loadApis();
    } catch (error) {
        apiListError.value = error?.message || '删除 API 失败';
    }
}

async function publishApi(item) {
    apiPublishLoadingId.value = item.id;
    try {
        await publishIntegrationConnectorApi(item.id, handleUnauthorized);
        showMessage('发布成功');
        await loadApis();
    } catch (error) {
        apiListError.value = error?.message || '发布 API 失败';
    } finally {
        apiPublishLoadingId.value = null;
    }
}

async function disableApi(item) {
    apiPublishLoadingId.value = item.id;
    try {
        await disableIntegrationConnectorApi(item.id, handleUnauthorized);
        showMessage('已取消发布');
        await loadApis();
    } catch (error) {
        apiListError.value = error?.message || '取消发布失败';
    } finally {
        apiPublishLoadingId.value = null;
    }
}

async function testApi() {
    apiTestError.value = '';
    apiTestResult.value = null;
    if (!editingApiId.value) {
        apiTestError.value = '请先保存 API 后再调试';
        return;
    }
    apiTesting.value = true;
    try {
        const argumentsValue = parseJsonObject(apiTestArgumentsText.value);
        apiTestResult.value = await testIntegrationConnectorApi(
            editingApiId.value,
            { arguments: argumentsValue },
            handleUnauthorized
        );
        if (apiTestResult.value?.jsonStr) {
            apiForm.returnJsonStr = apiTestResult.value.jsonStr;
        }
        showMessage('调试完成');
    } catch (error) {
        apiTestError.value = error?.message || 'API 调试失败';
    } finally {
        apiTesting.value = false;
    }
}

function openOutputPicker(target) {
    outputPickerTarget.value = target;
    outputPickerOpen.value = true;
}

function appendOutputParam(row) {
    if (outputPickerTarget.value === 'auth') {
        authForm.authInfo.returnInfo = [...normalizeParams(authForm.authInfo.returnInfo), row];
    } else {
        apiForm.outputParams = [...normalizeParams(apiForm.outputParams), row];
    }
}

watch(selectedAuth, item => {
    if (item) {
        assignAuthForm(item);
    }
});

watch(activeTab, tab => {
    if (tab === 'api') {
        loadApis();
    }
});

onMounted(async () => {
    await loadConnector();
    await loadApis();
});

onBeforeUnmount(() => {
    clearApiCodeTimer();
    if (messageTimer) {
        clearTimeout(messageTimer);
    }
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col gap-5 overflow-y-auto p-6">
        <div
            v-if="message"
            class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700"
        >
            {{ message }}
        </div>

        <header class="rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
            <div class="flex min-w-0 items-center gap-3">
                <button
                    class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 hover:text-primary"
                    type="button"
                    @click="router.push(ROUTE_PATHS.adminIntegrationConnectors)"
                >
                    <span class="material-symbols-outlined text-[20px]">arrow_back</span>
                </button>
                <div class="min-w-0 flex-1">
                    <div class="flex min-w-0 flex-wrap items-center gap-2">
                        <h1 class="truncate text-xl font-bold text-slate-900">
                            {{ pageTitle }}
                        </h1>
                        <span
                            v-if="connector"
                            class="rounded-full px-2.5 py-0.5 text-xs font-semibold"
                            :class="getIntegrationStatusMeta(connector.status).badgeClass"
                        >
                            {{ getIntegrationStatusMeta(connector.status).label }}
                        </span>
                        <span
                            class="rounded-full px-2.5 py-0.5 text-xs font-semibold"
                            :class="currentPermissionClass"
                        >
                            {{ currentPermissionLabel }}
                        </span>
                    </div>
                    <p class="mt-1 truncate text-sm text-slate-500">{{ pageSubtitle }}</p>
                </div>
            </div>

            <div
                class="mt-3 flex flex-wrap gap-1.5 rounded-xl border border-slate-200 bg-slate-50 p-1"
            >
                <button
                    v-for="tab in mainTabs"
                    :key="tab.value"
                    :class="[
                        'rounded-lg px-4 py-2 text-sm font-semibold transition',
                        activeTab === tab.value
                            ? 'bg-white text-primary shadow-sm ring-1 ring-slate-200'
                            : 'text-slate-500 hover:bg-white/80 hover:text-slate-800',
                    ]"
                    type="button"
                    @click="activeTab = tab.value"
                >
                    {{ tab.label }}
                </button>
            </div>
        </header>

        <div v-if="loadError" class="rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600">
            {{ loadError }}
        </div>
        <div
            v-if="loading"
            class="rounded-3xl border border-slate-200 bg-white px-6 py-16 text-center text-sm text-slate-400"
        >
            正在加载连接器详情...
        </div>

        <section
            v-else-if="activeTab === 'base'"
            class="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"
        >
            <div class="mb-5">
                <h2 class="text-lg font-bold text-slate-900">基础信息</h2>
                <p class="mt-1 text-sm text-slate-500">连接器编码创建后不可修改。</p>
            </div>

            <div
                v-if="basicSaveError"
                class="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ basicSaveError }}
            </div>

            <div class="grid gap-4 lg:grid-cols-2">
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">
                        <span class="text-rose-500">*</span>
                        连接器名称
                    </span>
                    <input
                        v-model="basicForm.name"
                        :disabled="!canOperateConnector"
                        :class="inputClass(false, !canOperateConnector)"
                        type="text"
                        placeholder="请输入连接器名称"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">
                        <span class="text-rose-500">*</span>
                        连接器编码
                    </span>
                    <input
                        v-model="basicForm.alias"
                        disabled
                        :class="inputClass(false, true)"
                        type="text"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600"
                        >接口基础地址</span
                    >
                    <input
                        v-model="basicForm.baseUrl"
                        :disabled="!canOperateConnector"
                        :class="inputClass(false, !canOperateConnector)"
                        type="text"
                        placeholder="例如：https://api.example.com，可为空"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">状态</span>
                    <AppSelect
                        :model-value="basicForm.status"
                        :options="connectorStatusOptions"
                        :disabled="!canOperateConnector"
                        :button-class="formSelectButtonClass"
                        menu-class="w-full"
                        @update:modelValue="value => (basicForm.status = value)"
                    />
                </label>
                <label class="block lg:col-span-2">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">权限范围</span>
                    <AppSelect
                        :model-value="basicForm.permissionScope"
                        :options="permissionOptions"
                        :disabled="!canOperateConnector || connector?.canChangePermission === false"
                        :button-class="formSelectButtonClass"
                        menu-class="w-full"
                        @update:modelValue="value => (basicForm.permissionScope = value)"
                    />
                    <p class="mt-2 text-sm text-slate-500">{{ currentPermissionDescription }}</p>
                </label>
            </div>

            <div class="mt-6 flex justify-end">
                <button
                    :disabled="basicSaving || !canOperateConnector"
                    class="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                    type="button"
                    @click="saveBasicInfo"
                >
                    {{ basicSaving ? '保存中...' : '保存' }}
                </button>
            </div>
        </section>

        <AdminIntegrationConnectorAuthSection
            v-else-if="activeTab === 'auth'"
            :auth-items="authItems"
            :selected-auth-id="selectedAuthId"
            :auth-form="authForm"
            :auth-variable-groups="authVariableGroups"
            :auth-save-error="authSaveError"
            :auth-saving="authSaving"
            :auth-testing="authTesting"
            :auth-test-error="authTestError"
            :auth-test-result="authTestResult"
            :auth-test-variables-text="authTestVariablesText"
            :active-request-tab="activeAuthRequestTab"
            :format-json="formatJson"
            @create="createAuth"
            @select="selectAuth"
            @delete="deleteAuth"
            @save="saveAuth"
            @test="testAuth"
            @pick-output="openOutputPicker('auth')"
            @update:authTestVariablesText="value => (authTestVariablesText = value)"
            @update:activeRequestTab="value => (activeAuthRequestTab = value)"
        />

        <AdminIntegrationConnectorApiSection
            v-else
            :api-list-error="apiListError"
            :api-list-loading="apiListLoading"
            :connector-apis="connectorApis"
            :api-editor-open="apiEditorOpen"
            :api-editor-mode="apiEditorMode"
            :api-save-error="apiSaveError"
            :api-saving="apiSaving"
            :api-testing="apiTesting"
            :api-test-error="apiTestError"
            :api-test-result="apiTestResult"
            :api-test-arguments-text="apiTestArgumentsText"
            :api-publish-loading-id="apiPublishLoadingId"
            :api-code-generating="apiCodeGenerating"
            :api-form="apiForm"
            :api-field-touched="apiFieldTouched"
            :api-form-errors="apiFormErrors"
            :api-variable-groups="apiVariableGroups"
            :auth-options="authOptions"
            :active-request-tab="activeApiRequestTab"
            :format-json="formatJson"
            @open-create="openCreateApi"
            @open-edit="openEditApi"
            @publish="publishApi"
            @disable="disableApi"
            @delete="deleteApi"
            @reset-editor="resetApiEditor"
            @save="saveApi"
            @test="testApi"
            @pick-output="openOutputPicker('api')"
            @update:apiTestArgumentsText="value => (apiTestArgumentsText = value)"
            @update:activeRequestTab="value => (activeApiRequestTab = value)"
            @api-name-input="handleApiNameInput"
            @api-code-input="handleApiCodeInput"
        />

        <IntegrationJsonResultPicker
            :open="outputPickerOpen"
            :json-text="outputPickerJsonText"
            :selected-keys="selectedOutputKeys"
            @close="outputPickerOpen = false"
            @select="appendOutputParam"
        />
    </section>
</template>
