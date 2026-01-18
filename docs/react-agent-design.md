# 基于 ReAct 的 Agent 功能设计（QQchat）

> 目标：在不引入“原生 Function Calling”强依赖的前提下（当前 `LLMClient` 是普通 `/chat/completions` 调用），为 QQchat 增加可扩展的 ReAct Agent：让模型在「推理 → 选择工具 → 执行工具 → 观察结果 → 继续推理」的闭环中完成更复杂的任务（RAG、设置、知识库管理等）。

## 1. 背景与现状

当前主链路（群聊/私聊）基本是：

- 插件层（GroupChatPlugin / PrivateChatPlugin）解析消息
- `ChatService` 负责：
  - 维护 `history`
  - （可选）调用 `HybridSearchService` 进行检索拼接 context
  - 调用 `LLMClient.normalResponse(userMessage, context, history)`

现有能力可复用为 Agent 的“工具（Tools）”：

- **RAG 检索**：`HybridSearchService.search(...)` / `searchDetail(...)`
- **RAG 问答**：`ChatService.ragChat(...)` / `ragChatExplain(...)`
- **运行时设置**：`SettingService`（sysprompt/top_p/temperature/limits/model）
- **知识库写入/清理**：`RagPlugin` + `VectorizationService` / `ElasticsearchService`

Agent 的价值：
- 让模型在回答前自动决定“是否需要检索、检索多少、是否要解释检索过程、是否要修改设置”等。
- 将能力从“单轮调用 + 拼接 context”提升为“多步规划 + 可观测执行”。

## 2. ReAct 方案选择

ReAct 的核心是把**推理（Reasoning）**与**行动（Action/Tool Call）**交织在一起。对本项目而言推荐两阶段落地：

### 2.1 阶段 A：基于「结构化文本」的 Tool Calling（推荐先做）

不依赖模型厂商的 function calling 协议，而是约定输出格式，例如：

```text
THOUGHT: ...
ACTION: {"name":"rag_search","args":{"query":"...","topK":5}}
```

优点：
- 兼容 DeepSeek/Qwen/OpenAI 风格的 `/chat/completions`
- 实现成本低，可快速验证 Agent 闭环

风险：
- 模型可能不完全遵循格式 → 需要 parser + 重试/纠错策略

### 2.2 阶段 B：接入供应商原生 Function Calling（可选增强）

如果 DeepSeek/Qwen 接口在你当前使用的 endpoint 中支持 `tools/function_call` 等字段，可升级为真正的工具调用。

优点：
- 格式更可靠

代价：
- 实现与供应商协议绑定；多模型切换时要抹平差异

## 3. 总体架构

建议新增“Agent 领域层”，与现有 `ChatService` 并行：

- `AgentService`（Orchestrator）：ReAct 循环控制器
- `ToolRegistry`：工具注册表（name → Tool）
- `Tool`：统一接口（校验参数、执行、返回 Observation）
- `ActionParser`：从模型输出中解析 action（JSON）
- `ConversationMemoryStore`：按会话隔离历史（避免当前 `ChatService.history` 全局共享）

### 3.1 关键问题：会话隔离（强烈建议先改造）

当前 `ChatService` 持有一个 `history` List，意味着：
- 不区分群/私聊
- 不区分不同群
- 不区分不同用户

这会导致 Agent 的多步执行“串线”，并带来严重的上下文污染。

**建议设计：**
- SessionKey = `channelType(group/private) + groupId + userId(可选)`
  - 群聊：通常用 groupId 作为会话 key（群共享上下文）
  - 私聊：用 userId 作为会话 key
- `ConversationMemoryStore`：`ConcurrentHashMap<SessionKey, Deque<Message>>`
- 每个会话独立容量（例如 40 条 message），超出滚动淘汰

> 这一步不一定要立刻动现有 `ChatService`，也可以在 `AgentService` 内部先做“独立 memory”，后续再统一。

## 4. ReAct 循环细节（建议实现）

### 4.1 循环伪代码

```java
for (int step = 1; step <= maxSteps; step++) {
  String prompt = buildAgentPrompt(userInput, memory, toolSpecs, scratchpad);
  String modelOutput = llmClient.normalResponse(prompt, "", nullOrHistory);

  Action action = actionParser.tryParse(modelOutput);

  if (action == null) {
    // 当作最终回答
    return finalizeAnswer(modelOutput);
  }

  Observation obs = toolRegistry.execute(action, ctx);
  scratchpad.append(renderObservation(action, obs));

  if (obs.isTerminal()) {
    return obs.getUserFacingMessage();
  }
}
return "已达到最大步骤限制，请缩小问题范围或稍后重试。";
```

### 4.2 Prompt 结构建议

在 system prompt 中给出：
1) 角色设定（沿用 `AiProperties.prompt.roles`）
2) 行为规则（沿用 `AiProperties.prompt.limits`）
3) 工具清单 + 调用格式（关键）
4) 安全规则（禁止泄露密钥/禁止越权执行设置类工具等）

在 user prompt 中给出：
- 用户原始问题
- 结构化上下文（会话信息、群号/用户号、是否管理员、是否允许写库等）

### 4.3 “不暴露思维链”的折中

对外回复时建议：
- 不把 `THOUGHT` 原样发给用户（避免泄露内部推理/提示词）
- 允许用“简短过程摘要”代替：例如“我检索了知识库并综合结果后给出回答”。

实现上：
- 模型输出可以包含 THOUGHT，但最终只提取 FINAL 段落
- 或者直接要求模型不输出 THOUGHT，只输出 `ACTION/FINAL`

