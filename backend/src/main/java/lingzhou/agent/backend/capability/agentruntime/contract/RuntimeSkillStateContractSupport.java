package lingzhou.agent.backend.capability.agentruntime.contract;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeSkillStateContractSupport {

    public static final String EXTENSION_KEY = "runtimeSkillState";
    private static final String LEGACY_SKILL_READ_FACTS_KEY = "skillReadFacts";
    private static final String LEGACY_LOADED_SKILLS_KEY = "loadedSkills";
    private static final String LEGACY_CURRENT_RUNTIME_SKILL_KEY = "currentRuntimeSkillName";
    private static final String LEGACY_SELECTED_SKILL_HINT_ID_KEY = "selectedSkillHintId";
    private static final String LEGACY_SELECTED_SKILL_HINT_NAME_KEY = "selectedSkillHintRuntimeSkillName";
    private static final String LEGACY_MENTIONED_SKILL_ID_KEY = "mentionedSkillId";

    private final ObjectMapper objectMapper;

    public RuntimeSkillStateContractSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuntimeSkillStateContract readContractFromParams(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return emptyContract();
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return emptyContract();
            }
            RuntimeSkillStateContract extensionContract = readContract(payload.get(EXTENSION_KEY));
            if (extensionContract != null && hasAnyState(extensionContract)) {
                return extensionContract;
            }
            List<String> loadedSkillNames = readLegacyLoadedSkillNames(payload.get(LEGACY_LOADED_SKILLS_KEY));
            String currentRuntimeSkillName = normalizeText(payload.get(LEGACY_CURRENT_RUNTIME_SKILL_KEY));
            Long selectedSkillHintId = toLong(payload.get(LEGACY_SELECTED_SKILL_HINT_ID_KEY));
            String selectedSkillHintRuntimeSkillName = normalizeText(payload.get(LEGACY_SELECTED_SKILL_HINT_NAME_KEY));
            Long mentionedSkillId = toLong(payload.get(LEGACY_MENTIONED_SKILL_ID_KEY));
            List<RuntimeSkillReadFactContract> legacyFacts = readLegacySkillReadFacts(payload.get(LEGACY_SKILL_READ_FACTS_KEY));
            if (!loadedSkillNames.isEmpty()
                    || StringUtils.hasText(currentRuntimeSkillName)
                    || selectedSkillHintId != null
                    || StringUtils.hasText(selectedSkillHintRuntimeSkillName)
                    || mentionedSkillId != null
                    || !legacyFacts.isEmpty()) {
                return normalize(new RuntimeSkillStateContract(
                        loadedSkillNames,
                        currentRuntimeSkillName,
                        selectedSkillHintId,
                        selectedSkillHintRuntimeSkillName,
                        mentionedSkillId,
                        legacyFacts));
            }
            return extensionContract == null ? emptyContract() : extensionContract;
        } catch (Exception ignored) {
            return emptyContract();
        }
    }

    public RuntimeSkillStateContract readContract(Object payload) {
        if (payload == null) {
            return emptyContract();
        }
        try {
            RuntimeSkillStateContract contract;
            if (payload instanceof RuntimeSkillStateContract runtimeSkillStateContract) {
                contract = runtimeSkillStateContract;
            } else if (payload instanceof Map<?, ?> map) {
                contract = objectMapper.convertValue(map, RuntimeSkillStateContract.class);
            } else if (payload instanceof String text && StringUtils.hasText(text)) {
                contract = objectMapper.readValue(text, RuntimeSkillStateContract.class);
            } else {
                contract = objectMapper.convertValue(payload, RuntimeSkillStateContract.class);
            }
            return normalize(contract);
        } catch (Exception ignored) {
            return emptyContract();
        }
    }

    public List<RuntimeSkillReadFactContract> readSkillReadFactsFromParams(String paramsJson) {
        return readContractFromParams(paramsJson).skillReadFacts();
    }

    public String mergeContractIntoParams(String paramsJson, RuntimeSkillStateContract contract) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(paramsJson)) {
            try {
                Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
                if (parsed != null) {
                    payload.putAll(parsed);
                }
            } catch (Exception ignored) {
                // rebuild payload with normalized contract only
            }
        }
        RuntimeSkillStateContract normalized = normalize(contract);
        if (!hasAnyState(normalized)) {
            payload.remove(EXTENSION_KEY);
        } else {
            payload.put(EXTENSION_KEY, toPayload(normalized));
        }
        payload.remove(LEGACY_SKILL_READ_FACTS_KEY);
        payload.remove(LEGACY_LOADED_SKILLS_KEY);
        payload.remove(LEGACY_CURRENT_RUNTIME_SKILL_KEY);
        payload.remove(LEGACY_SELECTED_SKILL_HINT_ID_KEY);
        payload.remove(LEGACY_SELECTED_SKILL_HINT_NAME_KEY);
        payload.remove(LEGACY_MENTIONED_SKILL_ID_KEY);
        return JSON.toJSONString(payload);
    }

    public Map<String, Object> toPayload(RuntimeSkillStateContract contract) {
        RuntimeSkillStateContract normalized = normalize(contract);
        if (!hasAnyState(normalized)) {
            return Map.of();
        }
        return objectMapper.convertValue(normalized, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    public RuntimeSkillStateContract normalize(RuntimeSkillStateContract contract) {
        if (contract == null) {
            return emptyContract();
        }
        List<String> loadedSkillNames = normalizeLoadedSkillNames(contract.loadedSkillNames());
        List<RuntimeSkillReadFactContract> facts = new ArrayList<>();
        for (RuntimeSkillReadFactContract fact : contract.skillReadFacts() == null ? List.<RuntimeSkillReadFactContract>of() : contract.skillReadFacts()) {
            if (fact == null || !StringUtils.hasText(fact.skillName()) || !StringUtils.hasText(fact.message())) {
                continue;
            }
            facts.add(new RuntimeSkillReadFactContract(
                    fact.skillName(), fact.displayName(), fact.message(), fact.toolCallId()));
        }
        return new RuntimeSkillStateContract(
                loadedSkillNames,
                normalizeText(contract.currentRuntimeSkillName()),
                contract.selectedSkillHintId(),
                normalizeText(contract.selectedSkillHintRuntimeSkillName()),
                contract.mentionedSkillId(),
                facts);
    }

    public RuntimeSkillStateContract withRoutingState(
            RuntimeSkillStateContract existing,
            List<String> loadedSkillNames,
            String currentRuntimeSkillName,
            Long selectedSkillHintId,
            String selectedSkillHintRuntimeSkillName,
            Long mentionedSkillId) {
        RuntimeSkillStateContract base = normalize(existing);
        return normalize(new RuntimeSkillStateContract(
                loadedSkillNames,
                currentRuntimeSkillName,
                selectedSkillHintId,
                selectedSkillHintRuntimeSkillName,
                mentionedSkillId,
                base.skillReadFacts()));
    }

    public String readCurrentRuntimeSkillName(String paramsJson) {
        return readContractFromParams(paramsJson).currentRuntimeSkillName();
    }

    public List<String> readLoadedSkillNames(String paramsJson) {
        return readContractFromParams(paramsJson).loadedSkillNames();
    }

    public String readSelectedSkillHintRuntimeSkillName(String paramsJson) {
        return readContractFromParams(paramsJson).selectedSkillHintRuntimeSkillName();
    }

    public Long readSelectedSkillHintId(String paramsJson) {
        return readContractFromParams(paramsJson).selectedSkillHintId();
    }

    public Long readMentionedSkillId(String paramsJson) {
        return readContractFromParams(paramsJson).mentionedSkillId();
    }

    private List<RuntimeSkillReadFactContract> readLegacySkillReadFacts(Object payload) {
        if (!(payload instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        List<RuntimeSkillReadFactContract> facts = new ArrayList<>();
        for (Object item : items) {
            try {
                RuntimeSkillReadFactContract fact = objectMapper.convertValue(item, RuntimeSkillReadFactContract.class);
                if (fact != null && StringUtils.hasText(fact.skillName()) && StringUtils.hasText(fact.message())) {
                    facts.add(fact);
                }
            } catch (Exception ignored) {
                // ignore malformed legacy payload item
            }
        }
        return List.copyOf(facts);
    }

    private List<String> readLegacyLoadedSkillNames(Object payload) {
        if (!(payload instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        List<String> skillNames = new ArrayList<>();
        for (Object item : items) {
            String skillName = normalizeText(item);
            if (StringUtils.hasText(skillName) && !skillNames.contains(skillName)) {
                skillNames.add(skillName);
            }
        }
        return List.copyOf(skillNames);
    }

    private List<String> normalizeLoadedSkillNames(List<String> loadedSkillNames) {
        if (loadedSkillNames == null || loadedSkillNames.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String skillName : loadedSkillNames) {
            String value = normalizeText(skillName);
            if (StringUtils.hasText(value) && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private boolean hasAnyState(RuntimeSkillStateContract contract) {
        return contract != null
                && (!contract.loadedSkillNames().isEmpty()
                        || StringUtils.hasText(contract.currentRuntimeSkillName())
                        || contract.selectedSkillHintId() != null
                        || StringUtils.hasText(contract.selectedSkillHintRuntimeSkillName())
                        || contract.mentionedSkillId() != null
                        || !contract.skillReadFacts().isEmpty());
    }

    private RuntimeSkillStateContract emptyContract() {
        return new RuntimeSkillStateContract(List.of(), "", null, "", null, List.of());
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "";
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
