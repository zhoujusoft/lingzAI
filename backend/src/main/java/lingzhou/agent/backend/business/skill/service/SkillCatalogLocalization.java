package lingzhou.agent.backend.business.skill.service;

import java.util.Map;
import org.springframework.util.StringUtils;

final class SkillCatalogLocalization {

    private static final String DEFAULT_SKILL_CATEGORY = "通用能力";

    private static final Map<String, SkillLabel> SKILL_LABELS = Map.ofEntries(
            Map.entry("inventory", new SkillLabel("库存管理", "查询服装商品的实时库存、尺码分布和缺货风险。")),
            Map.entry("pricing", new SkillLabel("定价分析", "查询商品售价、成本和毛利空间，辅助定价决策。")),
            Map.entry("trend", new SkillLabel("销售趋势", "分析热销趋势、需求变化和未来销量预测。")),
            Map.entry("supplier", new SkillLabel("供应商管理", "查询供应商目录、批发价格、库存和报价信息。")),
            Map.entry("purchase", new SkillLabel("采购策略", "基于库存与销售趋势生成补货和采购优化建议。")),
            Map.entry("weather", new SkillLabel("天气助手", "查询城市天气信息，辅助出行和经营判断。")),
            Map.entry("fashion-guide", new SkillLabel("时尚导购", "提供服装行业趋势、搭配建议和参考资料。")),
            Map.entry("algorithmic-art", new SkillLabel("算法艺术", "通过代码生成算法艺术与交互式视觉作品。")),
            Map.entry("pdf-extractor", new SkillLabel("PDF 提取", "提取 PDF 文本和元数据，便于分析与处理。")),
            Map.entry("pdf", new SkillLabel("PDF 文档处理", "读取、提取、合并、拆分和生成 PDF 文档，支持表单填写、加密与 OCR 场景。")),
            Map.entry("docx", new SkillLabel("Word 文档处理", "创建、读取、编辑和重组 Word 文档，支持格式调整、内容替换和专业文档生成。")),
            Map.entry("pptx", new SkillLabel("PPT 生成与编辑", "读取、生成和编辑 PPT 演示文稿，支持模板改写、内容提取和页面检查。")),
            Map.entry("xlsx", new SkillLabel("Excel 表格处理", "读取、整理、计算和生成 Excel 表格，支持公式、格式化和数据分析。")),
            Map.entry("ui-ux-pro-max", new SkillLabel("UI/UX 设计专家", "提供界面设计、交互优化和前端体验改进建议。")),
            Map.entry("market-research", new SkillLabel("市场研究", "开展市场调研、竞品分析、投资人尽调和行业研究，输出带来源依据的决策建议。")),
            Map.entry("contract-review-pro", new SkillLabel("专业合同审核", "基于合同审核方法论提供合同审阅、风险识别和建议输出。")),
            Map.entry(
                    "legal-consultation",
                    new SkillLabel(
                            "法律案件查询",
                            "根据案件描述分析法律风险、识别相关法条，并提供处理建议、证据收集指导和立案条件说明。")),
            Map.entry(
                    "form-app-assistant",
                    new SkillLabel(
                            "表单应用助手",
                            "引导创建或扩展表单应用，结合文字、Excel 与参考资料生成标准化表单模型。")));

    private static final Map<String, String> SKILL_CATEGORY_LABELS = Map.ofEntries(
            Map.entry("inventory", "库存管理"),
            Map.entry("pricing", "定价决策"),
            Map.entry("trend", "销售分析"),
            Map.entry("supplier", "采购供应"),
            Map.entry("purchase", "采购供应"),
            Map.entry("weather", "信息查询"),
            Map.entry("fashion-guide", "行业导购"),
            Map.entry("algorithmic-art", "创意生成"),
            Map.entry("pdf-extractor", "文档处理"),
            Map.entry("pdf", "文档处理"),
            Map.entry("docx", "文档处理"),
            Map.entry("pptx", "演示制作"),
            Map.entry("xlsx", "数据处理"),
            Map.entry("ui-ux-pro-max", "设计体验"),
            Map.entry("market-research", "商业研究"),
            Map.entry("contract-review-pro", "法务合规"),
            Map.entry("legal-consultation", "法务合规"),
            Map.entry("brand-guidelines", "品牌规范"),
            Map.entry("form-app-assistant", "表单搭建"));

    private static final Map<String, String> RAW_CATEGORY_LABELS = Map.ofEntries(
            Map.entry("retail", "零售经营"),
            Map.entry("information", "信息查询"));

