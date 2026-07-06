package lingzhou.agent.backend.business.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationMessage;
import org.junit.jupiter.api.Test;

class ConversationEventServiceTest {

    @Test
    void shouldStripArtifactDownloadLinksFromAssistantHistoryText() {
        String content =
                """
                已为您完成筛选！从武汉活动名单中筛选出了湖北省内所有城市的企业，共132家。

                您可以下载处理后的文件：
                - [湖北省所有城市企业名单.xlsx](/api/files/artifacts/abc/download?fileName=test.xlsx)
                """;

        String sanitized = ConversationEventService.sanitizeAssistantHistoryText(content);

        assertThat(sanitized).contains("已为您完成筛选");
        assertThat(sanitized).contains("湖北省所有城市企业名单.xlsx");
        assertThat(sanitized).doesNotContain("/api/files/artifacts/");
        assertThat(sanitized).doesNotContain("/download?fileName=");
    }

    @Test
    void shouldExcludeFailedAssistantMessagesFromHistory() {
        ConversationMessage failedAssistant = new ConversationMessage();
        failedAssistant.setRole("ASSISTANT");
        failedAssistant.setStatus("FAILED");
        failedAssistant.setContent("当前 REACT 决策 JSON 仅允许 type=tool；最终回答请直接输出正文");

        ConversationMessage completedAssistant = new ConversationMessage();
        completedAssistant.setRole("ASSISTANT");
        completedAssistant.setStatus("COMPLETED");
        completedAssistant.setContent("已为您完成筛选");

        assertThat(ConversationEventService.shouldIncludeHistoryMessage(failedAssistant)).isFalse();
        assertThat(ConversationEventService.shouldIncludeHistoryMessage(completedAssistant)).isTrue();
    }

    @Test
    void shouldStripAssistantExecutionChatterButKeepStableSummary() {
        String content =
                """
                我将使用会议助手技能查询“数字化经营分析会”的会议详情。

                首先，我需要确认会议室预订对象的信息：

                我注意到数据集工具返回了错误，提示数据集不存在。让我尝试直接执行查询。

                已整理会议详细信息，请查看右侧预览。
                """;

        String sanitized = ConversationEventService.sanitizeAssistantHistoryText(content);

        assertThat(sanitized).isEqualTo("已整理会议详细信息，请查看右侧预览。");
        assertThat(sanitized).doesNotContain("我将使用会议助手技能");
        assertThat(sanitized).doesNotContain("让我尝试");
    }

    @Test
    void shouldCollapseSystemHistoryIntoSinglePromptSummary() {
        List<ConversationEventService.MemoryItem> items = List.of(
                new ConversationEventService.MemoryItem(
                        "EVENT", "SYSTEM", "[运行事实] 路由到经营指标技能", new Date(1), "E-1", null, 1, 10),
                new ConversationEventService.MemoryItem(
                        "MESSAGE", "USER", "今年整体经营情况怎么样?", new Date(2), "M-1", 1, null, 10),
                new ConversationEventService.MemoryItem(
                        "EVENT", "SYSTEM", "[运行事实] 已生成经营指标整体情况.html", new Date(3), "E-2", null, 2, 10),
                new ConversationEventService.MemoryItem(
                        "MESSAGE", "ASSISTANT", "已整理经营指标结果，请查看右侧预览。", new Date(4), "M-2", 2, null, 10));

        List<ConversationEventService.MemoryItem> collapsed =
                ConversationEventService.collapseSystemHistoryForPrompt(items);

        assertThat(collapsed).hasSize(3);
        assertThat(collapsed.get(0).role()).isEqualTo("SYSTEM");
        assertThat(collapsed.get(0).text()).contains("[历史运行摘要]");
        assertThat(collapsed.get(0).text()).contains("不能视为当前轮已经完成");
        assertThat(collapsed.get(0).text()).contains("路由到经营指标技能");
        assertThat(collapsed.get(0).text()).contains("已生成经营指标整体情况.html");
    }

    @Test
    void shouldKeepOnlyFirstFourSystemFactsInPromptSummary() {
        List<ConversationEventService.MemoryItem> items = List.of(
                new ConversationEventService.MemoryItem("EVENT", "SYSTEM", "事实1", new Date(1), "E-1", null, 1, 1),
                new ConversationEventService.MemoryItem("EVENT", "SYSTEM", "事实2", new Date(2), "E-2", null, 2, 1),
                new ConversationEventService.MemoryItem("EVENT", "SYSTEM", "事实3", new Date(3), "E-3", null, 3, 1),
                new ConversationEventService.MemoryItem("EVENT", "SYSTEM", "事实4", new Date(4), "E-4", null, 4, 1),
                new ConversationEventService.MemoryItem("EVENT", "SYSTEM", "事实5", new Date(5), "E-5", null, 5, 1));

        ConversationEventService.MemoryItem summary = ConversationEventService.buildPromptSystemSummary(items);

        assertThat(summary.text()).contains("事实1");
        assertThat(summary.text()).contains("事实4");
        assertThat(summary.text()).doesNotContain("事实5");
        assertThat(summary.text()).contains("其余历史运行细节已省略");
    }
}
