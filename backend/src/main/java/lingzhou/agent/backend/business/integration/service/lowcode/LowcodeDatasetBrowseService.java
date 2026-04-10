package lingzhou.agent.backend.business.integration.service.lowcode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import lingzhou.agent.backend.business.skill.service.LowcodeApiBrowseService;
import lingzhou.agent.backend.business.skill.service.LowcodePlatformConfigService;
import lingzhou.agent.backend.business.skill.service.LowcodeTokenService;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodeDatasetBrowseService {

    private static final List<String> EXCLUDED_FIELD_KEYWORDS = List.of(
            "logo",
            "icon",
            "image",
            "img",
            "picture",
            "avatar",
            "file",
            "attachment",
            "upload");
    private static final List<String> EXCLUDED_FIELD_TYPE_KEYWORDS = List.of(
            "richtext",
            "rich-text",
            "markdown",
            "html",
            "editor",
            "imageupload",
            "pictureupload",
            "fileupload",
            "attachmentupload",
            "uploader",
            "media",
            "富文本",
            "图片",
            "图像",
            "头像",
            "附件",
            "文件上传",
            "图片上传",
            "附件上传",
            "上传");

    private final LowcodePlatformConfigService lowcodePlatformConfigService;
    private final LowcodeTokenService lowcodeTokenService;
    private final LowcodePlatformClient lowcodePlatformClient;

    public LowcodeDatasetBrowseService(
            LowcodePlatformConfigService lowcodePlatformConfigService,
            LowcodeTokenService lowcodeTokenService,
            LowcodePlatformClient lowcodePlatformClient) {
        this.lowcodePlatformConfigService = lowcodePlatformConfigService;
        this.lowcodeTokenService = lowcodeTokenService;
        this.lowcodePlatformClient = lowcodePlatformClient;
    }

    public List<LowcodeApiBrowseService.PlatformOption> listPlatforms() {
        return lowcodePlatformConfigService.listEnabledPlatforms().stream()
                .map(platform -> new LowcodeApiBrowseService.PlatformOption(
                        trimText(platform.getKey()), trimText(platform.getName()), trimText(platform.getApiUrl())))
                .toList();
    }

    public List<LowcodeApiBrowseService.AppView> listApps(String platformKey) throws TaskException {
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        return lowcodePlatformClient.getAccessibleApps(platform, token).stream()
                .map(item -> new LowcodeApiBrowseService.AppView(
                        trimText(item.get("AppCode")),
                        firstNonBlank(item.get("AppName"), item.get("AppCode")),
                        trimText(item.get("AppCode")),
                        trimText(item.get("Icon")),
                        "",
                        null))
                .sorted(Comparator.comparing(LowcodeApiBrowseService.AppView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<ObjectView> listObjects(String platformKey, String appId) throws TaskException {
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        return flattenMenus(platform, token, loadMenus(platform, token, appId), "", 0).stream()
                .sorted(Comparator.comparing(ObjectView::objectName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<FieldView> listFields(String platformKey, String appId, String objectCode, String formCode) throws TaskException {
        String normalizedObjectCode = requireText(objectCode, "objectCode 不能为空");
        String normalizedFormCode = requireText(firstNonBlank(formCode, objectCode), "formCode 不能为空");
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        Map<String, Object> target = lowcodePlatformClient.getDataSourceNew(platform, token, normalizedFormCode);
        List<FieldView> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectFieldGroup(target.get("UserFields"), normalizedObjectCode, normalizedFormCode, "", "MAIN_USER", fields, seen);
        collectFieldGroup(target.get("SystemFields"), normalizedObjectCode, normalizedFormCode, "", "MAIN_SYSTEM", fields, seen);
        Object rawSubFields = target.get("SubFields");
        if (rawSubFields instanceof List<?> subFieldList) {
            for (Object subFieldItem : subFieldList) {
                if (!(subFieldItem instanceof Map<?, ?> subFieldMap)) {
                    continue;
                }
                Map<String, Object> subMap = normalizeMap(subFieldMap);
                Map<String, Object> item = asMap(subMap.get("Item"));
                String subObjectCode = firstNonBlank(
                        item.get("ItemName"),
                        item.get("SchemaCode"),
                        item.get("RelateName"),
                        item.get("Code"),
                        item.get("ChildSchemaCode"));
                String subObjectName = firstNonBlank(item.get("FunctionName"), item.get("DisplayName"), subObjectCode);
                collectFieldGroup(item.get("UserFields"), normalizedObjectCode, normalizedFormCode, subObjectCode, "SUB_USER", fields, seen);
                collectFieldGroup(item.get("SystemFields"), normalizedObjectCode, normalizedFormCode, subObjectCode, "SUB_SYSTEM", fields, seen);
                collectFieldGroup(subMap, normalizedObjectCode, normalizedFormCode, subObjectCode, "SUB_USER", fields, seen);
                List<Map<String, Object>> childFields = asList(item.get("children"));
                int childSort = 0;
                for (Map<String, Object> childField : childFields) {
                    if (!shouldExposeField(childField, "SUB_CHILD")) {
                        continue;
                    }
                    addField(
                            fields,
                            seen,
                            new FieldView(
                                    normalizedObjectCode,
                                    normalizedFormCode,
                                    trimText(childField.get("ItemName")),
                                    firstNonBlank(childField.get("DisplayName"), childField.get("ItemName")),
                                    firstNonBlank(childField.get("LogicTypeName"), childField.get("LogicType")),
                                    "SUB_CHILD",
                                    subObjectCode,
                                    subObjectName,
                                    childSort++,
                                    childField));
                }
            }
        }
        return fields.stream()
                .sorted(Comparator.comparing(FieldView::subObjectCode, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparingInt(field -> fieldScopeOrder(field.fieldScope()))
                        .thenComparingInt(FieldView::sortOrder))
                .toList();
    }

    public List<RelationView> listRelations(String platformKey, String appId, List<String> objectCodes) throws TaskException {
        Set<String> selectedCodes = objectCodes == null
                ? Set.of()
                : objectCodes.stream().filter(StringUtils::hasText).map(String::trim).collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<ObjectView> objects = listObjects(platformKey, appId);
        List<RelationView> relations = new ArrayList<>();
        for (ObjectView object : objects) {
            if (object.folder()) {
                continue;
            }
            List<FieldView> fields = listFields(platformKey, appId, object.objectCode(), object.formCode());
            Set<String> subObjectCodes = fields.stream()
                    .map(FieldView::subObjectCode)
                    .filter(StringUtils::hasText)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
            for (String subObjectCode : subObjectCodes) {
                if (!selectedCodes.isEmpty()
                        && (!selectedCodes.contains(object.objectCode()) || !selectedCodes.contains(subObjectCode))) {
                    continue;
                }
                boolean hasParentObjectId = fields.stream()
                        .anyMatch(field -> subObjectCode.equals(field.subObjectCode())
                                && "ParentObjectID".equalsIgnoreCase(field.fieldName()));
                if (hasParentObjectId) {
                    relations.add(new RelationView(
                            object.objectCode(),
                            "ObjectID",
                            subObjectCode,
                            "ParentObjectID",
                            "PARENT_SUBTABLE"));
                }
            }
        }
        return relations.stream().distinct().toList();
    }

    private List<Map<String, Object>> loadMenus(String platformKey, String appId) throws TaskException {
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        return loadMenus(platform, token, appId);
    }

    private List<Map<String, Object>> loadMenus(PlatformEndpointItem platform, String token, String appId) throws TaskException {
        return lowcodePlatformClient.getAppActiveMenus(platform, token, requireText(appId, "appId 不能为空"));
    }

    private List<ObjectView> flattenMenus(
            PlatformEndpointItem platform,
            String token,
            List<Map<String, Object>> items,
            String parentCode,
            int depth)
            throws TaskException {
        List<ObjectView> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        int sort = 0;
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            String objectCode = trimText(item.get("Code"));
            String menuName = firstNonBlank(item.get("Name"), objectCode);
            boolean folder = Boolean.parseBoolean(trimText(item.get("IsFolder")));
            String appCode = trimText(item.get("AppCode"));
            String path = StringUtils.hasText(parentCode) ? parentCode + "/" + menuName : menuName;
            List<ObjectView> childViews = flattenMenus(platform, token, asList(item.get("Children")), objectCode, depth + 1);
            if (folder) {
                if (!childViews.isEmpty()) {
                    result.add(new ObjectView(
                            objectCode,
                            menuName,
                            "FOLDER",
                            "",
                            "",
                            appCode,
                            parentCode,
                            true,
                            depth,
                            sort++,
                            path,
                            item));
                    result.addAll(childViews);
                }
                continue;
            }
            Map<String, Object> itemInfo = lowcodePlatformClient.getAppItemInfo(platform, token, objectCode);
            String objectSource = firstNonBlank(
                    itemInfo.get("ObjectSource"),
                    itemInfo.get("objectSource"),
                    item.get("ObjectSource"),
                    item.get("objectSource"),
                    item.get("AppItemType"),
                    "MENU");
            if (!"GRIDLIST".equalsIgnoreCase(objectSource)) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>(item);
            if (!itemInfo.isEmpty()) {
                raw.putAll(itemInfo);
            }
            String bizTableName = firstNonBlank(itemInfo.get("BizTableName"), itemInfo.get("bizTableName"));
            ObjectView view = new ObjectView(
                    firstNonBlank(bizTableName, menuName, objectCode),
                    menuName,
                    objectSource,
                    firstNonBlank(bizTableName, objectCode),
                    objectCode,
                    appCode,
                    parentCode,
                    false,
                    depth,
                    sort++,
                    path,
                    raw);
            result.add(view);
        }
        return result;
    }

    private void collectFieldGroup(
            Object group,
            String rootObjectCode,
            String formCode,
            String subObjectCode,
            String fieldScope,
            List<FieldView> fields,
            Set<String> seen) {
        Map<String, Object> groupMap = asMap(group);
        List<Map<String, Object>> items = asList(groupMap.get("Result"));
        int sort = 0;
        for (Map<String, Object> item : items) {
            if (!shouldExposeField(item, fieldScope)) {
                continue;
            }
            addField(
                    fields,
                    seen,
                    new FieldView(
                            rootObjectCode,
                            formCode,
                            resolveFieldName(item, fieldScope),
                            firstNonBlank(item.get("DisplayName"), item.get("ItemName")),
                            firstNonBlank(item.get("LogicTypeName"), item.get("LogicType")),
                            fieldScope,
                            subObjectCode,
                            firstNonBlank(trimText(item.get("ParentItemName")), subObjectCode),
                            sort++,
                            item));
        }
    }

    private void addField(List<FieldView> fields, Set<String> seen, FieldView fieldView) {
        if (!StringUtils.hasText(fieldView.fieldName())) {
            return;
        }
        String uniqueKey = (fieldView.fieldScope() + "|" + fieldView.subObjectCode() + "|" + fieldView.fieldName())
                .toLowerCase(Locale.ROOT);
        if (seen.add(uniqueKey)) {
                fields.add(fieldView);
        }
    }

    private String resolveFieldName(Map<String, Object> item, String fieldScope) {
        if (fieldScope != null && fieldScope.toUpperCase(Locale.ROOT).contains("SYSTEM")) {
            return trimText(item.get("ItemName"));
        }
        return trimText(item.get("RelateName"));
    }

    private int fieldScopeOrder(String fieldScope) {
        String normalized = trimText(fieldScope).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MAIN_USER" -> 0;
            case "MAIN_SYSTEM" -> 1;
            case "SUB_USER" -> 2;
            case "SUB_CHILD" -> 3;
            case "SUB_SYSTEM" -> 4;
            default -> 9;
        };
    }

    private boolean shouldExposeField(Map<String, Object> item, String fieldScope) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String fieldName = resolveFieldName(item, fieldScope);
        String fieldLabel = firstNonBlank(item.get("DisplayName"), item.get("ItemName"), item.get("FieldName"));
        String fieldType = firstNonBlank(
                item.get("LogicTypeName"),
                item.get("LogicType"),
                item.get("ControlType"),
                item.get("ControlTypeName"),
                item.get("EditorType"),
                item.get("Type"));
        String nameAndLabel = (fieldName + "|" + fieldLabel).toLowerCase(Locale.ROOT);
        String normalizedFieldType = fieldType.toLowerCase(Locale.ROOT);
        for (String keyword : EXCLUDED_FIELD_KEYWORDS) {
            if (nameAndLabel.contains(keyword) || normalizedFieldType.contains(keyword)) {
                return false;
            }
        }
        for (String keyword : EXCLUDED_FIELD_TYPE_KEYWORDS) {
            if (normalizedFieldType.contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        return normalizeMap(rawMap);
    }

    private List<Map<String, Object>> asList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                items.add(normalizeMap(rawMap));
            }
        }
        return items;
    }

    private Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = trimText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String requireText(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String trimText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    public record ObjectView(
            String objectCode,
            String objectName,
            String objectSource,
            String description,
            String formCode,
            String appCode,
            String parentCode,
            boolean folder,
            int depth,
            int sortOrder,
            String path,
            Map<String, Object> raw) {}

    public record FieldView(
            String objectCode,
            String formCode,
            String fieldName,
            String fieldLabel,
            String fieldType,
            String fieldScope,
            String subObjectCode,
            String subObjectName,
            int sortOrder,
            Object raw) {}

    public record RelationView(
            String leftObjectCode,
            String leftFieldName,
            String rightObjectCode,
            String rightFieldName,
            String relationSource) {}
}