## 5. 工具（Tools）设计

### 5.1 工具接口

建议统一为：

- `name`: string
- `description`: string
- `argsSchema`: （可选）JSON Schema / 自定义校验
- `execute(ctx, args) -> Observation`

其中 `ctx` 至少包含：
- channelType（group/private）
- groupId/userId
- isAdmin（用于权限）
- 原始 event（可选）

`Observation` 建议包含：
- `ok`: boolean
- `content`: string（给模型看的 observation，尽量结构化）
- `userFacingMessage`: string（如果需要直接回复用户）
- `terminal`: boolean（工具执行完直接结束本轮）

### 5.2 推荐首批工具（复用现有服务）

1) `rag_search`
- args: `{ "query": string, "topK": number, "explain": boolean }`
- impl: `HybridSearchService.search(...)` 或 `searchDetail(...)`
- obs: 返回命中片段（截断）+ 分数 + 元信息

2) `rag_answer`
- args: `{ "query": string, "topK": number }`
- impl: `ChatService.ragChat(query, topK)`
- obs: `answer`

3) `chat`
- args: `{ "text": string }`
- impl: `ChatService.normalChat(text)`

4) `settings_update`
- 子命令式 args：
  - `{ "op":"set_sysprompt", "value": "..." }`
  - `{ "op":"set_temperature", "value": 0.7 }`
  - `{ "op":"set_topp", "value": 0.9 }`
  - `{ "op":"model_switch", "value": "deepseek|qwen" }`
  - `{ "op":"limit_add", "value": "..." }` 等
- impl: 调用 `SettingService` / `LLMClient.switchModel(...)`
- 权限：仅 admin

5) `memory_clear`
- args: `{}`
- impl: 清空对应 SessionKey 的 memory
- 权限：可对所有用户开放（或仅 admin）

> 知识库写入（/rag/add/*）也可以做成工具，但建议默认仅 admin，且需要二次确认（见 6.3）。

### 5.3 工具输出规范（减少模型幻觉）

- observation 尽量用 JSON：

```json
{"hits":[{"rank":1,"score":0.42,"text":"..."}]}
```

- 长文本统一截断（例如每条 200-300 字，总计 2k-4k 字）
- 对“不可用/失败”要明确：`{"ok":false,"error":"..."}`

## 6. 安全与权限

### 6.1 工具分级

- Read-only：检索、查看规则、帮助、总结
- Write/Side-effect：改 sysprompt、切模型、写入/清空知识库

### 6.2 权限模型

建议在配置中维护 allowlist：
- `ai.adminUserIds: [123, 456]`
- 群聊可选：`ai.adminGroupIds`

执行 tool 前必须校验 `ctx.isAdmin`。

### 6.3 “高危工具”二次确认

对以下工具建议引入二次确认：
- 清空知识库索引
- 大规模写入知识库

实现策略：
- 让工具返回 `terminal=false`，并在 observation 中要求模型向用户发起确认
- 或者实现一个 `confirm` 状态机：会话里缓存 pending action

## 7. 与现有插件的集成方式

建议新增一个触发入口（避免影响现有 /rq 和 @ 逻辑）：

- 群聊：`/agent ...` 或 `/a ...`
- 私聊：同样支持 `/agent ...`

处理流程：
1) Plugin 提取纯文本
2) 构造 `AgentRequest`（包含 groupId/userId/channelType/text）
3) 调 `AgentService.run(request)`
4) 将返回文本发送给 QQ

后续可以做“智能路由”：
- 默认走普通聊天
- 当检测到“需要检索/设置/多步任务”时自动走 agent

## 8. 可观测性与调试

建议记录每次 agent 执行的 trace（不要记录敏感信息）：
- requestId, sessionKey
- step 数
- action name + args（可脱敏）
- tool 执行耗时、是否成功
- 最终回答长度

同时提供一个 debug 命令（仅 admin）：
- `/agent/trace on|off`
- `/agent/trace last` 输出最近一次的 action 序列（不含思维链）

## 9. 验收标准（Definition of Done）

最小可用（MVP）：
- `/agent 问题` 能自动决定是否走 `rag_search` 并回答
- 有 `maxSteps`、超时、格式解析失败的兜底
- 每个群/私聊会话历史隔离
- settings 类工具具备权限控制

增强：
- 支持解释模式：`/agent/explain ...`
- 支持二次确认
- 支持流式输出（如果后续加 `stream=true`）

## 10. 分阶段落地建议

- Phase 1（1-2 天）：
  - AgentService + ToolRegistry + ActionParser
  - rag_search + chat 两个工具
  - 会话 memory（独立于 ChatService）
  - 新增 `/agent` 入口

- Phase 2（2-4 天）：
  - settings_update 工具 + 权限
  - 观测日志 + debug 命令

- Phase 3（可选）：
  - function calling 适配
  - 二次确认状态机
  - 更细粒度的群策略（每群独立 sysprompt/limits/model）

---

## 附录 A：建议的 Action 输出格式（强约束）

让模型在需要使用工具时输出（只输出一行 JSON，便于解析）：

```text
ACTION {"name":"rag_search","args":{"query":"...","topK":5,"explain":false}}
```

当无需工具、直接给用户回答时输出：

```text
FINAL 这里是要发给用户的最终回答
```

解析策略：
- 优先查找 `FINAL`
- 若存在 `ACTION`，执行工具并把 observation 回灌下一轮
- 若都没有，整段当作 FINAL（兜底）
