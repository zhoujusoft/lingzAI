#!/usr/bin/env bash
set -euo pipefail

# Manual vLLM OpenAI-compatible tool-calling probe for deploy/lingz.
# Usage:
#   VLLM_ENDPOINT='http://127.0.0.1:8000/v1/chat/completions' VLLM_API_KEY='...' ./debug-vllm-tool-calls.sh all
#   VLLM_ENDPOINT='http://127.0.0.1:8000/v1/chat/completions' VLLM_API_KEY='...' ./debug-vllm-tool-calls.sh 01-short
#   VLLM_ENDPOINT='http://127.0.0.1:8000/v1/chat/completions' VLLM_API_KEY='...' PROGRAM_BODY_FILE=./payloads/5.json ./debug-vllm-tool-calls.sh 00-replay

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

endpoint="${VLLM_ENDPOINT:-http://127.0.0.1:8000/v1/chat/completions}"
model="${VLLM_MODEL:-qwen3.5-397b}"
api_key="${VLLM_API_KEY:-}"
case_name="${1:-all}"

if [[ -z "${api_key}" ]]; then
  echo "VLLM_API_KEY is required" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

curl_payload() {
  local name="$1"
  local file="$2"
  echo
  echo "===== ${name} ====="
  curl -sS "${endpoint}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${api_key}" \
    --data-binary @"${file}"
  echo
}

write_common_tools_json() {
  cat <<'JSON'
[
  {
    "type": "function",
    "function": {
      "description": "Inspect metadata of a logical runtime file or directory path, including existence, size and type.",
      "name": "stat",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {
            "type": "string",
            "description": "Logical or relative file path"
          }
        },
        "required": ["arg0"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "Read a UTF-8 text file from a logical runtime path such as /workspace, /outputs, /temp, /profile or a relative path inside the current runtime workspace. Do not use this for binary files like .xlsx, .docx or .pdf.",
      "name": "file_read",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {
            "type": "string",
            "description": "Logical or relative UTF-8 text file path"
          }
        },
        "required": ["arg0"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "Publish a final downloadable artifact. Prefer sourcePath when a script already generated the file. Use content only for UTF-8 text artifacts.",
      "name": "write_artifact",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {"type": "string", "description": "Artifact category folder, e.g. translation"},
          "arg1": {"type": "string", "description": "Final file name, e.g. translated-output.docx"},
          "arg2": {"type": "string", "description": "UTF-8 text content. Leave empty when sourcePath is provided; never serialize binary document content here."},
          "arg3": {"type": "string", "description": "Optional logical source file path under current runtime workspace. Existing files are copied into /outputs and uploaded as artifacts."},
          "arg4": {"type": "string", "description": "Optional MIME content type"}
        },
        "required": ["arg0", "arg1", "arg2", "arg3", "arg4"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "Write UTF-8 text content to a logical runtime file path such as /workspace/... or a relative path inside the current runtime workspace.",
      "name": "file_write",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {"type": "string", "description": "Logical or relative file path"},
          "arg1": {"type": "string", "description": "UTF-8 text content"}
        },
        "required": ["arg0", "arg1"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "List entries under a logical runtime directory such as /workspace, /uploads, /outputs, /temp or a relative directory inside the current runtime workspace.",
      "name": "list_dir",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {"type": "string", "description": "Logical or relative directory path"}
        },
        "required": ["arg0"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "Execute a Python script that already exists under /workspace or /skill/scripts. Pass input and output logical paths through args instead of hardcoding them in the script body.",
      "name": "run_python",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {"type": "string", "description": "Logical script path under /workspace; /skill/scripts is only valid in skill world"},
          "arg1": {"type": "array", "items": {"type": "string"}, "description": "Optional argument array or JSON array string."},
          "arg2": {"type": "string", "description": "Optional logical working directory, default /workspace"},
          "arg3": {"type": "integer", "description": "Optional timeout in seconds, default 120, max 300"}
        },
        "required": ["arg0", "arg1", "arg2", "arg3"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "Parse an uploaded attachment into model-readable content on demand. Use this when you need to understand the content of a binary attachment such as .docx, .xlsx or other supported office files. Do not use direct file_read for binary attachments.",
      "name": "parse_file",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "Uploaded file name, or runtime logical path such as /uploads/... /temp/... /outputs/... /workspace/..."
          },
          "mode": {
            "type": "string",
            "description": "Optional parse mode: structured, markdown or text",
            "enum": ["structured", "markdown", "text"]
          }
        },
        "required": ["path", "mode"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "List all skills currently available to the user for this conversation, and indicate which ones are already loaded in the current runtime.",
      "name": "listActiveSkills",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {},
        "required": []
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "Load the content of a skill by its runtime skill name. This activates the skill for the current runtime and returns its documentation.",
      "name": "loadSkillContent",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {"type": "string", "description": "The name of the skill to load"}
        },
        "required": ["arg0"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "description": "ONLY use this tool when a skill's content explicitly mentions it has reference materials. Load a specific reference from a skill using the reference key mentioned in the skill's content.",
      "name": "loadSkillReference",
      "parameters": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": false,
        "type": "object",
        "properties": {
          "arg0": {"type": "string", "description": "The skill name that has references"},
          "arg1": {"type": "string", "description": "The exact reference key mentioned in the skill's content"}
        },
        "required": ["arg0", "arg1"]
      }
    }
  }
]
JSON
}

