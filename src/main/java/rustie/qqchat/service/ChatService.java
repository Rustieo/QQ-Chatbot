package rustie.qqchat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rustie.qqchat.agent.ReActAgent;
import rustie.qqchat.agent.tools.GenImageTool;
import rustie.qqchat.client.LLMClient;
import rustie.qqchat.model.dto.ChatMessage;
import rustie.qqchat.model.dto.Response;
import rustie.qqchat.model.entity.ChatTextSearchResult;
import rustie.qqchat.utils.ImageIntentUtils;
import rustie.qqchat.utils.IdHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;


import java.util.List;
import java.util.function.Consumer;
@Service
@RequiredArgsConstructor
public class ChatService {
    private final LLMClient llmClient;
    //private final HybridSearchService searchService;
    private final ObjectMapper objectMapper;
    private final MessageHistoryService messageHistoryService;
    private final ReActAgent reActAgent;
    private final GenImageTool genImageTool;
    private boolean groupAgentEnabled = true;
    private boolean privateAgentEnabled = false;

    public boolean switchGroupAgentMode() {
        groupAgentEnabled = !groupAgentEnabled;
        return groupAgentEnabled;
    }
    public boolean switchPrivateAgentMode() {
        privateAgentEnabled = !privateAgentEnabled;
        return privateAgentEnabled;
    }

    public Response normalChatPrivate(long userId, String userMessage) {
        return normalChatPrivate(userId, userMessage, List.of());
    }

    public Response normalChatPrivate(long userId, String userMessage, List<String> imageUrls) {
        return normalChatPrivate(userId, userMessage, imageUrls, null);
    }

