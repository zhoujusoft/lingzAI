# vLLM Tool Calls 调试脚本

本目录用于在 `deploy/lingz` 部署现场手动验证 vLLM OpenAI-compatible `/v1/chat/completions` 的原生 `tool_calls` 行为，重点排查流式输出、工具参数、`enable_thinking=false`、多工具 schema 对工具选择的影响。

## 目录结构

- `debug-vllm-tool-calls.sh`：调试入口脚本
- `payloads/5.json`：最小 `listActiveSkills` 流式 tool call 请求体
- `payloads/lingz-vllm-normalized-body.json`：接近 Lingz 运行时的完整归一化请求体，可用于 `00-replay`
- `payloads/2.json` 至 `payloads/8.json`：历史排查样例，覆盖不同 prompt、工具集合、模型名和参数组合

## 前置条件

- vLLM 服务已启动，并暴露 OpenAI-compatible 接口
- 模型支持原生 tool calling
- 已确认接口地址、模型名和 API Key；若 vLLM 未启用鉴权，可填任意非空 Key

不要把生产密钥写进 JSON 文件或提交到仓库，建议通过环境变量传入。

## 快速使用

在本目录执行：

```bash
cd deploy/lingz/scripts/vllm-tool-calls
chmod +x debug-vllm-tool-calls.sh

VLLM_ENDPOINT="http://127.0.0.1:8000/v1/chat/completions" \
VLLM_MODEL="qwen3.5-397b" \
VLLM_API_KEY="EMPTY" \
./debug-vllm-tool-calls.sh all
```

常用环境变量：

- `VLLM_ENDPOINT`：vLLM chat completions 地址，默认 `http://127.0.0.1:8000/v1/chat/completions`
- `VLLM_MODEL`：请求体中的模型名，默认 `qwen3.5-397b`
- `VLLM_API_KEY`：Bearer Token，必填
- `PROGRAM_BODY_FILE`：`00-replay` 使用的完整请求体文件，默认 `./payloads/lingz-vllm-normalized-body.json`

## Case 说明

```bash
# 跑基础组合：短 prompt 非流式、短 prompt 流式、完整工具集、项目 prompt
./debug-vllm-tool-calls.sh all

# 只验证最短 tool call 请求
./debug-vllm-tool-calls.sh 01-short

# 验证不同工具集合对工具选择的影响
./debug-vllm-tool-calls.sh tools

# 验证不同工具 schema 命名对工具选择的影响
./debug-vllm-tool-calls.sh schema

# 回放完整请求体，默认使用 payloads/lingz-vllm-normalized-body.json
./debug-vllm-tool-calls.sh 00-replay

# 回放指定请求体
PROGRAM_BODY_FILE="./payloads/5.json" ./debug-vllm-tool-calls.sh 00-replay
```

## 观察重点

- 返回是否包含 OpenAI 原生 `tool_calls`
- 流式响应中 `delta.tool_calls` 是否完整、可聚合
- `function.name` 是否命中预期工具，例如 `listActiveSkills` 或 `parse_file`
- `function.arguments` 是否是合法 JSON 字符串
- 是否错误输出 `<tool_call>`、`<function_calls>`、`<think>` 等伪标签文本
- usage 信息是否随 `stream_options.include_usage=true` 返回

如果 `00-replay` 正常但脚本生成的 case 异常，优先比较 prompt、tools schema、`stream`、`temperature`、`enable_thinking` 和 `chat_template_kwargs` 差异。
