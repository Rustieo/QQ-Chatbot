package rustie.qqchat.agent;

import lombok.extern.slf4j.Slf4j;
import rustie.qqchat.client.LLMClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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

    public Result run(String userInput, int maxIters) throws Exception {
        return run(userInput, maxIters, null);
    }

    public Result run(String userInput, int maxIters, Consumer<String> realtimeOut) throws Exception {
        int upper = Math.max(1, maxIters);
        ArrayNode messages = om.createArrayNode();
        messages.add(systemMessage("""
                     你是一个有工具可用的助手，必须尽可能通过工具完成用户请求。

                     通用工作流（务必遵守）：
                     1) 先判断用户目标：是要信息查询、执行操作、还是生成/修改图片。
                     2) 再判断完成目标所需信息是否齐全：缺什么就先问清楚或用工具获取；不要凭空编造。
                     3) 若存在多候选/歧义（比如昵称匹配多个人、指代不清、要求不明确），先列出候选并追问确认，再继续。
                     4) 工具串联规则：
                         - 当你需要“工具A的输出作为工具B的输入”时，必须选择不会直接结束对话的工具/用法，确保链路可继续。
                         - 如果某个工具的描述明确写了“会直接返回给用户/调用后结束本轮/returnDirect”，那么它只适合做最终一步；除非用户只要它的直接结果。
                     5) 图片相关规则：
                         - 你无法直接看见图片内容。凡是你要基于“输入图片的具体内容”去写提示词/判断下一步时，必须先调用 image_understanding。
                         - 不要在未理解图片的前提下擅自推测图片是人物/风景/物品等；若用户已明确描述图片内容，则可直接据此写提示词。
                     6) 输出规则：
                         - 若调用到 returnDirect=true 的工具（例如生图/直接返回链接类工具），调用后无需再输出额外文本。

                     术语消歧：用户说“重构头像”通常指“重绘/改图/风格化头像”，不是让你重构代码。
                """));
        messages.add(userMessage(userInput));

        var tools = registry.all();
        ArrayNode toolsPayload = LLMClient.toolsPayload(om, tools);
        List<String> toolNames = tools.stream().map(Tool::name).toList();

        log.info("Agent开始运行：maxIters={}，工具={}，用户输入={}", upper, toolNames, clip(userInput));
        boolean usedImageUnderstanding = false;

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
                return new Result(content, List.of(), i, "final", usedImageUnderstanding);
            }

            String assistantContent = Optional.ofNullable(msg.get("content")).map(JsonNode::asText).orElse("");
            log.info("第{}/{}轮：模型请求调用工具（耗时={}ms，toolCalls={}，assistantContent={}）",
                     i, upper, elapsedMs, toolCalls.size(), clip(assistantContent));

            if (realtimeOut != null) {
                String toSend = assistantContent == null ? "" : assistantContent.trim();
                if (!toSend.isBlank()) {
                    realtimeOut.accept(clipForUser(toSend));
                }
            }

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
                        if ("image_understanding".equals(toolName)) usedImageUnderstanding = true;
                        log.info("工具调用：开始执行 name={}，tool_call_id={}，args={}", toolName, toolCallId, clip(rawArgs));
                        JsonNode argsNode = om.readTree(rawArgs);
                        result = tool.execute(argsNode, om);
                        long toolMs = (System.nanoTime() - toolT0) / 1_000_000;
                        log.info("工具调用：执行完成 name={}，tool_call_id={}，耗时={}ms，结果={}",
                                toolName, toolCallId, toolMs, clip(om.writeValueAsString(result)));

                        if (tool.returnDirect()) {
                            List<String> directUrls = extractUrls(result);
                            String out = tool.toUserText(result, om);
                            log.info("工具调用：returnDirect 触发，直接返回给用户：{}", clip(out));
                            return new Result(out, directUrls, i, "tool_return_direct", usedImageUnderstanding);
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
        return new Result("Reached max iterations (" + upper + ") without a final answer.", List.of(), upper, "max_iters", usedImageUnderstanding);
    }

    private static List<String> extractUrls(JsonNode toolResult) {
        if (toolResult == null) return List.of();
        JsonNode urls = toolResult.get("urls");
        if (urls == null || !urls.isArray() || urls.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode u : urls) {
            if (u != null && u.isString() && !u.asText("").isBlank()) out.add(u.asText());
        }
        return out;
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

    private static String clipForUser(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return "";
        // user-facing: allow a bit more, but still cap
        final int limit = 800;
        if (trimmed.length() <= limit) return trimmed;
        return trimmed.substring(0, limit) + "...(已截断)";
    }

    public record Result(String output, List<String> urls, int iterations, String stopReason, boolean usedImageUnderstanding) {
    }
}