write_parse_file_tool_json() {
  cat <<'JSON'
[
  {
    "type": "function",
    "function": {
      "name": "parse_file",
      "description": "Parse an uploaded attachment into model-readable content on demand. Use this when you need to read or analyze an uploaded file.",
      "parameters": {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "Uploaded file name or runtime logical path, for example: 科技信息化总队2月12日至2月28日值班表.xls or /uploads/科技信息化总队2月12日至2月28日值班表.xls"
          },
          "mode": {
            "type": "string",
            "description": "Parse mode: text, markdown, or structured",
            "enum": ["text", "markdown", "structured"]
          }
        },
        "required": ["path", "mode"]
      }
    }
  }
]
JSON
}

tools_filter() {
  local tools_json="$1"
  local names_csv="$2"
  python3 - "$tools_json" "$names_csv" <<'PY'
import json
import sys

tools = json.loads(sys.argv[1])
names = {name.strip() for name in sys.argv[2].split(",") if name.strip()}
filtered = [tool for tool in tools if tool.get("function", {}).get("name") in names]
print(json.dumps(filtered, ensure_ascii=False))
PY
}

write_schema_probe_tools_json() {
  local variant="$1"
  python3 - "$variant" <<'PY'
import json
import sys

variant = sys.argv[1]
parse_file = {
    "type": "function",
    "function": {
        "name": "parse_file",
        "description": "Parse an uploaded attachment into model-readable content on demand. Use this when you need to read or analyze an uploaded file.",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Uploaded file name or runtime logical path."
                },
                "mode": {
                    "type": "string",
                    "description": "Parse mode.",
                    "enum": ["text", "markdown", "structured"]
                },
            },
            "required": ["path", "mode"],
        },
    },
}

def simple_tool(name, description, properties, required):
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required,
            },
        },
    }

variants = {
    "dummy": simple_tool(
        "dummy_lookup",
        "A harmless secondary tool for testing tool selection.",
        {"query": {"type": "string", "description": "Query text."}},
        ["query"],
    ),
    "file_read_path": simple_tool(
        "file_read",
        "Read a UTF-8 text file.",
        {"path": {"type": "string", "description": "UTF-8 text file path."}},
        ["path"],
    ),
    "file_read_arg0": simple_tool(
        "file_read",
        "Read a UTF-8 text file.",
        {"arg0": {"type": "string", "description": "UTF-8 text file path."}},
        ["arg0"],
    ),
    "read_text_path": simple_tool(
        "read_text",
        "Read a UTF-8 text file.",
        {"path": {"type": "string", "description": "UTF-8 text file path."}},
        ["path"],
    ),
    "run_python_min": simple_tool(
        "run_python",
        "Execute an existing Python script.",
        {"script": {"type": "string", "description": "Script path."}},
        ["script"],
    ),
    "load_skill_min": simple_tool(
        "loadSkillContent",
        "Load a skill by name.",
        {"skillName": {"type": "string", "description": "Skill name."}},
        ["skillName"],
    ),
}
if variant not in variants:
    raise SystemExit(f"unknown variant: {variant}")
print(json.dumps([parse_file, variants[variant]], ensure_ascii=False))
PY
}

short_system_prompt='<nothink>
你是一个严格使用 OpenAI 原生 tool_calls 的助手。
当用户要求读取、分析、统计上传文件内容时，必须调用 parse_file 工具。
不要输出 <think>、</think>、<function_calls>、<invoke>、<parameter>、<tool_call> 或任何伪 XML 工具调用文本。
如果需要工具，assistant 正文 content 必须为空或极短，必须通过原生 tool_calls 返回工具调用。'

project_system_prompt='<nothink>
你是快速回答 AI。直接输出最终答案，不要思考过程、不要 <think>、不要分析、不要解释、不要一步一步。保持简洁自然。

