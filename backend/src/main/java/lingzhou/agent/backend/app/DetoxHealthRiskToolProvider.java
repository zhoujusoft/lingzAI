package lingzhou.agent.backend.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

final class DetoxHealthRiskToolProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_BATCH_SIZE = 20;

    @Tool(description = "生成戒毒人员身体健康风险评估测试数据。可指定身份证号；不指定时随机生成。")
    public String generateDetoxHealthRiskTestData(
            @ToolParam(description = "身份证号码，可选，支持 15 位或 18 位") String idCard,
            @ToolParam(description = "生成条数，默认 1，最大 20") Integer count) {
        try {
            int size = count == null ? 1 : Math.max(1, Math.min(count, MAX_BATCH_SIZE));
            List<Map<String, Object>> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String resolvedIdCard = StringUtils.hasText(idCard) ? idCard.trim() : randomIdCard();
                result.add(generateUserData(resolvedIdCard));
            }
            return toJson(size == 1 ? result.get(0) : result);
        } catch (Exception ex) {
            return errorJson(ex.getMessage());
        }
    }

    @Tool(description = "根据戒毒人员身体健康数据 JSON 计算风险总分、风险等级、各维度明细和评估报告。")
    public String assessDetoxHealthRisk(
            @ToolParam(description = "单个用户健康数据 JSON，建议使用 generateDetoxHealthRiskTestData 的输出") String userDataJson) {
        if (!StringUtils.hasText(userDataJson)) {
            return errorJson("用户健康数据不能为空");
        }
        try {
            Map<String, Object> userData = JSON.readValue(userDataJson, new TypeReference<LinkedHashMap<String, Object>>() {});
            return toJson(buildAssessment(userData));
        } catch (Exception ex) {
            return errorJson("风险评估失败: " + ex.getMessage());
        }
    }

    static Map<String, Object> generateUserData(String idCard) {
        String normalizedIdCard = normalizeIdCard(idCard);
        Map<String, Object> basicInfo = parseIdCard(normalizedIdCard);
        int age = intValue(basicInfo.get("age"));
        long seed = seedFromIdCard(normalizedIdCard);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id_card", normalizedIdCard);
        result.put("basic_info", basicInfo);
        result.put("category1_basic_diseases", generateDiseaseData(age, seed));
        result.put("category2_drug_complications", generateDrugComplication(seed));
        result.put("category3_withdrawal_symptoms", generateWithdrawalSymptom(seed));
        result.put("category4_outpatient_visits", generateOutpatientVisits(seed));
        result.put("category5_abnormal_signals", generateAbnormalSignals(seed));
        result.put("category6_sleep_quality", generateSleepQuality(seed));
        result.put("category7_age", generateAgeScore(age));
        result.put("category8_clinic_visits", generateClinicVisits(seed));
        return result;
    }

    static Map<String, Object> buildAssessment(Map<String, Object> userData) {
        Map<String, Object> basicInfo = asMap(userData.get("basic_info"));
        List<Map<String, Object>> categoryScores = new ArrayList<>();
        categoryScores.add(categoryScore(
                "分类1",
                "基础性疾病与慢性病状况",
                asMap(userData.get("category1_basic_diseases")),
                reasonForCategory1(asMap(userData.get("category1_basic_diseases")))));
        categoryScores.add(categoryScore(
                "分类2",
                "毒品相关躯体并发症",
                asMap(userData.get("category2_drug_complications")),
                reasonForCategory2(asMap(userData.get("category2_drug_complications")))));
        categoryScores.add(categoryScore(
                "分类3",
                "戒断反应状况",
                asMap(userData.get("category3_withdrawal_symptoms")),
                reasonForCategory3(asMap(userData.get("category3_withdrawal_symptoms")))));
        categoryScores.add(categoryScore(
                "分类4",
                "出所就医情况",
                asMap(userData.get("category4_outpatient_visits")),
                reasonForCategory4(asMap(userData.get("category4_outpatient_visits")))));
        categoryScores.add(categoryScore(
                "分类5",
                "异常生理信号",
                asMap(userData.get("category5_abnormal_signals")),
                reasonForCategory5(asMap(userData.get("category5_abnormal_signals")))));
        categoryScores.add(categoryScore(
                "分类6",
                "病理性睡眠质量异常",
                asMap(userData.get("category6_sleep_quality")),
                reasonForCategory6(asMap(userData.get("category6_sleep_quality")))));
        categoryScores.add(categoryScore(
                "分类7",
                "年龄",
                asMap(userData.get("category7_age")),
                reasonForCategory7(asMap(userData.get("category7_age")))));
        categoryScores.add(categoryScore(
                "分类8",
                "所内就医频率",
                asMap(userData.get("category8_clinic_visits")),
                reasonForCategory8(asMap(userData.get("category8_clinic_visits")))));

        int totalScore = categoryScores.stream().mapToInt(item -> intValue(item.get("score"))).sum();
        String riskLevel = totalScore >= 100 ? "高风险" : (totalScore >= 60 ? "中风险" : "低风险");
        List<Map<String, Object>> highlights = categoryScores.stream()
                .sorted(Comparator.comparingInt(item -> -intValue(item.get("score"))))
                .filter(item -> intValue(item.get("score")) > 0)
                .limit(3)
                .toList();

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("id_card", text(userData.get("id_card")));
        assessment.put("basic_info", basicInfo);
        assessment.put("risk_level", riskLevel);
        assessment.put("total_score", totalScore);
        assessment.put("category_scores", categoryScores);
        assessment.put("highlights", highlights);
        assessment.put("suggestions", buildSuggestions(riskLevel, highlights));
        assessment.put("report", buildReport(text(userData.get("id_card")), basicInfo, riskLevel, totalScore, categoryScores, highlights));
        return assessment;
    }

    private static Map<String, Object> parseIdCard(String idCard) {
        String normalized = normalizeIdCard(idCard);
        boolean eighteen = normalized.length() == 18;
        int birthYear = Integer.parseInt(eighteen ? normalized.substring(6, 10) : "19" + normalized.substring(6, 8));
        int birthMonth = Integer.parseInt(eighteen ? normalized.substring(10, 12) : normalized.substring(8, 10));
        int birthDay = Integer.parseInt(eighteen ? normalized.substring(12, 14) : normalized.substring(10, 12));
        int genderCode = Integer.parseInt(eighteen ? normalized.substring(16, 17) : normalized.substring(14, 15));

        LocalDate birthDate = LocalDate.of(birthYear, birthMonth, birthDay);
        LocalDate today = LocalDate.now();
        int age = today.getYear() - birthYear;
        if (today.getMonthValue() < birthMonth || (today.getMonthValue() == birthMonth && today.getDayOfMonth() < birthDay)) {
            age--;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("age", age);
        result.put("gender", genderCode % 2 == 1 ? "男" : "女");
        result.put("birth_date", birthDate.format(DATE_FORMATTER));
        return result;
    }

    private static Map<String, Object> generateDiseaseData(int age, long seed) {
        Random random = random(seed, 0);
        double diseaseProb = Math.min(0.8d, 0.3d + age * 0.008d);
        boolean hasDisease = random.nextDouble() < diseaseProb;
        if (!hasDisease) {
            return mapOf(
                    "has_disease", false,
                    "disease_type", null,
                    "severity", "无",
                    "score_level", 0);
        }

        double severityRoll = random.nextDouble();
        if (severityRoll < 0.1d) {
            return mapOf(
                    "has_disease", true,
                    "disease_type", pick(random, "急性心梗", "脑出血", "急性呼吸衰竭"),
                    "severity", "急性重症",
                    "score_level", 60);
        }
        if (severityRoll < 0.3d) {
            return mapOf(
                    "has_disease", true,
                    "disease_type", pick(random, "冠心病稳定期", "肝硬化代偿期", "慢性心衰"),
                    "severity", "严重慢性病",
                    "score_level", 40);
        }
        if (severityRoll < 0.6d) {
            return mapOf(
                    "has_disease", true,
                    "disease_type", pick(random, "高血压2级", "糖尿病伴并发症", "慢性肾病"),
                    "severity", "中度",
                    "score_level", 20);
        }
        return mapOf(
                "has_disease", true,
                "disease_type", pick(random, "高血压1级", "脂肪肝", "高尿酸血症"),
                "severity", "轻度",
                "score_level", 10);
    }

    private static Map<String, Object> generateDrugComplication(long seed) {
        Random random = random(seed, 1);
        double roll = random.nextDouble();
        if (roll < 0.15d) {
            return mapOf(
                    "has_complication", true,
                    "complication_type", "注射相关",
                    "specific_complication", pick(random, "静脉炎", "感染性心内膜炎", "注射部位感染"),
                    "score_level", 20);
        }
        if (roll < 0.3d) {
            return mapOf(
                    "has_complication", true,
                    "complication_type", "脏器损害",
                    "specific_complication", pick(random, "甲基苯丙胺脑损伤", "可卡因心肌病", "肝功能损害"),
                    "score_level", 15);
        }
        if (roll < 0.45d) {
            return mapOf(
                    "has_complication", true,
                    "complication_type", "过量史",
                    "specific_complication", "毒品过量/中毒史（曾昏迷抢救）",
                    "score_level", 15);
        }
        return mapOf(
                "has_complication", false,
                "complication_type", null,
                "specific_complication", null,
                "score_level", 0);
    }

    private static Map<String, Object> generateWithdrawalSymptom(long seed) {
        Random random = random(seed, 2);
        int cowsScore = random.nextInt(31);
        String severity;
        int scoreLevel;
        if (cowsScore >= 25) {
            severity = "重度";
            scoreLevel = 15;
        } else if (cowsScore >= 13) {
            severity = "中度";
            scoreLevel = 10;
        } else if (cowsScore >= 6) {
            severity = "轻度";
            scoreLevel = 5;
        } else {
            severity = "无或轻微";
            scoreLevel = 0;
        }
        return mapOf("cow_score", cowsScore, "severity", severity, "score_level", scoreLevel);
    }

    private static Map<String, Object> generateOutpatientVisits(long seed) {
        Random random = random(seed, 3);
        int visitCount = weightedPick(random, new int[] {0, 1, 2, 3, 4, 5}, new double[] {0.4d, 0.25d, 0.15d, 0.1d, 0.07d, 0.03d});
        if (visitCount >= 3) {
            boolean emergency = random.nextBoolean();
            return mapOf(
                    "visit_count", visitCount,
                    "description", "出所就医" + visitCount + "次以上" + (emergency ? "，包含紧急外诊" : ""),
                    "score_level", 15);
        }
        if (visitCount == 2) {
            return mapOf("visit_count", 2, "description", "有2次出所就医", "score_level", 10);
        }
        if (visitCount == 1) {
            return mapOf("visit_count", 1, "description", "有1次出所就医，为常规检查", "score_level", 5);
        }
        return mapOf("visit_count", 0, "description", "无出所就医", "score_level", 0);
    }

    private static Map<String, Object> generateAbnormalSignals(long seed) {
        Random random = random(seed, 4);
        double roll = random.nextDouble();
        if (roll < 0.1d) {
            return mapOf(
                    "has_abnormal_signal", true,
                    "severity", "危急",
                    "symptoms", List.of(pick(random, "剧烈胸痛", "咯血", "呕血", "便血", "意识模糊", "晕厥")),
                    "score_level", 10);
        }
        if (roll < 0.25d) {
            return mapOf(
                    "has_abnormal_signal", true,
                    "severity", "持续",
                    "symptoms", List.of(pick(random, "持续腹痛", "持续性头痛", "原因不明乏力", "持续头晕")),
                    "score_level", 5);
        }
        if (roll < 0.45d) {
            return mapOf(
                    "has_abnormal_signal", true,
                    "severity", "轻度",
                    "symptoms", List.of(pick(random, "食欲不振", "体重明显下降", "间断失眠")),
                    "score_level", 3);
        }
        return mapOf(
                "has_abnormal_signal", false,
                "severity", null,
                "symptoms", List.of(),
                "score_level", 0);
    }

    private static Map<String, Object> generateSleepQuality(long seed) {
        Random random = random(seed, 5);
        int sleepHours = weightedPick(random, new int[] {2, 3, 4, 5, 6, 7, 8}, new double[] {0.05d, 0.08d, 0.12d, 0.15d, 0.25d, 0.2d, 0.15d});
        double roll = random.nextDouble();
        if (sleepHours < 4 || roll < 0.1d) {
            String description = sleepHours < 4 ? "每日睡眠时间约" + sleepHours + "小时，严重失眠" : "彻夜不眠或睡眠节律完全颠倒";
            return mapOf(
                    "sleep_hours", sleepHours,
                    "sleep_quality", "严重失眠",
                    "description", description,
                    "score_level", 10);
        }
        if (sleepHours < 6) {
            return mapOf(
                    "sleep_hours", sleepHours,
                    "sleep_quality", "中度失眠",
                    "description", "每日睡眠时间约" + sleepHours + "小时，睡眠质量差",
                    "score_level", 5);
        }
        if (sleepHours < 7 || roll < 0.3d) {
            return mapOf(
                    "sleep_hours", sleepHours,
                    "sleep_quality", "轻度睡眠问题",
                    "description", "每日睡眠时间约" + sleepHours + "小时，睡眠浅或多梦",
                    "score_level", 3);
        }
        return mapOf(
                "sleep_hours", sleepHours,
                "sleep_quality", "正常",
                "description", "每日睡眠时间约" + sleepHours + "小时，质量尚可",
                "score_level", 0);
    }

    private static Map<String, Object> generateAgeScore(int age) {
        if (age >= 70) {
            return mapOf("age", age, "age_range", "70岁以上", "score_level", 20);
        }
        if (age >= 60) {
            return mapOf("age", age, "age_range", "60-70岁", "score_level", 15);
        }
        if (age >= 51) {
            return mapOf("age", age, "age_range", "51-60岁", "score_level", 10);
        }
        if (age >= 18) {
            return mapOf("age", age, "age_range", "18-50岁", "score_level", 5);
        }
        return mapOf("age", age, "age_range", "18岁以下", "score_level", 0);
    }

    private static Map<String, Object> generateClinicVisits(long seed) {
        Random random = random(seed, 6);
        int visitCount = weightedPick(random, new int[] {0, 1, 2, 3, 4, 5, 6}, new double[] {0.5d, 0.2d, 0.12d, 0.1d, 0.05d, 0.02d, 0.01d});
        if (visitCount >= 5) {
            return mapOf(
                    "clinic_visit_count", visitCount,
                    "description", "所内就医" + visitCount + "次，频繁报医",
                    "score_level", 5);
        }
        if (visitCount >= 3) {
            return mapOf(
                    "clinic_visit_count", visitCount,
                    "description", "所内就医" + visitCount + "次，病情反复",
                    "score_level", 3);
        }
        if (visitCount >= 1) {
            return mapOf(
                    "clinic_visit_count", visitCount,
                    "description", "所内就医" + visitCount + "次，常见轻微病症",
                    "score_level", 2);
        }
        return mapOf("clinic_visit_count", 0, "description", "无所内就医", "score_level", 0);
    }

    private static Map<String, Object> categoryScore(String code, String name, Map<String, Object> rawData, String reason) {
        return mapOf(
                "code", code,
                "name", name,
                "score", intValue(rawData.get("score_level")),
                "reason", reason,
                "raw_data", rawData);
    }

    private static List<String> buildSuggestions(String riskLevel, List<Map<String, Object>> highlights) {
        List<String> suggestions = new ArrayList<>();
        if (Objects.equals(riskLevel, "高风险")) {
            suggestions.add("建议立即纳入重点健康监测名单，必要时安排医疗岗位会诊。");
            suggestions.add("对高分维度建立连续跟踪记录，缩短复查周期。");
        } else if (Objects.equals(riskLevel, "中风险")) {
            suggestions.add("建议按周跟踪主要风险维度，重点观察症状是否持续。");
            suggestions.add("结合生活作息、睡眠和就医记录做持续监测。");
        } else {
            suggestions.add("当前整体风险较低，建议保持常规健康巡查与基础筛查。");
            suggestions.add("如异常信号或睡眠问题持续出现，应及时复评。");
        }
        for (Map<String, Object> highlight : highlights) {
            suggestions.add("重点关注「" + text(highlight.get("name")) + "」：" + text(highlight.get("reason")));
        }
        return suggestions.stream().distinct().toList();
    }

    private static String buildReport(
            String idCard,
            Map<String, Object> basicInfo,
            String riskLevel,
            int totalScore,
            List<Map<String, Object>> categoryScores,
            List<Map<String, Object>> highlights) {
        StringBuilder builder = new StringBuilder();
        builder.append("【身体健康风险评估报告】\n\n");
        builder.append("用户基本信息：\n");
        builder.append("- 身份证号：").append(idCard).append('\n');
        builder.append("- 年龄：").append(intValue(basicInfo.get("age"))).append("岁\n");
        builder.append("- 性别：").append(text(basicInfo.get("gender"))).append("\n\n");
        builder.append("风险评估结果：\n");
        builder.append("- 风险级别：").append(riskLevel).append('\n');
        builder.append("- 风险总分：").append(totalScore).append("分\n\n");
        builder.append("各维度得分：\n");
        for (int i = 0; i < categoryScores.size(); i++) {
            Map<String, Object> item = categoryScores.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(text(item.get("name")))
                    .append("：")
                    .append(intValue(item.get("score")))
                    .append("分\n   - 原因：")
                    .append(text(item.get("reason")))
                    .append('\n');
        }
        if (!highlights.isEmpty()) {
            builder.append("\n重点风险提示：\n");
            for (int i = 0; i < highlights.size(); i++) {
                Map<String, Object> item = highlights.get(i);
                builder.append(i + 1)
                        .append(". ")
                        .append(text(item.get("name")))
                        .append("（")
                        .append(intValue(item.get("score")))
                        .append("分）：")
                        .append(text(item.get("reason")))
                        .append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static String reasonForCategory1(Map<String, Object> data) {
        if (!boolValue(data.get("has_disease"))) {
            return "无基础性疾病，体检指标正常。";
        }
        return "存在" + text(data.get("severity")) + "疾病：" + text(data.get("disease_type")) + "。";
    }

    private static String reasonForCategory2(Map<String, Object> data) {
        if (!boolValue(data.get("has_complication"))) {
            return "未见毒品相关躯体并发症。";
        }
        return text(data.get("complication_type")) + "，具体表现为" + text(data.get("specific_complication")) + "。";
    }

    private static String reasonForCategory3(Map<String, Object> data) {
        return "COWS 评分为" + intValue(data.get("cow_score")) + "，属于" + text(data.get("severity")) + "戒断反应。";
    }

    private static String reasonForCategory4(Map<String, Object> data) {
        return text(data.get("description")) + "。";
    }

    private static String reasonForCategory5(Map<String, Object> data) {
        if (!boolValue(data.get("has_abnormal_signal"))) {
            return "暂无异常生理信号。";
        }
        return text(data.get("severity")) + "异常信号：" + String.join("、", stringList(data.get("symptoms"))) + "。";
    }

    private static String reasonForCategory6(Map<String, Object> data) {
        return text(data.get("description")) + "。";
    }

    private static String reasonForCategory7(Map<String, Object> data) {
        return "年龄为" + intValue(data.get("age")) + "岁，归入" + text(data.get("age_range")) + "。";
    }

    private static String reasonForCategory8(Map<String, Object> data) {
        return text(data.get("description")) + "。";
    }

    private static Random random(long seed, int offset) {
        return new Random(seed + offset);
    }

    private static int weightedPick(Random random, int[] values, double[] weights) {
        double roll = random.nextDouble();
        double cumulative = 0d;
        for (int i = 0; i < values.length; i++) {
            cumulative += weights[i];
            if (roll <= cumulative || i == values.length - 1) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    @SafeVarargs
    private static <T> T pick(Random random, T... values) {
        return values[random.nextInt(values.length)];
    }

    private static String randomIdCard() {
        Random random = new Random();
        int year = 1970 + random.nextInt(31);
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28);
        int sequence = 100 + random.nextInt(900);
        int check = random.nextInt(10);
        return "110101" + year + String.format(Locale.ROOT, "%02d%02d", month, day) + sequence + check;
    }

    private static long seedFromIdCard(String idCard) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(idCard.getBytes(StandardCharsets.UTF_8));
            long result = 0L;
            for (int i = 0; i < 4; i++) {
                result = (result << 8) | (digest[i] & 0xffL);
            }
            return result;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 不可用", ex);
        }
    }

    private static String normalizeIdCard(String idCard) {
        String normalized = text(idCard).toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[0-9]{15}$|^[0-9]{17}[0-9X]$")) {
            throw new IllegalArgumentException("身份证号格式错误，应为 15 位或 18 位");
        }
        return normalized;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(DetoxHealthRiskToolProvider::text).filter(StringUtils::hasText).toList();
        }
        return List.of();
    }

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Integer.parseInt(text);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String toJson(Object value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    private static String errorJson(String message) {
        return toJson(mapOf("error", message));
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