    public Response normalChatPrivate(long userId, String userMessage, List<String> imageUrls, Consumer<String> realtimeOut) {
        // Agent模式下无图也要能触发生图（避免模型不主动选工具）

        final String originalUserMessage = userMessage;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            // Let DeepSeek decide whether to call image_understanding tool.
            String inputForAgent = userMessage + "\n\n(附：本轮携带了 " + imageUrls.size()
                    + " 张图片；若需要理解图片内容请调用 image_understanding 工具。或者你认为这是图片生成/修改,则直接调用gen_image工具即可)";
            return agentChatPrivate(userId, originalUserMessage, inputForAgent, realtimeOut);
        }
        if (privateAgentEnabled) {
            return agentChatPrivate(userId, originalUserMessage, originalUserMessage, realtimeOut);
        }
        List<ChatMessage> history = messageHistoryService.getPrivateHistory(userId);
        String responseText = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.savePrivateTurn(userId, userMessage, responseText);
        return Response.ofText(responseText);
    }

    public Response normalChatGroup(long groupId, long groupMemberId, String userMessage) {
        return normalChatGroup(groupId, groupMemberId, userMessage, List.of());
    }

    /**
     * If imageUrls is not empty: let agent decide whether to call image_understanding tool.
     * Persist as: first user input + final model output (ignore intermediate tool calls).
     */
    public Response normalChatGroup(long groupId, long groupMemberId, String userMessage, List<String> imageUrls) {
        return normalChatGroup(groupId, groupMemberId, userMessage, imageUrls, null);
    }

    public Response normalChatGroup(long groupId, long groupMemberId, String userMessage, List<String> imageUrls, Consumer<String> realtimeOut) {
        // Agent模式下无图也要能触发生图（避免模型不主动选工具）
        final String originalUserMessage = userMessage;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            // Let DeepSeek decide whether to call image_understanding tool.
            String inputForAgent = userMessage + "\n\n(附：本轮携带了 " + imageUrls.size()
                    + " 张图片；若需要理解图片内容请调用 image_understanding 工具,或者你认为这是图片生成/修改,则直接调用gen_image工具即可.)";
            return agentChatGroup(groupId, groupMemberId, originalUserMessage, inputForAgent, realtimeOut);
        }
        if (groupAgentEnabled) {
            return agentChatGroup(groupId, groupMemberId, originalUserMessage, originalUserMessage, realtimeOut);
        }
        List<ChatMessage> history = messageHistoryService.getGroupHistory(groupId);
        String responseText = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, responseText);
        return Response.ofText(responseText);
    }

    private Response agentChatGroup(long groupId,
                                 long groupMemberId,
                                 String originalUserMessage,
                                 String inputForAgent,
                                 Consumer<String> realtimeOut) {
        try {
            ReActAgent.Result r = reActAgent.run(inputForAgent, 8, realtimeOut);
            maybePersistAgentTurnGroup(groupId, groupMemberId, originalUserMessage, r);
            return toResponse(r);
        } catch (Exception e) {
            return Response.ofText("Agent 运行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            IdHolder.removeAll();
        }
    }

    private Response agentChatPrivate(long userId,
                                   String originalUserMessage,
                                   String inputForAgent,
                                   Consumer<String> realtimeOut) {
        try {
            ReActAgent.Result r = reActAgent.run(inputForAgent, 8, realtimeOut);
            maybePersistAgentTurnPrivate(userId, originalUserMessage, r);
            return toResponse(r);
        } catch (Exception e) {
            return Response.ofText("Agent 运行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            IdHolder.removeAll();
        }
    }

    private static Response toResponse(ReActAgent.Result r) {
        if (r == null) return Response.ofText("");
        if (r.urls() != null && !r.urls().isEmpty()) {
            // gen_image: URLs should be rendered as MsgUtils.img(url), not as plain text.
            return Response.of("", r.urls());
        }
        return Response.ofText(r.output());
    }

    public boolean isGroupAgentEnabled() {
        return groupAgentEnabled;
    }

    public boolean isPrivateAgentEnabled() {
        return privateAgentEnabled;
    }

    private String buildContext(List<ChatTextSearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            // 返回空字符串，让 LLMClient 按"无检索结果"逻辑处理
            return "";
        }
        final int MAX_SNIPPET_LEN = 300; // 单段最长字符数，超出截断
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            ChatTextSearchResult result = searchResults.get(i);
            String snippet = result.getContent();
            if (snippet.length() > MAX_SNIPPET_LEN) {
                snippet = snippet.substring(0, MAX_SNIPPET_LEN) + "…";
            }
            //TODO:这里后续可以加上高人
            String userName= result.getUserName()!=null?result.getUserId():"未知用户";

            context.append(String.format("[%d] (%s),%s, %s\n", i + 1, userName,":", snippet));
        }
        return context.toString();
    }

    public void clearPrivateHistory(long userId) {
        messageHistoryService.clearPrivateHistory(userId);
    }

    public void clearGroupHistory(long groupId) {
        messageHistoryService.clearGroupHistory(groupId);
    }

    private void maybePersistAgentTurnGroup(long groupId, long groupMemberId, String userMessage, ReActAgent.Result r) {
        if (r == null) return;
        // For image understanding: persist user first input + final output, ignore tool-call steps.
        // Also persist normal "final" answers in agent mode for continuity.
        if (r.usedImageUnderstanding() || "final".equals(r.stopReason())) {
            messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, r.output());
        }
    }

    private void maybePersistAgentTurnPrivate(long userId, String userMessage, ReActAgent.Result r) {
        if (r == null) return;
        if (r.usedImageUnderstanding() || "final".equals(r.stopReason())) {
            messageHistoryService.savePrivateTurn(userId, userMessage, r.output());
        }
    }

    private String directGenImage(String prompt) {
        try {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("prompt", prompt == null ? "" : prompt);
            JsonNode result = genImageTool.execute(args, objectMapper);
            return genImageTool.toUserText(result, objectMapper);
        } catch (Exception e) {
            return "生图失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            // keep consistent with other flows
            IdHolder.removeAll();
        }
    }

}