你是 Lingz Agent。

基本要求：
- 默认使用中文回答，必要时可保留英文技术术语、代码、命令、路径和接口字段名
- 回答必须基于实际工具结果、技能内容和可验证信息，不要凭空编造
- 只有在继续执行任务确实受阻时，才提出简短澄清问题
- 当用户请求与某个可用技能匹配时，优先先调用 loadSkillContent(skillName) 再作答
- 需要技能参考资料时，调用 loadSkillReference(skillName, referenceKey)
- 当用户询问可用技能时，调用 listActiveSkills
- 使用技能时，要优先依赖技能中定义的流程、边界和工具
- 如果用户点名了某个 skill，但 loadSkillContent(skillName) 明确返回未找到，不要连续猜测近似 skill 名；先告诉用户当前没有找到该 skill，再根据 listActiveSkills 选择其他可用 skill 或请用户确认名称
- 如果请求将进入工具执行或技能执行，用户可见的回复不要只剩工具调用；应先用 1 到 2 句自然中文说明你理解的任务和接下来第一阶段要做什么
- 当执行进入新阶段时，例如文件已解析、开始处理、开始生成产物、发现错误后自动修正，应补一条简短进度说明，让用户知道当前在做什么
- 阶段说明应面向用户目标，不要把 loadSkillContent、file_write 这类内部工具名直接当成主要叙述内容；除非用户明确要求技术细节
- 不要为每一个工具调用都机械解释；只在任务开始、阶段切换、自动修正和完成时给出简短说明即可

## 执行模式
- 按以下顺序决策：1. 能直接回答就直接回答；2. 不能直接回答就先判断现有 skill / tool 是否足够；3. 足够就继续用 skill / tool 完成；4. 明显不足时才升级到 CODE。
- 当前请求已判定为执行型请求，应优先从现有能力中选择合适工具或技能完成任务。
- 当前请求需要读取上传的 xls 文件内容，必须优先调用 parse_file。
- 当你已经决定要调用工具时，优先直接返回原生 tool_calls，不要先输出说明文字、分析文字、伪 XML/HTML 标签，或 <|parse_file> 这类伪工具调用文本。
- 若 provider 支持原生 function/tool calling，则工具阶段的 assistant 输出应尽量保持为空正文，仅返回结构化 tool call。
- 对于 .xlsx/.xls/.csv/.docx/.pdf 这类附件或 runtime 产物，不要用 file_read 直接读取二进制内容；优先使用 parse_file 或受控脚本处理。
- 选择 parse_file 模式时：如果用户目标是提取内容、读取原文、摘要或统计，优先使用 text；只有当目标是看表结构、列名、schema 时，才优先使用 structured。

强约束：
- 不要输出 <function_calls>、<invoke>、<parameter>、<tool_call>。
- 如果需要工具，必须通过 OpenAI 原生 tool_calls 字段返回。'

user_content='这个表里面有多少人

User uploaded files:
- 科技信息化总队2月12日至2月28日值班表.xls'

make_payload() {
  local file="$1"
  local system_prompt="$2"
  local tools_json="$3"
  local stream="$4"
  local temperature="$5"
  local max_tokens="$6"
  python3 - "$file" "$model" "$system_prompt" "$user_content" "$tools_json" "$stream" "$temperature" "$max_tokens" <<'PY'
import json
import sys

file, model, system_prompt, user_content, tools_json, stream, temperature, max_tokens = sys.argv[1:]
payload = {
    "model": model,
    "messages": [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_content},
    ],
    "tools": json.loads(tools_json),
    "tool_choice": "auto",
    "temperature": float(temperature),
    "max_tokens": int(max_tokens),
    "stream": stream.lower() == "true",
    "enable_thinking": False,
    "chat_template_kwargs": {"enable_thinking": False},
}
if payload["stream"]:
    payload["stream_options"] = {"include_usage": True}
with open(file, "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False)
PY
}

tools_parse_file="$(write_parse_file_tool_json)"
tools_project="$(write_common_tools_json)"

