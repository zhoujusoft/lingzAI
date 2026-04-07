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
        return flattenMenus(loadMenus(platformKey, appId), "", 0).stream()
                .sorted(Comparator.comparing(ObjectView::objectName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<FieldView> listFields(String platformKey, String appId, String objectCode) throws TaskException {
        String normalizedFormCode = requireText(objectCode, "objectCode 不能为空");
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        Map<String, Object> target = lowcodePlatformClient.getDataSourceNew(platform, token, normalizedFormCode);
        List<FieldView> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectFieldGroup(target.get("SystemFields"), normalizedFormCode, "", "MAIN_SYSTEM", fields, seen);
        collectFieldGroup(target.get("UserFields"), normalizedFormCode, "", "MAIN_USER", fields, seen);
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
                collectFieldGroup(item.get("SystemFields"), normalizedFormCode, subObjectCode, "SUB_SYSTEM", fields, seen);
                collectFieldGroup(item.get("UserFields"), normalizedFormCode, subObjectCode, "SUB_USER", fields, seen);
                collectFieldGroup(subMap, normalizedFormCode, subObjectCode, "SUB_USER", fields, seen);
                List<Map<String, Object>> childFields = asList(item.get("children"));
                int childSort = 0;
                for (Map<String, Object> childField : childFields) {
                    addField(
                            fields,
                            seen,
                            new FieldView(
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
                        .thenComparing(FieldView::fieldScope)
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
            List<FieldView> fields = listFields(platformKey, appId, object.objectCode());
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
        return lowcodePlatformClient.getAppActiveMenus(platform, token, requireText(appId, "appId 不能为空"));
    }

    private List<ObjectView> flattenMenus(List<Map<String, Object>> items, String parentCode, int depth) {
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
            String objectName = firstNonBlank(item.get("Name"), objectCode);
            boolean folder = Boolean.parseBoolean(trimText(item.get("IsFolder")));
            String appCode = trimText(item.get("AppCode"));
            String objectType = firstNonBlank(item.get("AppItemType"), folder ? "FOLDER" : "MENU");
            String path = StringUtils.hasText(parentCode) ? parentCode + "/" + objectName : objectName;
            result.add(new ObjectView(
                    objectCode,
                    objectName,
                    objectType,
                    "",
                    appCode,
                    parentCode,
                    folder,
                    depth,
                    sort++,
                    path,
                    item));
            List<Map<String, Object>> children = asList(item.get("Children"));
            if (!children.isEmpty()) {
                result.addAll(flattenMenus(children, objectCode, depth + 1));
            }
        }
        return result;
    }

    private void collectFieldGroup(
            Object group,
            String rootObjectCode,
            String subObjectCode,
            String fieldScope,
            List<FieldView> fields,
            Set<String> seen) {
        Map<String, Object> groupMap = asMap(group);
        List<Map<String, Object>> items = asList(groupMap.get("Result"));
        int sort = 0;
        for (Map<String, Object> item : items) {
            addField(
                    fields,
                    seen,
                    new FieldView(
                            rootObjectCode,
                            trimText(item.get("ItemName")),
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
            String appCode,
            String parentCode,
            boolean folder,
            int depth,
            int sortOrder,
            String path,
            Map<String, Object> raw) {}

    public record FieldView(
            String objectCode,
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
