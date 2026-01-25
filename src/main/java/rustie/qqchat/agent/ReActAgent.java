package rustie.qqchat.agent;

import lombok.extern.slf4j.Slf4j;
import rustie.qqchat.client.LLMClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/**
 * Minimal ReAct-style agent: send tools, let the model decide tool_calls, execute, loop.
 */
@Slf4j
public final class ReActAgent {
    private static final int LOG_TEXT_LIMIT = 400;
    private final LLMClient client;
    private final ToolRegistry registry;
    private final ObjectMapper om;

    public ReActAgent(LLMClient client, ToolRegistry registry, ObjectMapper om) {
        this.client = client;
        this.registry = registry;
        this.om = om;
    }

    public Result run(String userInput, int maxIters) throws Exception {int upper = Math.max(1, maxIters);
        ArrayNode messages = om.createArrayNode();
        messages.add(systemMessage("""
                你是一个有工具可用的助手。
                - 需要外部信息时请调用工具（tool_choice=auto）。
                - 如果用户消息携带了图片，你无法直接“看见”图片内容，请调用图片理解工具再回答。
                - 若调用到“会直接向用户返回结果”的工具（如生图工具），调用后无需再输出额外文本。
                """));
        messages.add(userMessage(userInput));

        var tools = registry.all();
        ArrayNode toolsPayload = LLMClient.toolsPayload(om, tools);
        List<String> toolNames = tools.stream().map(Tool::name).toList();

        log.info("Agent开始运行：maxIters={}，工具={}，用户输入={}", upper, toolNames, clip(userInput));

        for (int i = 1; i <= upper; i++) {
            long t0 = System.nanoTime();
            log.info(" 第{}/{}轮：发送给模型（messages={}，tools={}）", i, upper, messages.size(), toolsPayload.size());
            JsonNode resp = client.createChatCompletion(messages, toolsPayload);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            JsonNode choice0 = resp.path("choices").path(0);
            JsonNode msg = choice0.path("message");

            // Always append assistant message back into history (including tool_calls if any).
            ObjectNode assistantMsg = om.createObjectNode();
            assistantMsg.put("role", "assistant");
            if (!msg.path("content").isMissingNode() && !msg.path("content").isNull()) {
                assistantMsg.set("content", msg.get("content"));
            } else {
                assistantMsg.putNull("content");
            }
            if (msg.has("tool_calls")) {
                assistantMsg.set("tool_calls", msg.get("tool_calls"));
            }
            messages.add(assistantMsg);

            JsonNode toolCalls = msg.get("tool_calls");
            if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
                String content = Optional.ofNullable(msg.get("content")).map(JsonNode::asText).orElse("");
                log.info("第{}/{}轮：模型给出最终回答（耗时={}ms）：{}", i, upper, elapsedMs, clip(content));
                return new Result(content, i, "final");
            }

            String assistantContent = Optional.ofNullable(msg.get("content")).map(JsonNode::asText).orElse("");
            log.info("第{}/{}轮：模型请求调用工具（耗时={}ms，toolCalls={}，assistantContent={}）",
                     i, upper, elapsedMs, toolCalls.size(), clip(assistantContent));

            // Execute all tool calls, add tool messages.
            for (JsonNode tc : toolCalls) {
                String toolCallId = tc.path("id").asText();
                String toolName = tc.path("function").path("name").asText();
                String rawArgs = tc.path("function").path("arguments").asText("{}");

                ObjectNode toolMsg = om.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);

                JsonNode result;
                try {
                    long toolT0 = System.nanoTime();
                    Tool tool = registry.get(toolName);
                    if (tool == null) {
                        log.warn("工具调用：未知工具 name={}，tool_call_id={}，args={}",toolName, toolCallId, clip(rawArgs));
                        result = om.createObjectNode()
                                .put("error", "unknown_tool")
                                .put("message", "No tool registered with name: " + toolName);
                    } else {
                        log.info("工具调用：开始执行 name={}，tool_call_id={}，args={}", toolName, toolCallId, clip(rawArgs));
                        JsonNode argsNode = om.readTree(rawArgs);
                        result = tool.execute(argsNode, om);
                        long toolMs = (System.nanoTime() - toolT0) / 1_000_000;
                        log.info("工具调用：执行完成 name={}，tool_call_id={}，耗时={}ms，结果={}",
                                toolName, toolCallId, toolMs, clip(om.writeValueAsString(result)));
                        if (tool.returnDirect()) {
                            String out = tool.toUserText(result, om);
                            log.info("工具调用：returnDirect 触发，直接返回给用户：{}", clip(out));
                            return new Result(out, i, "tool_return_direct");
                        }
                    }
                } catch (Exception e) {
                    log.warn(" 工具调用：执行失败 name={}，tool_call_id={}，args={}，异常={}",
                            toolName, toolCallId, clip(rawArgs), e.toString(), e);
                    result = om.createObjectNode()
                            .put("error", "tool_execution_failed")
                            .put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }

                Tool tool = registry.get(toolName);
                boolean include = tool == null || tool.includeResultInModelContext();
                if (include) {
                    toolMsg.put("content", om.writeValueAsString(result));
                    messages.add(toolMsg);
                } else {
                    log.info("工具调用：按配置不回传给模型 name={} tool_call_id={}", toolName, toolCallId);
                }
            }
        }

        log.warn("Agent达到最大迭代次数仍未产出最终回答：maxIters={}", upper);
        return new Result("Reached max iterations (" + upper + ") without a final answer.", upper, "max_iters");
    }

    private ObjectNode systemMessage(String content) {
        ObjectNode n = om.createObjectNode();
        n.put("role", "system");
        n.put("content", content);
        return n;
    }

    private ObjectNode userMessage(String content) {
        ObjectNode n = om.createObjectNode();
        n.put("role", "user");
        n.put("content", content);
        return n;
    }

    private static String clip(String s) {
        if (s == null) return "null";
        String oneLine = s.replace("\r", "\\r").replace("\n", "\\n");
        if (oneLine.length() <= LOG_TEXT_LIMIT) return oneLine;
        return oneLine.substring(0, LOG_TEXT_LIMIT) + "...(已截断,总长=" + oneLine.length() + ")";
    }

    public record Result(String output, int iterations, String stopReason) {
    }
}

