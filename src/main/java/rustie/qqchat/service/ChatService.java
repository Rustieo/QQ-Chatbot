package rustie.qqchat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rustie.qqchat.agent.ReActAgent;
import rustie.qqchat.agent.tools.GenImageTool;
import rustie.qqchat.client.LLMClient;
import rustie.qqchat.model.dto.ChatMessage;
import rustie.qqchat.model.entity.ChatTextSearchResult;
import rustie.qqchat.utils.ImageIntentUtils;
import rustie.qqchat.utils.IdHolder;
import tools.jackson.databind.ObjectMapper;


import java.util.List;
@Service
@RequiredArgsConstructor
public class ChatService {
    private final LLMClient llmClient;
    //private final HybridSearchService searchService;
    private final ObjectMapper objectMapper;
    private final MessageHistoryService messageHistoryService;
    private final ReActAgent reActAgent;
    private final GenImageTool genImageTool;
    private boolean groupAgentEnabled = false;
    private boolean privateAgentEnabled = false;

    public boolean switchGroupAgentMode() {
        groupAgentEnabled = !groupAgentEnabled;
        return groupAgentEnabled;
    }
    public boolean switchPrivateAgentMode() {
        privateAgentEnabled = !privateAgentEnabled;
        return privateAgentEnabled;
    }

    public String normalChatPrivate(long userId, String userMessage) {
        return normalChatPrivate(userId, userMessage, List.of());
    }

    public String normalChatPrivate(long userId, String userMessage, List<String> imageUrls) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            // Let DeepSeek decide whether to call image_understanding tool.
            String inputForAgent = userMessage + "\n\n(附：本轮携带了 " + imageUrls.size()
                    + " 张图片；若需要理解图片内容请调用 image_understanding 工具。或者你认为这是图片生成/修改,则直接调用gen_image工具即可)";
            return agentChatPrivate(userId, inputForAgent);
        }
        if (privateAgentEnabled) {
            return agentChatPrivate(userId, userMessage);
        }
        List<ChatMessage> history = messageHistoryService.getPrivateHistory(userId);
        String response = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.savePrivateTurn(userId, userMessage, response);
        return response;
    }

    public String normalChatGroup(long groupId, long groupMemberId, String userMessage) {
        return normalChatGroup(groupId, groupMemberId, userMessage, List.of());
    }

    /**
     * If imageUrls is not empty: use Qwen-VL for image understanding.
     * NOTE: image understanding is NOT persisted and NOT added to memory/history.
     */
    public String normalChatGroup(long groupId, long groupMemberId, String userMessage, List<String> imageUrls) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            // Let DeepSeek decide whether to call image_understanding tool.
            String inputForAgent = userMessage + "\n\n(附：本轮携带了 " + imageUrls.size()
                    + " 张图片；若需要理解图片内容请调用 image_understanding 工具,或者你认为这是图片生成/修改,则直接调用gen_image工具即可.)";
            return agentChatGroup(groupId, groupMemberId, inputForAgent);
        }
        if (groupAgentEnabled) {
            return agentChatGroup(groupId, groupMemberId, userMessage);
        }
        List<ChatMessage> history = messageHistoryService.getGroupHistory(groupId);
        String response = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, response);
        return response;
    }

    private String agentChatGroup(long groupId, long groupMemberId, String userMessage) {
        try {
            return reActAgent.run(userMessage, 8).output();
        } catch (Exception e) {
            return "Agent 运行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            IdHolder.removeAll();
        }
    }

    private String agentChatPrivate(long userId, String userMessage) {
        try {
            return reActAgent.run(userMessage, 8).output();
        } catch (Exception e) {
            return "Agent 运行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            IdHolder.removeAll();
        }
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

}
