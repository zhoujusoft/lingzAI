package lingzhou.agent.spring.ai.skill.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

class SkillAwareToolCallingManagerTest {

    @Test
    void invalidToolArgumentsAreNotReplayedToProvider() {
        SkillAwareToolCallingManager manager =
                new SkillAwareToolCallingManager(null, DefaultToolCallingManager.builder().build());
        Prompt prompt = new Prompt(new UserMessage("生成 HTML 报告"));
        AssistantMessage malformedToolCall = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "file_write", "{\"path\":\"/workspace/report.html\",\"content\":\"")))
                .build();

        ToolExecutionResult result =
                manager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(malformedToolCall))));

        assertThat(result.conversationHistory())
                .noneMatch(message -> message instanceof AssistantMessage assistant && assistant.hasToolCalls());
        assertThat(result.conversationHistory().get(result.conversationHistory().size() - 1))
                .isInstanceOfSatisfying(
                        UserMessage.class,
                        message -> assertThat(message.getText())
                                .contains("工具调用参数非法")
                                .contains("拆分为更小的工具调用"));
    }
}
