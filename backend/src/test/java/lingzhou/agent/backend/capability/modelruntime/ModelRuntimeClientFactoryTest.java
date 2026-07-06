package lingzhou.agent.backend.capability.modelruntime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

class ModelRuntimeClientFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNormalizeVllmToolCallArgumentsAsStrings() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(
                """
                {
                  "messages": [
                    {
                      "role": "assistant",
                      "tool_calls": [
                        {
                          "id": "call-null",
                          "type": "function",
                          "function": {
                            "name": "search",
                            "arguments": null
                          }
                        },
                        {
                          "id": "call-object",
                          "type": "function",
                          "function": {
                            "name": "search",
                            "arguments": {
                              "city": "武汉"
                            }
                          }
                        },
                        {
                          "id": "call-blank",
                          "type": "function",
                          "function": {
                            "name": "search",
                            "arguments": " "
                          }
                        },
                        {
                          "id": "call-string",
                          "type": "function",
                          "function": {
                            "name": "search",
                            "arguments": "{\\"query\\":\\"报销\\"}"
                          }
                        }
                      ]
                    },
                    {
                      "role": "assistant",
                      "toolCalls": [
                        {
                          "id": "call-camel",
                          "type": "function",
                          "function": {
                            "name": "search",
                            "arguments": {}
                          }
                        }
                      ]
                    }
                  ]
                }
                """);

        boolean changed = ModelRuntimeClientFactory.normalizeVllmToolCallArguments(request);

        assertThat(changed).isTrue();
        ArrayNode snakeToolCalls = (ArrayNode) request.get("messages").get(0).get("tool_calls");
        assertArgumentsText(snakeToolCalls.get(0), "{}");
        assertArgumentsText(snakeToolCalls.get(1), "{\"city\":\"武汉\"}");
        assertArgumentsText(snakeToolCalls.get(2), "{}");
        assertArgumentsText(snakeToolCalls.get(3), "{\"query\":\"报销\"}");
        ArrayNode camelToolCalls = (ArrayNode) request.get("messages").get(1).get("toolCalls");
        assertArgumentsText(camelToolCalls.get(0), "{}");
    }

    @Test
    void shouldRoundTripSpringAiChatCompletionRequestAfterNormalization() throws Exception {
        OpenAiApi.ChatCompletionMessage.ChatCompletionFunction function =
                new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("search", null);
        OpenAiApi.ChatCompletionMessage.ToolCall toolCall =
                new OpenAiApi.ChatCompletionMessage.ToolCall("call-null", "function", function);
        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                null,
                OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                null,
                null,
                List.of(toolCall),
                null,
                null,
                null,
                null);
        OpenAiApi.ChatCompletionRequest request =
                new OpenAiApi.ChatCompletionRequest(List.of(message), "qwen", List.of(), null);
        ObjectNode requestNode = objectMapper.valueToTree(request);

        boolean changed = ModelRuntimeClientFactory.normalizeVllmToolCallArguments(requestNode);
        OpenAiApi.ChatCompletionRequest normalized =
                objectMapper.treeToValue(requestNode, OpenAiApi.ChatCompletionRequest.class);

        assertThat(changed).isTrue();
        assertThat(normalized.messages().get(0).toolCalls().get(0).function().arguments())
                .isEqualTo("{}");
    }

    private void assertArgumentsText(JsonNode toolCall, String expected) {
        JsonNode arguments = toolCall.get("function").get("arguments");
        assertThat(arguments.isTextual()).isTrue();
        assertThat(arguments.asText()).isEqualTo(expected);
    }
}