    private static final Map<String, ToolLabel> TOOL_LABELS = Map.ofEntries(
            Map.entry("readFile", new ToolLabel("读取文件", "读取本地 UTF-8 文本文件内容。")),
            Map.entry("writeFile", new ToolLabel("写入文件", "将 UTF-8 文本写入本地文件，必要时自动创建目录。")),
            Map.entry("runPython", new ToolLabel("执行 Python", "执行指定 Python 脚本并返回标准输出。")),
            Map.entry("get_render_template", new ToolLabel("获取渲染模板", "根据模板编码返回前端渲染模板定义，可选结合目标 API 工具生成有效 dataSchema。")),
            Map.entry("build_frontend_render_payload", new ToolLabel("封装前端渲染结果", "根据模板、业务数据和组件配置封装统一的前端渲染结果。")),
            Map.entry("generate_frontend_render", new ToolLabel("生成前端渲染", "将结构化 JSON 数据封装成前端组件可直接消费的渲染结果。")),
            Map.entry("listActiveSkills", new ToolLabel("查看已激活技能", "列出当前已激活技能及其说明，便于判断可调用能力。")),
            Map.entry("loadSkillContent", new ToolLabel("读取技能内容", "读取指定技能的完整内容，用于查看技能说明和可用工具。")),
            Map.entry("loadSkillReference", new ToolLabel("读取技能参考资料", "读取指定技能的参考资料条目，用于补充技能上下文。")),
            Map.entry("checkInventory", new ToolLabel("查询库存", "查询指定品类或全部商品的库存、尺码和库存状态。")),
            Map.entry("getPricing", new ToolLabel("查询定价", "查询商品售价、成本价、毛利率和建议价格区间。")),
            Map.entry("getSalesTrends", new ToolLabel("销售趋势分析", "查询指定周期内的热销商品、销售速度和市场趋势。")),
            Map.entry("getPredictedDemand", new ToolLabel("需求预测", "预测未来周期的需求量、建议备货量和风险等级。")),
            Map.entry("getSupplierCatalog", new ToolLabel("供应商目录", "查询供应商可售商品、批发价、起订量和供货情况。")),
            Map.entry("getSupplierQuote", new ToolLabel("获取供应商报价", "根据供应商 SKU 和数量生成明细报价与折扣信息。")),
            Map.entry("generatePurchaseStrategy", new ToolLabel("生成采购策略", "根据预算和优先级生成补货与采购策略建议。")),
            Map.entry("optimizePurchaseOrder", new ToolLabel("优化采购单", "优化采购单数量与成本结构，提升 ROI 和折扣收益。")),
            Map.entry("getWeather", new ToolLabel("查询天气", "查询指定城市的天气、温度、湿度和风速。")));

    private SkillCatalogLocalization() {}

    static SkillLabel resolveSkill(String runtimeSkillName, String fallbackDescription) {
        SkillLabel label = SKILL_LABELS.get(runtimeSkillName);
        if (label != null) {
            return label;
        }
        return new SkillLabel(runtimeSkillName, normalizeDescription(fallbackDescription));
    }

    static ToolLabel resolveTool(String toolName, String fallbackDescription) {
        ToolLabel label = TOOL_LABELS.get(toolName);
        if (label != null) {
            return label;
        }
        if (toolName != null && toolName.startsWith("mcp.")) {
            return new ToolLabel(toolName, normalizeDescription(fallbackDescription));
        }
        return new ToolLabel(toolName, normalizeDescription(fallbackDescription));
    }

    static String resolveCategory(String runtimeSkillName, String rawCategory) {
        String category = SKILL_CATEGORY_LABELS.get(runtimeSkillName);
        if (StringUtils.hasText(category)) {
            return category;
        }
        if (StringUtils.hasText(rawCategory)) {
            String normalized = rawCategory.trim();
            return RAW_CATEGORY_LABELS.getOrDefault(normalized, normalized);
        }
        return DEFAULT_SKILL_CATEGORY;
    }

    static boolean isLegacyCategoryValue(String category, String rawCategory, String source) {
        if (!StringUtils.hasText(category)) {
            return true;
        }
        String normalized = category.trim();
        if (DEFAULT_SKILL_CATEGORY.equals(normalized) || "通用".equals(normalized)) {
            return true;
        }
        if (StringUtils.hasText(rawCategory) && normalized.equals(rawCategory.trim())) {
            return true;
        }
        if (StringUtils.hasText(source) && normalized.equals(source.trim())) {
            return true;
        }
        return "example".equals(normalized)
                || "resources".equals(normalized)
                || "retail".equals(normalized)
                || "information".equals(normalized)
                || "内置".equals(normalized)
                || "外部".equals(normalized);
    }

    private static String normalizeDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    record SkillLabel(String displayName, String description) {}

    record ToolLabel(String displayName, String description) {}
}