run_case() {
  local name="$1"
  local file="${tmp_dir}/${name}.json"
  case "${name}" in
    00-replay)
      PROGRAM_BODY_FILE="${PROGRAM_BODY_FILE:-${script_dir}/payloads/lingz-vllm-normalized-body.json}"
      curl_payload "${name}: replay OpenAI-compatible 完整请求体 body as-is" "${PROGRAM_BODY_FILE}"
      ;;
    01-short)
      make_payload "${file}" "${short_system_prompt}" "${tools_parse_file}" false 0.0 512
      curl_payload "${name}: short prompt + parse_file only + non-stream + temperature 0" "${file}"
      ;;
    02-short-stream)
      make_payload "${file}" "${short_system_prompt}" "${tools_parse_file}" true 0.0 512
      curl_payload "${name}: short prompt + parse_file only + stream + temperature 0" "${file}"
      ;;
    03-all-tools)
      make_payload "${file}" "${short_system_prompt}" "${tools_project}" true 0.0 512
      curl_payload "${name}: short prompt + project tool set + stream + temperature 0" "${file}"
      ;;
    03a-runtime-read-tools)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,stat,file_read,list_dir')" true 0.0 512
      curl_payload "${name}: short prompt + parse/stat/file_read/list_dir + stream + temperature 0" "${file}"
      ;;
    03b-runtime-all-tools)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,stat,file_read,list_dir,file_write,run_python,write_artifact')" true 0.0 512
      curl_payload "${name}: short prompt + all runtime tools + stream + temperature 0" "${file}"
      ;;
    03c-skill-tools)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,listActiveSkills,loadSkillContent,loadSkillReference')" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + skill tools + stream + temperature 0" "${file}"
      ;;
    03d-parse-stat)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,stat')" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + stat + stream + temperature 0" "${file}"
      ;;
    03e-parse-file-read)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,file_read')" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + file_read + stream + temperature 0" "${file}"
      ;;
    03f-parse-list-dir)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,list_dir')" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + list_dir + stream + temperature 0" "${file}"
      ;;
    03g-parse-run-python)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,run_python')" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + run_python + stream + temperature 0" "${file}"
      ;;
    03h-parse-skill-load)
      make_payload "${file}" "${short_system_prompt}" "$(tools_filter "${tools_project}" 'parse_file,loadSkillContent')" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + loadSkillContent + stream + temperature 0" "${file}"
      ;;
    06a-parse-dummy)
      make_payload "${file}" "${short_system_prompt}" "$(write_schema_probe_tools_json dummy)" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + dummy_lookup minimal schema" "${file}"
      ;;
    06b-parse-file-read-path)
      make_payload "${file}" "${short_system_prompt}" "$(write_schema_probe_tools_json file_read_path)" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + file_read minimal path schema" "${file}"
      ;;
    06c-parse-file-read-arg0)
      make_payload "${file}" "${short_system_prompt}" "$(write_schema_probe_tools_json file_read_arg0)" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + file_read minimal arg0 schema" "${file}"
      ;;
    06d-parse-read-text-path)
      make_payload "${file}" "${short_system_prompt}" "$(write_schema_probe_tools_json read_text_path)" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + read_text minimal path schema" "${file}"
      ;;
    06e-parse-run-python-min)
      make_payload "${file}" "${short_system_prompt}" "$(write_schema_probe_tools_json run_python_min)" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + run_python minimal schema" "${file}"
      ;;
    06f-parse-load-skill-min)
      make_payload "${file}" "${short_system_prompt}" "$(write_schema_probe_tools_json load_skill_min)" true 0.0 512
      curl_payload "${name}: short prompt + parse_file + loadSkillContent minimal schema" "${file}"
      ;;
    04-project-prompt)
      make_payload "${file}" "${project_system_prompt}" "${tools_project}" true 0.0 1024
      curl_payload "${name}: project-like prompt + project tool set + stream + temperature 0" "${file}"
      ;;
    05-project-temp07)
      make_payload "${file}" "${project_system_prompt}" "${tools_project}" true 0.7 1024
      curl_payload "${name}: project-like prompt + project tool set + stream + temperature 0.7" "${file}"
      ;;
    *)
      echo "Unknown case: ${name}" >&2
      exit 1
      ;;
  esac
}

if [[ "${case_name}" == "all" ]]; then
  for name in 01-short 02-short-stream 03-all-tools 04-project-prompt 05-project-temp07; do
    run_case "${name}"
  done
elif [[ "${case_name}" == "tools" ]]; then
  for name in 03a-runtime-read-tools 03b-runtime-all-tools 03c-skill-tools 03d-parse-stat 03e-parse-file-read 03f-parse-list-dir 03g-parse-run-python 03h-parse-skill-load; do
    run_case "${name}"
  done
elif [[ "${case_name}" == "schema" ]]; then
  for name in 06a-parse-dummy 06b-parse-file-read-path 06c-parse-file-read-arg0 06d-parse-read-text-path 06e-parse-run-python-min 06f-parse-load-skill-min; do
    run_case "${name}"
  done
else
  run_case "${case_name}"
fi
